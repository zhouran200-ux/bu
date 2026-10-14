package com.lucent.app.data

import java.io.File
import java.io.IOException

internal object DatabaseFileGuard {
    private val header = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    fun requireReadable(dbFile: File) {
        if (!dbFile.exists()) return
        val actual = ByteArray(header.size)
        val count = dbFile.inputStream().use { input ->
            var total = 0
            while (total < actual.size) {
                val read = input.read(actual, total, actual.size - total)
                if (read <= 0) break
                total += read
            }
            total
        }
        if (count != header.size || !actual.contentEquals(header)) {
            throw IOException("数据库格式无法识别，已保留原文件及备份，未重建或删除资料")
        }
    }
}
