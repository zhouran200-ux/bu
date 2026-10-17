package com.lucent.app.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal object DocumentInputLimits {
    const val MAX_INPUT_BYTES = 32 * 1024 * 1024
    const val MAX_XML_BYTES = 16 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 64 * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 4096

    fun read(input: InputStream, limit: Int): ByteArray {
        require(limit >= 0)
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (count == 0) {
                val next = input.read()
                if (next < 0) return output.toByteArray()
                total++
                if (total > limit) throw IOException("文档数据超过导入大小限制")
                output.write(next)
            } else {
                total += count
                if (total > limit) throw IOException("文档数据超过导入大小限制")
                output.write(buffer, 0, count)
            }
        }
    }

    fun discard(input: InputStream, limit: Int): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return total
            if (count == 0) {
                if (input.read() < 0) return total
                total++
            } else total += count
            if (total > limit) throw IOException("文档解压数据超过大小限制")
        }
    }

    fun checkInput(bytes: ByteArray) {
        require(bytes.size <= MAX_INPUT_BYTES) { "文件超过32MiB导入限制" }
    }
}
