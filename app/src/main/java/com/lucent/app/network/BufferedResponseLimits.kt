package com.lucent.app.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import okhttp3.ResponseBody

internal object BufferedResponseLimits {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun read(body: ResponseBody, limit: Int = MAX_BYTES): String {
        require(limit >= 0)
        if (body.contentLength() > limit) throw IOException("Response exceeds buffered size limit")
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val source = body.source()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val remaining = (limit.toLong() - total + 1).coerceAtMost(buffer.size.toLong()).toInt()
            val count = source.read(buffer, 0, remaining)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("Response exceeds buffered size limit")
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        return String(bytes, charset).removePrefix("\uFEFF")
    }
}
