package com.lucent.app.data

import java.io.File
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder

class DatabaseFileGuardTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun missingDatabaseCanBeCreatedNormally() {
        val file = File(folder.root, "missing.db")
        DatabaseFileGuard.requireReadable(file)
        assertFalse(file.exists())
    }

    @Test fun validHeaderLeavesMainAndSidecarsUntouched() {
        val file = folder.newFile("lucent.db")
        val bytes = "SQLite format 3\u0000payload".toByteArray(Charsets.ISO_8859_1)
        file.writeBytes(bytes)
        val wal = folder.newFile("lucent.db-wal").apply { writeText("wal") }
        DatabaseFileGuard.requireReadable(file)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals("wal", wal.readText())
    }

    @Test fun invalidAndShortFilesAreNeverDeletedMovedOrRewritten() {
        for (body in listOf("encrypted-data", "SQLite format 3X", "")) {
            val file = File(folder.root, "lucent.db").apply { writeText(body) }
            val backup = File(folder.root, "lucent.db.pre-encrypt").apply { writeText("backup") }
            val wal = File(folder.root, "lucent.db-wal").apply { writeText("wal") }
            try { DatabaseFileGuard.requireReadable(file); fail("must reject unknown file") }
            catch (_: IOException) { }
            assertEquals(body, file.readText())
            assertEquals("backup", backup.readText())
            assertEquals("wal", wal.readText())
        }
    }
}
