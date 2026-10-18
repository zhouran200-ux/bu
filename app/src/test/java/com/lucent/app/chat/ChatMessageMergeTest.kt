package com.lucent.app.chat

import com.lucent.app.data.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatMessageMergeTest {

    @Test
    fun keepsLegacyColumnsThatTheUiNoLongerReads() {
        val stored = ChatMessage(
            id = 7,
            conversationId = 3,
            role = "assistant",
            content = "old",
            timestamp = 100,
            attachmentMime = "image/png",
            attachmentData = "Zm9v",
            attachmentName = "a.png",
            attachmentList = "[1,2]",
            replyToId = 42,
            reasoningBlocks = "blocks",
            quotedRole = "user",
            quotedText = "quoted"
        )
        val merged = mergeChatMessage(stored, UiChatMessage(7, "assistant", "new", timestamp = 200), 3)
        assertEquals("new", merged.content)
        assertEquals(200, merged.timestamp)
        assertEquals("image/png", merged.attachmentMime)
        assertEquals("Zm9v", merged.attachmentData)
        assertEquals("a.png", merged.attachmentName)
        assertEquals("[1,2]", merged.attachmentList)
        assertEquals(42, merged.replyToId)
        assertEquals("blocks", merged.reasoningBlocks)
        assertEquals("user", merged.quotedRole)
        assertEquals("quoted", merged.quotedText)
    }

    @Test
    fun newMessagesStartWithoutLegacyPayload() {
        val merged = mergeChatMessage(null, UiChatMessage(9, "user", "hello", timestamp = 5), 3)
        assertEquals(9, merged.id)
        assertEquals(3, merged.conversationId)
        assertEquals("hello", merged.content)
        assertNull(merged.attachmentMime)
        assertEquals(0, merged.replyToId)
    }
}
