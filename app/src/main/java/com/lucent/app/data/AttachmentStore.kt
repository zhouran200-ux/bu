package com.lucent.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object AttachmentStore {

    private const val DIR_NAME = "attachments"

    fun baseDir(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    fun fileFor(context: Context, id: String): File = File(baseDir(context), id)

    fun looksLikeId(value: String): Boolean {
        if (value.length != 36) return false
        return try {
            UUID.fromString(value); true
        } catch (e: Exception) {
            false
        }
    }

    fun importBytes(context: Context, bytes: ByteArray): String? {
        val id = UUID.randomUUID().toString()
        return if (writeBytes(context, id, bytes)) id else null
    }

    fun writeBytes(context: Context, id: String, bytes: ByteArray): Boolean = try {
        openOutputStream(context, id)?.use { it.write(bytes) } != null
    } catch (e: IOException) {
        delete(context, id)
        false
    }

    fun openOutputStream(context: Context, id: String): OutputStream? = try {
        fileFor(context, id).outputStream()
    } catch (t: Throwable) {
        null
    }

    fun openInputStream(context: Context, id: String): InputStream? = try {
        val file = fileFor(context, id)
        if (!file.exists()) null else file.inputStream()
    } catch (t: Throwable) {
        null
    }

    fun readBytes(context: Context, id: String, maxBytes: Long = 32L * 1024 * 1024): ByteArray? {
        if (sizeOf(context, id) > maxBytes) return null
        return try {
            openInputStream(context, id)?.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        }
    }

    fun exists(context: Context, id: String): Boolean = fileFor(context, id).exists()

    fun sizeOf(context: Context, id: String): Long = fileFor(context, id).length()

    fun delete(context: Context, id: String): Boolean {
        val f = fileFor(context, id)
        return if (f.exists()) f.delete() else true
    }

    fun pruneOrphans(context: Context, referencedIds: Set<String>) {
        baseDir(context).listFiles()?.forEach { f ->
            if (f.name !in referencedIds) f.delete()
        }
    }

    fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
    } catch (e: Exception) {
        null
    }
}
