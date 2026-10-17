package com.lucent.app.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream

object FontStore {

    const val MAX_FONTS = 12
    const val MAX_FONT_BYTES = 32L * 1024 * 1024

    private const val DIR = "fonts"

    private const val INDEX_FILE = "fonts.json"

    private val MAGIC_TTF = byteArrayOf(0x00, 0x01, 0x00, 0x00)
    private val MAGIC_TRUE = byteArrayOf(0x74, 0x72, 0x75, 0x65)
    private val MAGIC_OTF = byteArrayOf(0x4F, 0x54, 0x54, 0x4F)
    private val MAGIC_TTC = byteArrayOf(0x74, 0x74, 0x63, 0x66)

    class NotFontException : IOException("Not a usable font file")
    class TooManyFontsException : IOException("Font slots are full")

    data class FontSlot(val id: String, val name: String, val fileName: String)

    data class FontIndex(val slots: List<FontSlot>)

    private fun dir(context: Context): File = File(context.filesDir, DIR)


    @Synchronized
    fun index(context: Context): FontIndex {
        val dir = dir(context)
        val indexFile = File(dir, INDEX_FILE)
        if (!indexFile.exists() && !File(dir, INDEX_FILE + ".bak").exists()) return FontIndex(emptyList())

        return try {
            val root = JSONObject(AtomicFile(indexFile).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            val arr = root.optJSONArray("fonts") ?: JSONArray()
            recoverDeletions(dir, (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("file") }.toSet())
            val slots = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "").ifBlank { return@mapNotNull null }
                val fileName = o.optString("file", "").ifBlank { return@mapNotNull null }
                val file = safeFontFile(dir, fileName) ?: return@mapNotNull null
                if (!file.isFile || file.length() <= 0L) return@mapNotNull null
                FontSlot(id = id, name = o.optString("name", fileName), fileName = fileName)
            }
            FontIndex(slots)
        } catch (_: Throwable) {
            rebuildFromFiles(context)
        }
    }

    @Synchronized
    private fun rebuildFromFiles(context: Context): FontIndex {
        val dir = dir(context)
        val files = dir.listFiles()?.filter { f ->
            safeFontFile(dir, f.name) != null && f.isFile && f.length() > 0L && listOf(".ttf", ".otf", ".ttc").any {
                f.name.lowercase().endsWith(it)
            }
        }.orEmpty().sortedBy { it.name }
        if (files.isEmpty()) return FontIndex(emptyList())
        val slots = files.take(MAX_FONTS).map { f ->
            val id = f.name.removePrefix("font_").substringBeforeLast('.').ifBlank { newId() }
            FontSlot(id = id, name = f.name, fileName = f.name)
        }
        val rebuilt = FontIndex(slots)
        try {
            writeIndex(context, rebuilt)
        } catch (_: Throwable) {
        }
        return rebuilt
    }

    @Synchronized
    private fun writeIndex(context: Context, idx: FontIndex) {
        val dir = dir(context)
        if (!dir.exists()) dir.mkdirs()
        val arr = JSONArray()
        idx.slots.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("name", s.name).put("file", s.fileName))
        }
        val root = JSONObject().put("fonts", arr)
        val atomic = AtomicFile(File(dir, INDEX_FILE))
        val output = atomic.startWrite()
        try {
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (failure: Throwable) {
            atomic.failWrite(output)
            throw failure
        }
    }


    fun fonts(context: Context): List<FontSlot> = index(context).slots

    fun fontFile(context: Context, id: String): File? =
        index(context).slots.firstOrNull { it.id == id }
            ?.let { safeFontFile(dir(context), it.fileName) }
            ?.takeIf { it.exists() && it.length() > 0L }

    fun canImportMore(context: Context): Boolean = fonts(context).size < MAX_FONTS


    @Throws(IOException::class)
    fun import(context: Context, source: PlatformFontSource, customName: String? = null): FontSlot {
        val existing = index(context)
        if (existing.slots.size >= MAX_FONTS) throw TooManyFontsException()

        val dir = dir(context)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Could not create the font directory")

        val id = newId()
        val pickedName = fontSourceDisplayName(context, source) ?: "font"

        openFontSource(context, source)?.use { raw ->
            val head = ByteArray(4)
            val headRead = readUpTo(raw, head)
            val ext = classify(head, headRead) ?: throw NotFontException()
            val fileName = "font_$id.$ext"
            val tmp = File(dir, "$fileName.tmp")
            try {
                copyPrefixed(head, headRead, raw, tmp)
                val name = customName?.trim()?.take(60)?.takeIf { it.isNotBlank() }
                    ?: pickedName.substringBeforeLast('.')
                val slot = FontSlot(id = id, name = name, fileName = fileName)
                synchronized(this) {
                    val latest = index(context)
                    if (latest.slots.size >= MAX_FONTS) throw TooManyFontsException()
                    val target = File(dir, fileName)
                    if (!tmp.renameTo(target)) throw IOException("Could not finalize the font file")
                    try {
                        writeIndex(context, FontIndex(latest.slots + slot))
                    } catch (failure: Throwable) {
                        target.delete()
                        throw failure
                    }
                }
                return slot
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        } ?: throw IOException("Could not open the selected file")
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val idx = index(context)
        val slot = idx.slots.firstOrNull { it.id == id } ?: return
        val directory = dir(context)
        val target = safeFontFile(directory, slot.fileName) ?: throw IOException("Invalid font path")
        val staging = File(directory, slot.fileName + ".deleting")
        if (staging.exists()) throw IOException("Font deletion recovery required")
        if (!target.renameTo(staging)) throw IOException("Could not stage font deletion")
        try {
            writeIndex(context, FontIndex(idx.slots.filter { it.id != id }))
        } catch (failure: Throwable) {
            if (!staging.renameTo(target)) failure.addSuppressed(IOException("Could not restore font after failed deletion"))
            throw failure
        }
        staging.delete()
        File(directory, slot.fileName + ".tmp").delete()
    }


    private fun recoverDeletions(directory: File, referenced: Set<String>) {
        for (staging in directory.listFiles().orEmpty()) {
            if (!staging.name.endsWith(".deleting") || !staging.isFile) continue
            val originalName = staging.name.removeSuffix(".deleting")
            val original = safeFontFile(directory, originalName) ?: continue
            if (originalName in referenced) {
                if (!original.exists() && !staging.renameTo(original)) throw IOException("Could not recover staged font")
                if (original.exists() && staging.exists()) staging.delete()
            } else staging.delete()
        }
    }

    private fun safeFontFile(directory: File, name: String): File? {
        if (name.isBlank() || File(name).name != name || name.contains(47.toChar()) || name.contains(92.toChar())) return null
        val candidate = File(directory, name)
        return candidate.takeIf { it.canonicalFile.parentFile == directory.canonicalFile }
    }

    private fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    private fun classify(head: ByteArray, headLen: Int): String? {
        if (headLen < 4) return null
        return when {
            head.contentEquals(MAGIC_TTF) || head.contentEquals(MAGIC_TRUE) -> "ttf"
            head.contentEquals(MAGIC_OTF) -> "otf"
            head.contentEquals(MAGIC_TTC) -> "ttc"
            else -> null
        }
    }

    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        return read
    }

    private fun copyPrefixed(prefix: ByteArray, prefixLen: Int, input: InputStream, out: File) {
        out.outputStream().use { os ->
            os.write(prefix, 0, prefixLen)
            var total = prefixLen.toLong()
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_FONT_BYTES) throw IOException("Font file exceeds 32 MiB limit")
                os.write(buf, 0, n)
            }
            os.flush()
        }
    }
}
