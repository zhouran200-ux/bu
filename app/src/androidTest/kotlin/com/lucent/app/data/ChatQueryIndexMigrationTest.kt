package com.lucent.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatQueryIndexMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun indexMigrationPreservesRowsAndAvoidsTemporaryOrderSort() {
        val name = "chat-query-index-migration"
        helper.createDatabase(name, 32).apply {
            execSQL("INSERT INTO chat_messages (id, role, content, timestamp, conversationId, tokens, cachedTokens, answerSeconds, replyToId, cachedTokensKnown) VALUES (1, 'assistant', 'kept', 123, 100007, 0, 0, 0, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 33, true, MIGRATION_32_33).use { db ->
            db.query("SELECT content FROM chat_messages WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("kept", cursor.getString(0))
            }
            val details = mutableListOf<String>()
            db.query("EXPLAIN QUERY PLAN SELECT * FROM chat_messages WHERE conversationId = 100007 ORDER BY timestamp ASC").use { cursor ->
                while (cursor.moveToNext()) details += cursor.getString(cursor.getColumnIndexOrThrow("detail"))
            }
            assertTrue(details.any { it.contains("index_chat_messages_conversationId_timestamp") })
            assertFalse(details.any { it.contains("TEMP B-TREE", ignoreCase = true) })
        }
    }
}
