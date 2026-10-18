package com.lucent.app.data

import androidx.datastore.core.CorruptionException
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsCorruptionBackupTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val pending = CorruptionException("broken preferences")

    private fun writePreferences(bytes: ByteArray): File {
        val directory = File(folder.root, "datastore")
        directory.mkdirs()
        return File(directory, "lucent_settings.preferences_pb").apply { writeBytes(bytes) }
    }

    @Test
    fun copiesTheCorruptFileBeforeResetSoKeysCanBeRecovered() {
        val original = byteArrayOf(1, 2, 3, 4)
        writePreferences(original)

        assertTrue(backupCorruptedSettings(folder.root, pending))

        val backup = File(folder.root, "datastore/lucent_settings.corrupt")
        assertTrue(backup.isFile)
        assertArrayEquals(original, backup.readBytes())
    }

    @Test
    fun reportsNoBackupWhenNothingWasCorrupted() {
        writePreferences(byteArrayOf(1))
        assertFalse(backupCorruptedSettings(folder.root, null))
        assertFalse(File(folder.root, "datastore/lucent_settings.corrupt").exists())
    }

    @Test
    fun reportsNoBackupWhenTheStoreFileIsMissing() {
        assertFalse(backupCorruptedSettings(folder.root, pending))
    }

    @Test
    fun overwritesAnEarlierBackupWithTheNewestCorruptContent() {
        val old = File(folder.root, "datastore").apply { mkdirs() }
        File(old, "lucent_settings.corrupt").writeBytes(byteArrayOf(9, 9))
        writePreferences(byteArrayOf(5, 6))

        assertTrue(backupCorruptedSettings(folder.root, pending))
        assertArrayEquals(byteArrayOf(5, 6), File(old, "lucent_settings.corrupt").readBytes())
    }
}
