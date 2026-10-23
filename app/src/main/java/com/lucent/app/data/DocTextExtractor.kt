package com.lucent.app.data

object DocTextExtractor {

    private const val SIGNATURE_MARK = 0xA5EC
    private const val TEXT_LENGTH_AT = 0x4C
    private const val TABLE_FLAG_AT = 0x0A
    private const val CLX_OFFSET_AT = 0x1A2
    private const val CLX_LENGTH_AT = 0x1A6
    private const val COMPRESSED_FLAG = 0x40000000

    fun extract(bytes: ByteArray): String {
        val store = CompoundFile(bytes)

        val document = store.stream("WordDocument")
            ?: error("无效的 DOC 文件结构 (缺少 WordDocument 流)")
        if (document.size < 0x1AA || readLe16(document, 0) != SIGNATURE_MARK) {
            error("无效的 DOC 文件结构 (文件标识不符)")
        }

        val tableName = if (readLe16(document, TABLE_FLAG_AT) and 0x0100 != 0) "1Table" else "0Table"
        val table = store.stream(tableName)
            ?: error("无效的 DOC 文件结构 (缺少 $tableName 流)")

        val textLength = readLe32(document, TEXT_LENGTH_AT)
        if (textLength <= 0) error("这份文档里没有可提取的正文")

        val clxStart = readLe32(document, CLX_OFFSET_AT)
        val clxLength = readLe32(document, CLX_LENGTH_AT)
        require(clxStart >= 0 && clxLength >= 0 && clxStart.toLong() + clxLength <= table.size.toLong()) { "DOC片段表偏移越界" }
        val plc = pieceTable(table, clxStart, clxStart + clxLength)
            ?: error("无法解析这份文档的正文片段表")

        return normalizeWordText(extractMainStory(document, plc, textLength))
    }

    internal fun extractMainStory(document: ByteArray, plc: ByteArray, textLength: Int): String {
        require(textLength >= 0 && plc.size >= 16 && (plc.size - 4) % 12 == 0) { "DOC片段表格式无效" }
        val out = StringBuilder()
        val pieces = (plc.size - 4) / 12
        for (index in 0 until pieces) {
            val startCp = readLe32(plc, 4 * index)
            val rawEndCp = readLe32(plc, 4 * (index + 1))
            require(startCp >= 0 && rawEndCp >= startCp) { "DOC正文字符范围越界" }
            if (startCp >= textLength || rawEndCp == startCp) continue
            val endCp = minOf(rawEndCp, textLength)
            val fc = readLe32(plc, 4 * (pieces + 1) + 8 * index + 2)
            appendPiece(out, document, startCp, endCp, fc)
        }
        return out.toString()
    }

    private fun pieceTable(table: ByteArray, from: Int, until: Int): ByteArray? {
        if (from < 0 || until < from || until > table.size || until.toLong() - from < 5) return null

        var cursor = from
        while (cursor + 3 < until && table[cursor].toInt() == 0x01) {
            cursor += 3 + readLe16(table, cursor + 1)
        }
        if (cursor + 5 > table.size || table[cursor].toInt() != 0x02) return null

        val length = readLe32(table, cursor + 1)
        if (length < 16 || cursor.toLong() + 5 + length > until.toLong()) return null
        return table.copyOfRange(cursor + 5, cursor + 5 + length)
    }

    private fun appendPiece(out: StringBuilder, document: ByteArray, startCp: Int, endCp: Int, fc: Int) {
        require(startCp >= 0 && endCp >= startCp) { "DOC正文字符范围越界" }
        if (endCp == startCp) return
        require(out.length.toLong() + endCp - startCp <= DocumentInputLimits.MAX_INPUT_BYTES) { "DOC正文超过大小限制" }

        val chars = endCp - startCp
        val located = fc and 0x3FFFFFFF

        if (fc and COMPRESSED_FLAG != 0) {
            val from = located / 2
            require(from.toLong() + chars <= document.size.toLong()) { "DOC正文偏移越界" }
            for (step in 0 until chars) {
                val at = from + step
                out.append((document[at].toInt() and 0xFF).toChar())
            }
        } else {
            for (step in 0 until chars) {
                val at = located.toLong() + 2L * step
                require(at + 1 < document.size.toLong()) { "DOC正文偏移越界" }
                out.append(readLe16(document, at.toInt()).toChar())
            }
        }
    }

    private fun normalizeWordText(raw: String): String {
        val out = StringBuilder(raw.length)
        for (ch in raw) {
            val code = ch.code
            when {
                code == 0x0D || code == 0x07 || code == 0x0B || code == 0x0C ->
                    if (out.isEmpty() || out.last() != '\n') out.append('\n')
                code == 0x00 || code == 0x01 || code == 0x02 || code == 0x08 || code == 0x0E -> Unit
                code in 0x13..0x15 || code in 0x1C..0x1F -> Unit
                else -> out.append(ch)
            }
        }
        return out.toString().trim()
    }
}
