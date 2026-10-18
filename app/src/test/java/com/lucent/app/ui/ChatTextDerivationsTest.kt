package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTextDerivationsTest {
    @Test fun bodyWinsOverReasoningAndUsesExistingMarkdownPreviewRules() {
        assertEquals(
            ChatHistoryPreview.Excerpt("标题 正文"),
            chatHistoryPreview(ChatHistoryPreviewInput("# 标题\n- 正文", "hidden", false))
        )
    }

    @Test fun emptyStreamingBodyHidesReasoningUntilGenerationFinishes() {
        val input = ChatHistoryPreviewInput(" \n", "> 推理", true)
        assertEquals(ChatHistoryPreview.Streaming, chatHistoryPreview(input))
        assertEquals(ChatHistoryPreview.Excerpt("推理"), chatHistoryPreview(input.copy(streaming = false)))
        assertEquals(ChatHistoryPreview.NoBody, chatHistoryPreview(input.copy(reasoning = null, streaming = false)))
    }

    @Test fun excerptRetainsSeventyCharacterAndInputPrefixLimits() {
        assertEquals(
            ChatHistoryPreview.Excerpt("a".repeat(70) + "…"),
            chatHistoryPreview(ChatHistoryPreviewInput("a".repeat(2048), null, false))
        )
        assertEquals(
            ChatHistoryPreview.Excerpt(""),
            chatHistoryPreview(ChatHistoryPreviewInput(" ".repeat(1024) + "tail", null, false))
        )
    }
}
