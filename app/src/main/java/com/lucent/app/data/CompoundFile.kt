package com.lucent.app.data

import java.io.ByteArrayOutputStream

private val COMPOUND_SIGNATURE = byteArrayOf(
    0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
    0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
)

internal fun readLe16(data: ByteArray, at: Int): Int =
    (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)

internal fun readLe32(data: ByteArray, at: Int): Int =
    (data[at].toInt() and 0xFF) or
        ((data[at + 1].toInt() and 0xFF) shl 8) or
        ((data[at + 2].toInt() and 0xFF) shl 16) or
        ((data[at + 3].toInt() and 0xFF) shl 24)

internal class CompoundFile(private val source: ByteArray) {

    private class Entry(val name: String, val type: Int, val startSector: Int, val size: Int)

    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Int
    private val fat: IntArray
    private val miniFat: IntArray
    private val miniStream: ByteArray
    private val entries: List<Entry>

    init {
        require(source.size >= 512 && matchesCompoundSignature(source)) { "这不是 Word 97-2003 文档" }

        sectorSize = 1 shl readLe16(source, 0x1E)
        miniSectorSize = 1 shl readLe16(source, 0x20)
        miniCutoff = readLe32(source, 0x38)

        val fatIds = allocationTableSectorIds(readLe32(source, 0x2C))
        require(fatIds.isNotEmpty()) { "无效的 DOC 文件结构 (缺少扇区分配表)" }

        val rawFat = sectorsOf(fatIds)
        fat = IntArray(rawFat.size / 4) { index -> readLe32(rawFat, index * 4) }

        entries = directoryEntries(readLe32(source, 0x30))

        val root = entries.firstOrNull { it.type == 5 }
        miniStream = if (root != null && root.size > 0 && root.startSector >= 0) {
            followChain(root.startSector, root.size)
        } else {
            ByteArray(0)
        }

        miniFat = firstMiniFatSector(source)?.let { start ->
            val raw = followChain(start, 0)
            IntArray(raw.size / 4) { index -> readLe32(raw, index * 4) }
        } ?: IntArray(0)
    }

    fun stream(name: String): ByteArray? {
        val entry = entries.firstOrNull { it.type in 1..2 && it.name == name } ?: return null
        if (entry.size <= 0) return ByteArray(0)

        if (sectorSize > 0 && entry.size >= miniCutoff) return followChain(entry.startSector, entry.size)

        val out = ByteArrayOutputStream(entry.size)
        var sector = entry.startSector
        var guard = 0
        while (sector >= 0 && sector < miniFat.size && guard < 200000) {
            val at = sector * miniSectorSize
            if (at + miniSectorSize > miniStream.size) break
            out.write(miniStream, at, miniSectorSize)
            sector = miniFat[sector]
            guard++
        }

        val raw = out.toByteArray()
        return if (raw.size > entry.size) raw.copyOf(entry.size) else raw
    }

    private fun firstMiniFatSector(bytes: ByteArray): Int? {
        val start = readLe32(bytes, 0x3C)
        val count = readLe32(bytes, 0x40)
        return if (start >= 0 && count > 0) start else null
    }

    private fun allocationTableSectorIds(fatCount: Int): List<Int> {
        val header = (0 until 109).map { readLe32(0x4C + 4 * it) }
        val ids = ArrayList<Int>(header.size + 8)
        ids.addAll(header.filter { it >= 0 })

        var sector = readLe32(0x44)
        var remaining = readLe32(0x48)
        while (sector >= 0 && remaining > 0 && (fatCount <= 0 || ids.size < fatCount)) {
            val at = sectorOffset(sector)
            if (at + sectorSize > byteCount()) break

            val perSector = sectorSize / 4 - 1
            for (index in 0 until perSector) {
                val value = readLe32(at + 4 * index)
                if (value >= 0) ids += value
            }
            sector = readLe32(at + 4 * perSector)
            remaining--
        }

        return if (fatCount > 0) ids.take(fatCount) else ids
    }

    private fun directoryEntries(firstSector: Int): List<Entry> {
        val raw = followChain(firstSector, 0)
        val found = ArrayList<Entry>()

        var cursor = 0
        while (cursor + 128 <= raw.size) {
            val nameLength = readLe16(raw, cursor + 0x40)
            val type = raw[cursor + 0x42].toInt()
            if (type in 1..2 || type == 5) {
                if (nameLength in 4..64) {
                    found += Entry(
                        name = String(raw, cursor, nameLength - 2, Charsets.UTF_16LE),
                        type = type,
                        startSector = readLe32(raw, cursor + 0x74),
                        size = readLe32(raw, cursor + 0x78)
                    )
                }
            }
            cursor += 128
        }

        return found
    }

    private fun followChain(start: Int, byteLimit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var sector = start
        val maxSteps = byteCount() / sectorSize + 2
        var guard = 0

        while (sector >= 0 && sector < fat.size && guard < maxSteps) {
            val at = sectorOffset(sector)
            if (at + sectorSize > byteCount()) break
            out.write(source, at, sectorSize)
            sector = fat[sector]
            guard++
            if (byteLimit > 0 && out.size() >= byteLimit) break
        }

        val raw = out.toByteArray()
        return if (byteLimit > 0 && raw.size > byteLimit) raw.copyOf(byteLimit) else raw
    }

    private fun sectorsOf(ids: List<Int>): ByteArray {
        val out = ByteArrayOutputStream(ids.size * sectorSize)
        for (id in ids) {
            val at = sectorOffset(id)
            if (at + sectorSize <= byteCount()) out.write(source, at, sectorSize)
        }
        return out.toByteArray()
    }

    private fun sectorOffset(sector: Int): Int = (sector + 1) * sectorSize

    private fun byteCount(): Int = source.size

    private fun readLe32(at: Int): Int = readLe32(source, at)
}

private fun matchesCompoundSignature(data: ByteArray): Boolean {
    for (index in COMPOUND_SIGNATURE.indices) {
        if (data[index] != COMPOUND_SIGNATURE[index]) return false
    }
    return true
}
