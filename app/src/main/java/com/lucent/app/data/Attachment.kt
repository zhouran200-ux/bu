package com.lucent.app.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class Attachment(
    val mime: String,
    val data: String,
    val name: String
) {
    val isImage: Boolean get() = mime.startsWith("image/")

    val isVideo: Boolean get() = mime.startsWith("video/")

    val isAudio: Boolean get() = mime.startsWith("audio/")
}

object Attachments {

    fun parse(json: String?): List<Attachment> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Attachment(
                    mime = o.optString("mime", "application/octet-stream"),
                    data = o.optString("data", ""),
                    name = o.optString("name", "file")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serialize(list: List<Attachment>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("mime", it.mime)
                    .put("data", it.data)
                    .put("name", it.name)
            )
        }
        return arr.toString()
    }


    fun readBytes(context: Context, att: Attachment, maxBytes: Long = 32L * 1024 * 1024): ByteArray? {
        return if (AttachmentStore.looksLikeId(att.data)) {
            AttachmentStore.readBytes(context, att.data, maxBytes)
        } else {
            val approx = estimateDecodedBase64Size(att.data)
            if (approx > maxBytes) return null
            try {
                android.util.Base64.decode(att.data, android.util.Base64.DEFAULT)
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun openStream(context: Context, att: Attachment) =
        if (AttachmentStore.looksLikeId(att.data)) AttachmentStore.openInputStream(context, att.data) else null

    fun decodeText(context: Context, att: Attachment): String? {
        if (att.isImage) return null
        val looksTextual = att.mime.startsWith("text/") ||
            att.mime == "application/json" ||
            att.mime == "application/xml" ||
            att.mime.endsWith("+json") ||
            att.mime.endsWith("+xml") ||
            att.mime == "application/octet-stream"
        if (!looksTextual) return null
        val bytes = readBytes(context, att, maxBytes = 2L * 1024 * 1024) ?: return null
        if (bytes.any { it.toInt() == 0 }) return null
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    fun idsFromJson(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return parse(json)
            .map { it.data }
            .filter { AttachmentStore.looksLikeId(it) }
            .toSet()
    }

    fun estimateDecodedBase64Size(base64: String): Long {
        if (base64.isEmpty()) return 0
        val padding = when {
            base64.endsWith("==") -> 2
            base64.endsWith("=") -> 1
            else -> 0
        }
        return (base64.length.toLong() * 3 / 4) - padding
    }
}
