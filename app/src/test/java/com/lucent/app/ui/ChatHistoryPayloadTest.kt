package com.lucent.app.ui

import com.lucent.app.chat.UiChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryPayloadTest {

    private fun message(id: Long, role: String, content: String) =
        UiChatMessage(id, role, content, timestamp = id)

    @Test
    fun stopsBeforeTheAnswerSlot() {
        val history = buildChatHistory(
            listOf(
                message(1, "user", "first"),
                message(2, "assistant", "reply"),
                message(3, "user", "second"),
                message(4, "assistant", "")
            ),
            4,
            5
        )
        assertEquals(listOf("first", "reply", "second"), history.map { it.content })
        assertEquals(listOf("user", "assistant", "user"), history.map { it.role })
    }

    @Test
    fun blankAssistantRowsNeverReachTheModel() {
        val history = buildChatHistory(
            listOf(
                message(1, "user", "hello"),
                message(2, "assistant", ""),
                message(3, "assistant", "   "),
                message(4, "assistant", "answer")
            ),
            9,
            5
        )
        assertEquals(listOf("hello", "answer"), history.map { it.content })
    }

    @Test
    fun keepsOnlyTheRequestedRounds() {
        val messages = (1L..9L).map { index ->
            message(index, if (index % 2 == 0L) "assistant" else "user", "m$index")
        }
        val history = buildChatHistory(messages, 99, rounds = 2)
        assertEquals(listOf("m6", "m7", "m8", "m9"), history.map { it.content })
    }
}
