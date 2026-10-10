package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookIsolationTest {

    @Test
    fun testEveryNotebookGetsItsOwnConversation() {
        val ids = (1L..200L).map { ChatScope.forNotebook(it) }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it > ChatScope.NOTEBOOK_CONVERSATION_FLOOR })
    }

    @Test
    fun testNotebookConversationsNeverCollideWithTheLegacyChat() {
        val legacyConversation = ChatMessage(role = "user", content = "hi").conversationId
        assertTrue(ChatScope.forNotebook(1L) != legacyConversation)
        assertTrue(ChatScope.forNotebook(1L) != 0L)
    }

    @Test
    fun testNotebookConversationsKeepNotebookOrder() {
        assertTrue(ChatScope.forNotebook(7L) > ChatScope.forNotebook(6L))
    }
}
