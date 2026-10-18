package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class ChatHistoryPreviewInput(
    val content: String,
    val reasoning: String?,
    val streaming: Boolean
)

internal sealed interface ChatHistoryPreview {
    data class Excerpt(val text: String) : ChatHistoryPreview
    data object Streaming : ChatHistoryPreview
    data object NoBody : ChatHistoryPreview
}

internal fun chatHistoryPreview(input: ChatHistoryPreviewInput): ChatHistoryPreview = when {
    input.streaming && input.content.isBlank() -> ChatHistoryPreview.Streaming
    input.content.isNotBlank() -> ChatHistoryPreview.Excerpt(previewExcerpt(input.content.take(1024), limit = 70))
    !input.reasoning.isNullOrBlank() -> ChatHistoryPreview.Excerpt(previewExcerpt(input.reasoning.take(1024), limit = 70))
    else -> ChatHistoryPreview.NoBody
}

@Composable
internal fun rememberChatHistoryPreview(input: ChatHistoryPreviewInput): State<ChatHistoryPreview?> {
    val throttle = remember { ChatPreviewThrottle() }
    return key(if (input.streaming) Unit else input) {
        produceState<ChatHistoryPreview?>(initialValue = null, input) {
            if (input.streaming) delay(throttle.remainingMillis())
            throttle.started()
            value = withContext(Dispatchers.Default) { chatHistoryPreview(input) }
        }
    }
}

private class ChatPreviewThrottle {
    private var lastStartedNanos: Long? = null

    fun remainingMillis(): Long = lastStartedNanos?.let { previous ->
        (100L - (System.nanoTime() - previous) / 1_000_000L).coerceAtLeast(0L)
    } ?: 0L

    fun started() { lastStartedNanos = System.nanoTime() }
}

@Composable
internal fun rememberChatBodyCharCount(content: String): State<Int?> =
    key(content) {
        produceState<Int?>(initialValue = null, content) {
            value = withContext(Dispatchers.Default) { ChatStats.bodyCharCount(content) }
        }
    }
