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
class ReplyStatsMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate30To31PreservesMessagesAndPersistsReplyStats() {
        val databaseName = "reply-stats-migration-test"
        helper.createDatabase(databaseName, 30).apply {
            execSQL(
                "INSERT INTO chat_messages " +
                    "(id, role, content, timestamp, conversationId, tokens, cachedTokens, answerSeconds, replyToId) " +
                    "VALUES (1, 'assistant', 'Original reply', 1700000000123, 7, 128, 32, 4, 9)"
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 31, true, MIGRATION_30_31).apply {
            query("SELECT * FROM chat_messages WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("assistant", cursor.getString(cursor.getColumnIndexOrThrow("role")))
                assertEquals("Original reply", cursor.getString(cursor.getColumnIndexOrThrow("content")))
                assertEquals(1700000000123L, cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))
                assertEquals(7L, cursor.getLong(cursor.getColumnIndexOrThrow("conversationId")))
                assertEquals(128, cursor.getInt(cursor.getColumnIndexOrThrow("tokens")))
                assertEquals(32, cursor.getInt(cursor.getColumnIndexOrThrow("cachedTokens")))
                assertEquals(4, cursor.getInt(cursor.getColumnIndexOrThrow("answerSeconds")))
                assertEquals(9L, cursor.getLong(cursor.getColumnIndexOrThrow("replyToId")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("firstTokenMillis")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("generationMillis")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("cachedTokensKnown")))
            }
            execSQL(
                "INSERT INTO chat_messages " +
                    "(id, role, content, timestamp, conversationId, tokens, cachedTokens, answerSeconds, " +
                    "replyToId, firstTokenMillis, generationMillis, cachedTokensKnown) " +
                    "VALUES (2, 'assistant', 'New reply', 1700000000456, 7, 256, 64, 6, 10, 125, 5678, 1)"
            )
            execSQL(
                "INSERT INTO chat_messages " +
                    "(id, role, content, timestamp, conversationId, tokens, cachedTokens, answerSeconds, replyToId) " +
                    "VALUES (3, 'assistant', 'Default stats', 1700000000789, 7, 0, 0, 0, 0)"
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 31, true, MIGRATION_30_31).apply {
            query("SELECT * FROM chat_messages WHERE id = 2").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("New reply", cursor.getString(cursor.getColumnIndexOrThrow("content")))
                assertEquals(1700000000456L, cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))
                assertEquals(256, cursor.getInt(cursor.getColumnIndexOrThrow("tokens")))
                assertEquals(64, cursor.getInt(cursor.getColumnIndexOrThrow("cachedTokens")))
                assertEquals(6, cursor.getInt(cursor.getColumnIndexOrThrow("answerSeconds")))
                assertEquals(125L, cursor.getLong(cursor.getColumnIndexOrThrow("firstTokenMillis")))
                assertEquals(5678L, cursor.getLong(cursor.getColumnIndexOrThrow("generationMillis")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("cachedTokensKnown")))
            }
            query("SELECT * FROM chat_messages WHERE id = 3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("firstTokenMillis")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("generationMillis")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("cachedTokensKnown")))
            }
            close()
        }
    }
}
