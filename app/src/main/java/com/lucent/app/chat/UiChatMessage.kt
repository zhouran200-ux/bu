package com.lucent.app.chat

data class UiChatMessage(
    val id: Long,
    val role: String,
    val content: String,
    val reasoningText: String? = null,
    val answerSeconds: Int = 0,
    val reasoningTag: String? = null,
    val promptTokens: Int = 0,
    val cachedTokens: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val firstTokenMillis: Long? = null,
    val generationMillis: Long? = null,
    val cachedTokensKnown: Boolean = false,
    val sourceNoteId: Long? = null,
    val sourceRevision: String? = null
)
