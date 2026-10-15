package com.lucent.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplySourceMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate31To32PreservesMessagesAndPersistsSourceIdentity() {
        val databaseName = "reply-source-migration-test"
        helper.createDatabase(databaseName, 31).apply {
            execSQL(
                "INSERT INTO chat_messages " +
                    "(id, role, content, timestamp, conversationId, tokens, cachedTokens, answerSeconds, " +
                    "replyToId, reasoningText, firstTokenMillis, generationMillis, cachedTokensKnown) " +
                    "VALUES (1, 'assistant', 'Original reply', 1700000000123, 100007, 128, 32, 4, " +
                    "9, 'Original reasoning', 125, 4567, 1)"
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 32, true, MIGRATION_31_32).apply {
            query("SELECT * FROM chat_messages WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("assistant", cursor.getString(cursor.getColumnIndexOrThrow("role")))
                assertEquals("Original reply", cursor.getString(cursor.getColumnIndexOrThrow("content")))
                assertEquals("Original reasoning", cursor.getString(cursor.getColumnIndexOrThrow("reasoningText")))
                assertEquals(1700000000123L, cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))
                assertEquals(100007L, cursor.getLong(cursor.getColumnIndexOrThrow("conversationId")))
                assertEquals(128, cursor.getInt(cursor.getColumnIndexOrThrow("tokens")))
                assertEquals(32, cursor.getInt(cursor.getColumnIndexOrThrow("cachedTokens")))
                assertEquals(4, cursor.getInt(cursor.getColumnIndexOrThrow("answerSeconds")))
                assertEquals(9L, cursor.getLong(cursor.getColumnIndexOrThrow("replyToId")))
                assertEquals(125L, cursor.getLong(cursor.getColumnIndexOrThrow("firstTokenMillis")))
                assertEquals(4567L, cursor.getLong(cursor.getColumnIndexOrThrow("generationMillis")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("cachedTokensKnown")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("sourceNoteId")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("sourceRevision")))
            }
            execSQL(
                "INSERT INTO chat_messages " +
                    "(id, role, content, timestamp, conversationId, tokens, cachedTokens, answerSeconds, " +
                    "replyToId, sourceNoteId, sourceRevision) " +
                    "VALUES (2, 'assistant', 'New reply', 1700000000456, 100007, 0, 0, 0, " +
                    "0, 4294967297, 'revision-before-edit')"
            )
            execSQL(
                "UPDATE chat_messages SET sourceRevision = ? WHERE id = ?",
                arrayOf<Any>("revision-after-edit", 2L)
            )
            execSQL(
                "INSERT INTO chat_messages " +
                    "(id, role, content, timestamp, conversationId, tokens, cachedTokens, answerSeconds, replyToId) " +
                    "VALUES (3, 'assistant', 'Default source', 1700000000789, 100007, 0, 0, 0, 0)"
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 32, true, MIGRATION_31_32).apply {
            query("SELECT * FROM chat_messages WHERE id = 2").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("New reply", cursor.getString(cursor.getColumnIndexOrThrow("content")))
                assertEquals(4294967297L, cursor.getLong(cursor.getColumnIndexOrThrow("sourceNoteId")))
                assertEquals("revision-after-edit", cursor.getString(cursor.getColumnIndexOrThrow("sourceRevision")))
            }
            query("SELECT * FROM chat_messages WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("sourceNoteId")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("sourceRevision")))
            }
            close()
        }
    }
}
