package com.lucent.app.data

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val TEST_DB = "app-database-migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val legacyOpenHelpers = mutableListOf<SupportSQLiteOpenHelper>()
    private val legacyDatabaseName = "app-database-legacy-migration-test"
    private val legacyMigrations = arrayOf(
        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
        MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
        MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
        MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
        MIGRATION_24_25
    )

    @After
    fun closeLegacyDatabases() {
        legacyOpenHelpers.forEach { it.close() }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(legacyDatabaseName)
    }

    private fun createLegacyDatabase(version: Int): SupportSQLiteDatabase {
        require(version in 2..25)
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(legacyDatabaseName)
        return openLegacyDatabase(object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, body TEXT NOT NULL, updatedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, isDone INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE chat_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "role TEXT NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL, " +
                        "attachmentMime TEXT, attachmentData TEXT, attachmentName TEXT)"
                )
                applyLegacyMigrations(db, 2, version, legacyMigrations)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("Legacy fixture must be created from an empty database")
            }
        })
    }

    private fun runLegacyMigrations(
        version: Int,
        vararg migrations: Migration
    ): SupportSQLiteDatabase = openLegacyDatabase(object : SupportSQLiteOpenHelper.Callback(version) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            error("Create and seed the legacy fixture before migrating it")
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            applyLegacyMigrations(db, oldVersion, newVersion, migrations)
        }
    })

    private fun openLegacyDatabase(callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteDatabase {
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(
                InstrumentationRegistry.getInstrumentation().targetContext
            ).name(legacyDatabaseName).callback(callback).build()
        )
        legacyOpenHelpers += openHelper
        return openHelper.writableDatabase
    }

    private fun applyLegacyMigrations(
        db: SupportSQLiteDatabase,
        fromVersion: Int,
        toVersion: Int,
        migrations: Array<out Migration>
    ) {
        var version = fromVersion
        while (version < toVersion) {
            val migration = migrations.single {
                it.startVersion == version && it.endVersion == version + 1
            }
            migration.migrate(db)
            version = migration.endVersion
        }
        assertEquals("The complete legacy migration path must run", toVersion, version)
    }

    @Test
    fun migrate2To3_addsNotesTagsColumnDefaultingToEmpty() {
        createLegacyDatabase(2).apply {
            execSQL("INSERT INTO notes (id, title, body, updatedAt) VALUES (1, 'Title', 'Body', 1000)")
            close()
        }

        val db = runLegacyMigrations(3, MIGRATION_2_3)

        db.query("SELECT title, tags FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Title", c.getString(0))
            assertEquals("", c.getString(1))
        }
    }


    @Test
    fun migrate3To4_addsAttachmentsColumnToNotesAndTasks() {
        createLegacyDatabase(3).apply {
            execSQL("INSERT INTO notes (id, title, body, updatedAt, tags) VALUES (1, 'N', 'B', 1000, '')")
            execSQL("INSERT INTO tasks (id, title, isDone, createdAt) VALUES (1, 'T', 0, 1000)")
            close()
        }

        val db = runLegacyMigrations(4, MIGRATION_3_4)

        db.query("SELECT attachments FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("[]", c.getString(0))
        }
        db.query("SELECT attachments FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("[]", c.getString(0))
        }
    }


    @Test
    fun migrate4To5_addsTasksDueAtColumnDefaultingToNull() {
        createLegacyDatabase(4).apply {
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments) " +
                    "VALUES (1, 'T', 0, 1000, '[]')"
            )
            close()
        }

        val db = runLegacyMigrations(5, MIGRATION_4_5)

        db.query("SELECT dueAt FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }


    @Test
    fun migrate5To6_addsTasksNotesColumnDefaultingToEmpty() {
        createLegacyDatabase(5).apply {
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt) " +
                    "VALUES (1, 'T', 0, 1000, '[]', NULL)"
            )
            close()
        }

        val db = runLegacyMigrations(6, MIGRATION_5_6)

        db.query("SELECT notes FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("", c.getString(0))
        }
    }


    @Test
    fun migrate6To7_backfillsCompletedAtForAlreadyDoneTasksOnly() {
        createLegacyDatabase(6).apply {
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes) " +
                    "VALUES (1, 'Done already', 1, 5000, '[]', NULL, '')"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes) " +
                    "VALUES (2, 'Still pending', 0, 6000, '[]', NULL, '')"
            )
            close()
        }

        val db = runLegacyMigrations(7, MIGRATION_6_7)

        db.query("SELECT completedAt FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(5000L, c.getLong(0))
        }
        db.query("SELECT completedAt FROM tasks WHERE id = 2").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }


    @Test
    fun migrate7To8_seedsOneConversationAndBackfillsExistingMessagesWhenHistoryExists() {
        createLegacyDatabase(7).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName) VALUES (1, 'user', 'hello', 1000, NULL, NULL, NULL)"
            )
            close()
        }

        val db = runLegacyMigrations(8, MIGRATION_7_8)

        db.query("SELECT COUNT(*) FROM chat_conversations").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        db.query("SELECT conversationId FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1L, c.getLong(0))
        }
    }

    @Test
    fun migrate7To8_seedsNoConversationWhenThereIsNoExistingChatHistory() {
        createLegacyDatabase(7).close()

        val db = runLegacyMigrations(8, MIGRATION_7_8)

        db.query("SELECT COUNT(*) FROM chat_conversations").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
    }


    @Test
    fun migrate8To9_addsNotesArchivedColumns() {
        createLegacyDatabase(8).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments) " +
                    "VALUES (1, 'N', 'B', 1000, '', '[]')"
            )
            close()
        }

        val db = runLegacyMigrations(9, MIGRATION_8_9)

        db.query("SELECT archived, archivedAt FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertTrue(c.isNull(1))
        }
    }


    @Test
    fun migrate9To10_addsNoteAndTaskOrganizationColumnsPlusUsableNoteVersionsTable() {
        createLegacyDatabase(9).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, archivedAt) " +
                    "VALUES (1, 'N', 'B', 1000, '', '[]', 0, NULL)"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, completedAt) " +
                    "VALUES (1, 'T', 0, 1000, '[]', NULL, '', NULL)"
            )
            close()
        }

        val db = runLegacyMigrations(10, MIGRATION_9_10)

        db.query(
            "SELECT pinned, color, isChecklist, checklist, trashedAt FROM notes WHERE id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals("", c.getString(1))
            assertEquals(0, c.getInt(2))
            assertEquals("[]", c.getString(3))
            assertTrue(c.isNull(4))
        }
        db.query(
            "SELECT priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt " +
                "FROM tasks WHERE id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertEquals("[]", c.getString(2))
            assertEquals("NONE", c.getString(3))
            assertEquals(0, c.getInt(4))
            assertTrue(c.isNull(5))
        }

        db.execSQL(
            "INSERT INTO note_versions (id, noteId, title, body, tags, isChecklist, checklist, savedAt) " +
                "VALUES (1, 1, 'N', 'B', '', 0, '[]', 1000)"
        )
        db.query("SELECT COUNT(*) FROM note_versions WHERE noteId = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }


    @Test
    fun migrate10To11_addsChatMessagesTokensColumnAndListQueryIndices() {
        createLegacyDatabase(10).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName, conversationId) " +
                    "VALUES (1, 'user', 'hi', 1000, NULL, NULL, NULL, 1)"
            )
            close()
        }

        val db = runLegacyMigrations(11, MIGRATION_10_11)

        db.query("SELECT tokens FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_notes_updatedAt'"
        ).use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }


    @Test
    fun migrate11To12_addsDraftAndHiddenColumnsPlusUsableTaskVersionsTable() {
        createLegacyDatabase(11).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt) " +
                    "VALUES (1, 'N', 'B', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', NULL)"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, " +
                    "completedAt, priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt) " +
                    "VALUES (1, 'T', 0, 1000, '[]', NULL, '', NULL, 0, 0, '[]', 'NONE', 0, NULL)"
            )
            close()
        }

        val db = runLegacyMigrations(12, MIGRATION_11_12)

        db.query("SELECT manualOrder, isDraft, draftSavedAt, hidden FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.isNull(2))
            assertEquals(0, c.getInt(3))
        }
        db.query("SELECT manualOrder, isDraft, draftSavedAt, hidden FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.isNull(2))
            assertEquals(0, c.getInt(3))
        }

        db.execSQL(
            "INSERT INTO task_versions (id, taskId, title, notes, subtasks, priority, dueAt, savedAt) " +
                "VALUES (1, 1, 'T', '', '[]', 0, NULL, 1000)"
        )
        db.query("SELECT COUNT(*) FROM task_versions WHERE taskId = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
    }


    @Test
    fun migrate12To13_addsNotesDoodleColumns() {
        createLegacyDatabase(12).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden) VALUES " +
                    "(1, 'N', 'B', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', NULL, 0, 0, NULL, 0)"
            )
            close()
        }

        val db = runLegacyMigrations(13, MIGRATION_12_13)

        db.query("SELECT isDoodle, doodle FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals("", c.getString(1))
        }
    }


    @Test
    fun migrate13To14_addsChatMessagesReplyToIdColumn() {
        createLegacyDatabase(13).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName, conversationId, tokens) " +
                    "VALUES (1, 'assistant', 'hi', 1000, NULL, NULL, NULL, 1, 12)"
            )
            close()
        }

        val db = runLegacyMigrations(14, MIGRATION_13_14)

        db.query("SELECT replyToId FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0L, c.getLong(0))
        }
    }


    @Test
    fun migrate14To15_addsRichTextSpanColumnsToNotesAndTasks() {
        createLegacyDatabase(14).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden, isDoodle, doodle) VALUES " +
                    "(1, 'N', 'B', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', NULL, 0, 0, NULL, 0, 0, '')"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, " +
                    "completedAt, priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt, " +
                    "manualOrder, isDraft, draftSavedAt, hidden) VALUES " +
                    "(1, 'T', 0, 1000, '[]', NULL, '', NULL, 0, 0, '[]', 'NONE', 0, NULL, 0, 0, NULL, 0)"
            )
            close()
        }

        val db = runLegacyMigrations(15, MIGRATION_14_15)

        db.query("SELECT bodySpans FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("", c.getString(0))
        }
        db.query("SELECT notesSpans FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("", c.getString(0))
        }
    }


    @Test
    fun migrate15To16_addsChatMessagesAttachmentListColumn() {
        createLegacyDatabase(15).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName, conversationId, tokens, replyToId) " +
                    "VALUES (1, 'user', 'hi', 1000, NULL, NULL, NULL, 1, 0, 0)"
            )
            close()
        }

        val db = runLegacyMigrations(16, MIGRATION_15_16)

        db.query("SELECT attachmentList FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }


    @Test
    fun migrate16To17_createsUsableNotebookAndNotebookItemTables() {
        createLegacyDatabase(16).close()

        val db = runLegacyMigrations(17, MIGRATION_16_17)

        db.execSQL("INSERT INTO notebooks (id, title, createdAt, updatedAt) VALUES (1, 'Trips', 1000, 1000)")
        db.execSQL(
            "INSERT INTO notebook_items (id, notebookId, itemKind, itemId, addedAt) " +
                "VALUES (1, 1, 'NOTE', 1, 1000)"
        )

        db.query("SELECT title FROM notebooks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Trips", c.getString(0))
        }
        db.query("SELECT itemKind, itemId FROM notebook_items WHERE notebookId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("NOTE", c.getString(0))
            assertEquals(1L, c.getLong(1))
        }
    }


    @Test
    fun migrate17To18_addsFtsTablesWhenAvailableAndNeverBreaksTheBaseTables() {
        createLegacyDatabase(17).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden, isDoodle, doodle, bodySpans) VALUES " +
                    "(1, 'FTS seed', 'a searchable body', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', " +
                    "NULL, 0, 0, NULL, 0, 0, '', '')"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, " +
                    "completedAt, priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt, " +
                    "manualOrder, isDraft, draftSavedAt, hidden, notesSpans) VALUES " +
                    "(1, 'FTS task', 0, 1000, '[]', NULL, 'notes body', NULL, 0, 0, '[]', 'NONE', 0, " +
                    "NULL, 0, 0, NULL, 0, '')"
            )
            close()
        }

        val db = runLegacyMigrations(18, MIGRATION_17_18)

        db.query("SELECT title FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("FTS seed", c.getString(0))
        }
        db.query("SELECT title FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("FTS task", c.getString(0))
        }

        val ftsAvailable = try {
            db.query("SELECT name FROM sqlite_master WHERE name = 'notes_fts'").use { it.moveToFirst() }
        } catch (t: Throwable) {
            false
        }
        if (ftsAvailable) {
            db.query("SELECT title FROM notes_fts WHERE rowid = 1").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("FTS seed", c.getString(0))
            }
            db.query("SELECT title FROM tasks_fts WHERE rowid = 1").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("FTS task", c.getString(0))
            }
        }
    }


    @Test
    fun fullChain2To18_migratesSeedDataThroughEveryRegisteredStepInOrder() {
        createLegacyDatabase(2).apply {
            execSQL("INSERT INTO notes (id, title, body, updatedAt) VALUES (1, 'Seed note', 'Seed body', 1000)")
            execSQL("INSERT INTO tasks (id, title, isDone, createdAt) VALUES (1, 'Seed task', 1, 500)")
            close()
        }

        val db = runLegacyMigrations(
            18,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
            MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
            MIGRATION_16_17, MIGRATION_17_18
        )

        db.query("SELECT title, tags, hidden, bodySpans, doodle FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Seed note", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(0, c.getInt(2))
            assertEquals("", c.getString(3))
            assertEquals("", c.getString(4))
        }
        db.query("SELECT title, isDone, completedAt, notesSpans FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Seed task", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(500L, c.getLong(2))
            assertEquals("", c.getString(3))
        }
    }

    @Test
    fun migrate19To20_addsChatMessagesAgentTraceColumn() {
        createLegacyDatabase(19).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName, attachmentList, conversationId, tokens, replyToId) " +
                    "VALUES (1, 'assistant', 'hi', 1000, NULL, NULL, NULL, NULL, 1, 0, 0)"
            )
            close()
        }

        val db = runLegacyMigrations(20, MIGRATION_19_20)

        db.query("SELECT agentTrace FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }

    @Test
    fun migrate20To21_addsFormatOverrideColumnsToNotesAndTasks() {
        createLegacyDatabase(20).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden, isDoodle, doodle, bodySpans) VALUES " +
                    "(1, 'N', 'B', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', NULL, 0, 0, NULL, 0, 0, '', '')"
            )
            execSQL(
                "INSERT INTO tasks (id, title, isDone, createdAt, attachments, dueAt, notes, " +
                    "completedAt, priority, pinned, subtasks, repeatRule, reminderEnabled, trashedAt, " +
                    "manualOrder, isDraft, draftSavedAt, hidden, notesSpans) VALUES " +
                    "(1, 'T', 0, 1000, '[]', NULL, '', NULL, 0, 0, '[]', 'NONE', 0, NULL, 0, 0, NULL, 0, '')"
            )
            close()
        }

        val db = runLegacyMigrations(21, MIGRATION_20_21)

        db.query("SELECT formatOverride FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
        db.query("SELECT formatOverride FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }

    @Test
    fun migrate21To22_addsCoverOrderAndTrashToNotebooks() {
        createLegacyDatabase(21).apply {
            execSQL(
                "INSERT INTO notebooks (id, title, createdAt, updatedAt) VALUES (1, 'Ideas', 1000, 1000)"
            )
            close()
        }

        val db = runLegacyMigrations(22, MIGRATION_21_22)

        db.query("SELECT title, color, manualOrder, trashedAt FROM notebooks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ideas", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(0, c.getInt(2))
            assertTrue(c.isNull(3))
        }
    }

    @Test
    fun migrate22To23_addsPinnedToNotebooks() {
        createLegacyDatabase(22).apply {
            execSQL(
                "INSERT INTO notebooks (id, title, createdAt, updatedAt, color, manualOrder, trashedAt) " +
                    "VALUES (1, 'Trips', 1000, 1000, 'ocean', 0, NULL)"
            )
            close()
        }

        val db = runLegacyMigrations(23, MIGRATION_22_23)

        db.query("SELECT title, color, pinned FROM notebooks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Trips", c.getString(0))
            assertEquals("ocean", c.getString(1))
            assertEquals(0, c.getInt(2))
        }
    }

    @Test
    fun migrate26To27_dropsAgentTraceColumn() {
        helper.createDatabase(TEST_DB, 26).apply {
            execSQL(
                "INSERT INTO chat_messages (id, conversationId, role, content, timestamp, tokens, replyToId, agentTrace) " +
                    "VALUES (1, 1, 'user', 'Hello', 1000, 7, 0, 'retired trace')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 27, true, MIGRATION_26_27)

        db.query("SELECT * FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getColumnIndex("agentTrace") == -1)
        }
    }

    @Test
    fun migrate27To28_dropsNoteVersionAndEmbeddingTables() {
        helper.createDatabase(TEST_DB, 27).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "pinned, color, manualOrder, isDraft, hidden, bodySpans) " +
                    "VALUES (1, 'Keep me', 'Body', 1000, '', '[]', 0, 0, '', 0, 0, 0, '')"
            )
            execSQL("INSERT INTO note_versions (id, noteId, title, body, tags, savedAt) VALUES (1, 1, 'Old', 'Old body', '', 900)")
            execSQL("INSERT INTO note_embeddings (noteId, model, dim, vec, updatedAt) VALUES (1, 'old-model', 1, X'00000000', 900)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 28, true, MIGRATION_27_28)

        db.query("SELECT title FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Keep me", c.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('note_versions', 'note_embeddings')").use { c ->
            assertFalse("the two retired tables must be gone", c.moveToFirst())
        }
    }

    @Test
    fun migrate28To29_addsTokenAndSecondsColumnsDefaultingToZero() {
        helper.createDatabase(TEST_DB, 28).apply {
            execSQL(
                "INSERT INTO chat_messages (id, conversationId, role, content, timestamp, tokens, replyToId) " +
                    "VALUES (1, 1, 'assistant', 'Answer', 1000, 42, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 29, true, MIGRATION_28_29)

        db.query("SELECT tokens, cachedTokens, answerSeconds FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(42, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }
    }

    @Test
    fun migrate29To30_dropsRetiredChatConversationsRosterAndKeepsMessages() {
        helper.createDatabase(TEST_DB, 29).apply {
            execSQL(
                "INSERT INTO chat_conversations (id, title, createdAt, updatedAt) " +
                    "VALUES (1, 'Conversation', 1000, 1000)"
            )
            execSQL(
                "INSERT INTO chat_messages (id, conversationId, role, content, timestamp, tokens, " +
                    "replyToId, cachedTokens, answerSeconds) " +
                    "VALUES (1, 1, 'user', 'Hello', 1000, 7, 0, 2, 3)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 30, true, MIGRATION_29_30)

        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'chat_conversations'").use { c ->
            assertFalse("the retired conversation roster must be gone", c.moveToFirst())
        }
        db.query("SELECT conversationId, content FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("Hello", c.getString(1))
        }
    }

    @Test
    fun migrate25To26_rebuildsNotesWithoutDropColumnAndKeepsData() {
        createLegacyDatabase(25).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, pinned, " +
                    "color, manualOrder, isDraft, hidden, bodySpans, isChecklist, checklist, isDoodle, doodle) " +
                    "VALUES (1, 'Kept', 'Body', 1000, '', '[]', 0, 0, '', 0, 0, 0, '', 0, '[]', 0, '')"
            )
            close()
        }

        val db = runLegacyMigrations(26, MIGRATION_25_26)

        val retired = listOf("isChecklist", "checklist", "isDoodle", "doodle")
        db.query("PRAGMA table_info(notes)").use { c ->
            val columns = mutableListOf<String>()
            while (c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name"))
            retired.forEach { assertFalse("$it must be gone from notes", it in columns) }
        }
        db.query("SELECT title, body FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Kept", c.getString(0))
            assertEquals("Body", c.getString(1))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'notes'").use { c ->
            val indexes = mutableListOf<String>()
            while (c.moveToNext()) indexes += c.getString(0)
            assertFalse("legacy isDraft index must be gone", "index_notes_isDraft" in indexes)
            assertFalse("legacy hidden index must be gone", "index_notes_hidden" in indexes)
            assertTrue("index_notes_updatedAt" in indexes)
            assertTrue("index_notes_archived" in indexes)
            assertTrue("index_notes_trashedAt" in indexes)
        }
    }

    @Test
    fun fullChain25To35_preservesNotesAndChatAndValidatesCurrentRoomSchema() {
        val noteColumns = "id, title, body, updatedAt, tags, attachments, archived, archivedAt, " +
            "pinned, color, trashedAt, manualOrder, isDraft, draftSavedAt, hidden, bodySpans, formatOverride"
        val chatColumns = "id, role, content, timestamp, attachmentMime, attachmentData, attachmentName, " +
            "attachmentList, conversationId, tokens, replyToId, reasoningBlocks, reasoningText, quotedRole, quotedText"
        val originalNotes: List<List<String?>>
        val originalChat: List<List<String?>>
        createLegacyDatabase(25).apply {
            assertEquals(25, version)
            execSQL(
                "INSERT INTO notes ($noteColumns, isChecklist, checklist, isDoodle, doodle) VALUES " +
                    "(41, 'Kept note', 'Rich body', 1234, 'tag', '[1]', 1, 1200, 1, 'ocean', " +
                    "1300, 7, 1, 1100, 1, 'spans', 'markdown', 1, '[2]', 1, 'old doodle')"
            )
            execSQL(
                "INSERT INTO notes ($noteColumns, isChecklist, checklist, isDoodle, doodle) VALUES " +
                    "(42, 'Nullable note', '', 2345, '', '[]', 0, NULL, 0, '', " +
                    "NULL, 0, 0, NULL, 0, '', NULL, 0, '[]', 0, '')"
            )
            execSQL(
                "INSERT INTO chat_messages ($chatColumns, agentTrace) VALUES " +
                    "(51, 'user', 'Question', 3456, NULL, NULL, NULL, NULL, 7001, 3, 0, " +
                    "NULL, NULL, NULL, NULL, NULL)"
            )
            execSQL(
                "INSERT INTO chat_messages ($chatColumns, agentTrace) VALUES " +
                    "(52, 'assistant', 'Answer', 4567, 'image/png', 'encoded', 'image.png', '[3]', " +
                    "7001, 19, 51, '[4]', 'Reasoning', 'user', 'Question', 'retired trace')"
            )
            originalNotes = readRows(this, "notes", noteColumns)
            originalChat = readRows(this, "chat_messages", chatColumns)
            assertEquals(2, originalNotes.size)
            assertEquals(2, originalChat.size)
            val legacyIndexes = readRows(
                this, "sqlite_master", "name", "WHERE type = 'index' AND name IN " +
                    "('index_notes_isDraft', 'index_notes_hidden', 'index_tasks_isDraft', 'index_tasks_hidden')"
            ).flatten().toSet()
            assertEquals(
                setOf("index_notes_isDraft", "index_notes_hidden", "index_tasks_isDraft", "index_tasks_hidden"),
                legacyIndexes
            )
            close()
        }
        runLegacyMigrations(26, MIGRATION_25_26).apply {
            assertEquals(26, version)
            assertEquals(originalNotes, readRows(this, "notes", noteColumns))
            assertEquals(originalChat, readRows(this, "chat_messages", chatColumns))
            assertRetiredColumnsAbsent(this, "notes", "isChecklist", "checklist", "isDoodle", "doodle")
            close()
        }
        legacyOpenHelpers.forEach { it.close() }
        legacyOpenHelpers.clear()
        helper.runMigrationsAndValidate(
            legacyDatabaseName, 35, true,
            MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30,
            MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35
        ).use { db ->
            assertEquals(35, db.version)
            assertEquals(originalNotes, readRows(db, "notes", noteColumns))
            assertEquals(originalChat, readRows(db, "chat_messages", chatColumns))
            assertRetiredColumnsAbsent(db, "notes", "isChecklist", "checklist", "isDoodle", "doodle")
            assertRetiredColumnsAbsent(db, "chat_messages", "agentTrace")
            db.query(
                "SELECT cachedTokens, answerSeconds, cachedTokensKnown, firstTokenMillis, " +
                    "generationMillis, sourceNoteId, sourceRevision FROM chat_messages ORDER BY id"
            ).use { cursor ->
                assertEquals(2, cursor.count)
                while (cursor.moveToNext()) {
                    assertEquals(0, cursor.getInt(0))
                    assertEquals(0, cursor.getInt(1))
                    assertEquals(0, cursor.getInt(2))
                    (3..6).forEach { assertTrue(cursor.isNull(it)) }
                }
            }
            val noteIndexes = readRows(
                db, "sqlite_master", "name", "WHERE type = 'index' AND tbl_name = 'notes'"
            ).flatten().toSet()
            assertEquals(
                setOf("index_notes_updatedAt", "index_notes_archived", "index_notes_trashedAt", "index_notes_title"),
                noteIndexes
            )
            assertEquals(
                listOf(listOf("index_chat_messages_conversationId_timestamp")),
                readRows(db, "sqlite_master", "name", "WHERE type = 'index' AND tbl_name = 'chat_messages'")
            )
            val schemaObjects = readRows(db, "sqlite_master", "name").flatten().filterNotNull()
            val retiredObjects = setOf(
                "index_notes_isDraft", "index_notes_hidden", "index_tasks_isDraft", "index_tasks_hidden",
                "tasks", "task_versions", "note_versions", "note_embeddings", "note_embeddings_cleanup",
                "chat_conversations", "notes_26", "chat_messages_27"
            )
            assertTrue("Retired objects and FTS shadow tables must be removed", schemaObjects.none {
                it in retiredObjects || it.startsWith("notes_fts") || it.startsWith("tasks_fts")
            })
        }
    }

    private fun readRows(
        db: SupportSQLiteDatabase,
        table: String,
        columns: String,
        where: String = ""
    ): List<List<String?>> = db.query("SELECT $columns FROM $table $where ORDER BY 1").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
            }
        }
    }

    private fun assertRetiredColumnsAbsent(db: SupportSQLiteDatabase, table: String, vararg retired: String) {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val columns = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            retired.forEach { assertFalse("$it must be gone from $table", it in columns) }
        }
    }

}