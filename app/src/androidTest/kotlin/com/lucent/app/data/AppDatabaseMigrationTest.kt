package com.lucent.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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

    private val fixture = LegacyMigrationFixture()
    private val legacyDatabaseName get() = fixture.databaseName

    @After
    fun closeLegacyDatabases() {
        fixture.closeAll()
    }

    @Test
    fun migrate19To20_addsChatMessagesAgentTraceColumn() {
        fixture.createLegacyDatabase(19).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, timestamp, attachmentMime, " +
                    "attachmentData, attachmentName, attachmentList, conversationId, tokens, replyToId) " +
                    "VALUES (1, 'assistant', 'hi', 1000, NULL, NULL, NULL, NULL, 1, 0, 0)"
            )
            close()
        }

        val db = fixture.runLegacyMigrations(20, MIGRATION_19_20)

        db.query("SELECT agentTrace FROM chat_messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }

    @Test
    fun migrate20To21_addsFormatOverrideColumnsToNotesAndTasks() {
        fixture.createLegacyDatabase(20).apply {
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

        val db = fixture.runLegacyMigrations(21, MIGRATION_20_21)

        db.query("SELECT formatOverride FROM notes WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
        db.query("SELECT formatOverride FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }

    @Test
    fun migrate21To22_addsCoverOrderAndTrashToNotebooks() {
        fixture.createLegacyDatabase(21).apply {
            execSQL(
                "INSERT INTO notebooks (id, title, createdAt, updatedAt) VALUES (1, 'Ideas', 1000, 1000)"
            )
            close()
        }

        val db = fixture.runLegacyMigrations(22, MIGRATION_21_22)

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
        fixture.createLegacyDatabase(22).apply {
            execSQL(
                "INSERT INTO notebooks (id, title, createdAt, updatedAt, color, manualOrder, trashedAt) " +
                    "VALUES (1, 'Trips', 1000, 1000, 'ocean', 0, NULL)"
            )
            close()
        }

        val db = fixture.runLegacyMigrations(23, MIGRATION_22_23)

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
        fixture.createLegacyDatabase(25).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, pinned, " +
                    "color, manualOrder, isDraft, hidden, bodySpans, isChecklist, checklist, isDoodle, doodle) " +
                    "VALUES (1, 'Kept', 'Body', 1000, '', '[]', 0, 0, '', 0, 0, 0, '', 0, '[]', 0, '')"
            )
            close()
        }

        val db = fixture.runLegacyMigrations(26, MIGRATION_25_26)

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
    fun fullChain25To37_preservesNotesAndChatAndValidatesCurrentRoomSchema() {
        val noteColumns = "id, title, body, updatedAt, tags, attachments, archived, archivedAt, " +
            "pinned, color, trashedAt, manualOrder, isDraft, draftSavedAt, hidden, bodySpans, formatOverride"
        val chatColumns = "id, role, content, timestamp, attachmentMime, attachmentData, attachmentName, " +
            "attachmentList, conversationId, tokens, replyToId, reasoningBlocks, reasoningText, quotedRole, quotedText"
        val originalNotes: List<List<String?>>
        val originalChat: List<List<String?>>
        fixture.createLegacyDatabase(25).apply {
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
        fixture.runLegacyMigrations(26, MIGRATION_25_26).apply {
            assertEquals(26, version)
            assertEquals(originalNotes, readRows(this, "notes", noteColumns))
            assertEquals(originalChat, readRows(this, "chat_messages", chatColumns))
            assertRetiredColumnsAbsent(this, "notes", "isChecklist", "checklist", "isDoodle", "doodle")
            close()
        }
        fixture.closeOpenHelpers()
        helper.runMigrationsAndValidate(
            legacyDatabaseName, 37, true,
            MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30,
            MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37
        ).use { db ->
            assertEquals(37, db.version)
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
            val notebookIndexes = readRows(
                db, "sqlite_master", "name", "WHERE type = 'index' AND tbl_name = 'notebooks'"
            ).flatten().toSet()
            assertEquals(
                setOf("index_notebooks_updatedAt", "index_notebooks_manualOrder_updatedAt"),
                notebookIndexes
            )
            val itemIndexes = readRows(
                db, "sqlite_master", "name", "WHERE type = 'index' AND tbl_name = 'notebook_items'"
            ).flatten().toSet()
            assertEquals(
                setOf("index_notebook_items_notebookId", "index_notebook_items_itemKind_itemId", "index_notebook_items_notebookId_addedAt"),
                itemIndexes
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
