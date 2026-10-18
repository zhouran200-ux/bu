package com.lucent.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class AppDatabaseResidualCleanupTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate33To34_dropsRetiredObjectsAndResidualIndexes() {
        helper.createDatabase(DB_NAME, 33).apply {
            execSQL("CREATE INDEX IF NOT EXISTS `index_notes_isDraft` ON `notes` (`isDraft`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_notes_hidden` ON `notes` (`hidden`)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 34, true, MIGRATION_33_34)

        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'notes'").use { c ->
            val indexes = mutableListOf<String>()
            while (c.moveToNext()) indexes += c.getString(0)
            assertFalse("index_notes_isDraft must be dropped", "index_notes_isDraft" in indexes)
            assertFalse("index_notes_hidden must be dropped", "index_notes_hidden" in indexes)
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE name IN " +
                "('notes_fts', 'tasks_fts', 'tasks', 'task_versions', 'notes_fts_ai', 'tasks_fts_ai')"
        ).use { c ->
            assertFalse("retired search and task objects must be gone", c.moveToFirst())
        }
    }

    @Test
    fun migrate34To35_addsTheNoteTitleIndex() {
        helper.createDatabase(DB_NAME, 34).close()

        val db = helper.runMigrationsAndValidate(DB_NAME, 35, true, MIGRATION_34_35)

        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_notes_title'").use { c ->
            assertTrue("index_notes_title must exist", c.moveToFirst())
        }
    }

    private companion object {
        const val DB_NAME = "app-database-residual-cleanup-test"
    }
}
