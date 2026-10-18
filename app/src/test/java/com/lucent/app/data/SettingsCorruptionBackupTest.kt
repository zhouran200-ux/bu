package com.lucent.app.data

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsCorruptionBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun filesDir(): File = folder.newFolder("files")

    private fun writeStoreFile(filesDir: File, bytes: ByteArray): File {
        val store = File(filesDir, "datastore")
        store.mkdirs()
        return File(store, "lucent_settings.preferences_pb").apply { writeBytes(bytes) }
    }

    @Test
    fun corruptedStoreFileIsBackedUpWithOriginalBytes() {
        val filesDir = filesDir()
        val original = byteArrayOf(1, 2, 3, 4, 5, 6)
        writeStoreFile(filesDir, original)

        assertTrue(backupCorruptedSettingsFile(filesDir))

        val backup = File(filesDir, "datastore/lucent_settings.corrupt")
        assertTrue(backup.isFile)
        assertArrayEquals(original, backup.readBytes())
    }

    @Test
    fun backupIsRefreshedWhenTheFileIsCorruptedAgain() {
        val filesDir = filesDir()
        writeStoreFile(filesDir, byteArrayOf(1))
        assertTrue(backupCorruptedSettingsFile(filesDir))
        writeStoreFile(filesDir, byteArrayOf(2, 2))
        assertTrue(backupCorruptedSettingsFile(filesDir))
        assertArrayEquals(byteArrayOf(2, 2), File(filesDir, "datastore/lucent_settings.corrupt").readBytes())
    }

    @Test
    fun backupHelperReportsFalseWhenTheStoreFileIsMissing() {
        assertFalse(backupCorruptedSettingsFile(filesDir()))
    }

    @Test
    fun corruptHandlerBacksUpBeforeHandingBackEmptyPreferences() = runBlocking {
        val filesDir = filesDir()
        val original = byteArrayOf(7, 7, 7, 7)
        writeStoreFile(filesDir, original)
        var backupResult: Boolean? = null

        val store = createSettingsDataStore(filesDir) { backupResult = it }
        try {
            store.data.first()
        } catch (e: Exception) {
        }

        assertEquals(true, backupResult)
        assertArrayEquals(original, File(filesDir, "datastore/lucent_settings.corrupt").readBytes())
    }
}
