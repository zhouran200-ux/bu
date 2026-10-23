package com.lucent.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.lucent.app.chat.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class InitialChatMarkdown(val text: String, val snapshot: MarkdownParseSnapshot)

@Composable
internal fun rememberInitialChatMarkdown(session: ChatSession): State<Map<Long, InitialChatMarkdown>?> {
    val ink = LocalOnGradient.current
    val muted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    return produceState<Map<Long, InitialChatMarkdown>?>(null, session) {
        val messages = session.messages.filter { it.role == "assistant" && it.content.isNotBlank() && it.id != session.streamingAnswerId }
        value = withContext(Dispatchers.Default) {
            messages.associate { message ->
                val style = MarkdownStyle(ink, muted, accent, true, false, message.sourceNoteId, message.sourceRevision, true)
                message.id to InitialChatMarkdown(message.content, MarkdownRenderer().render(message.content, style, null))
            }
        }
    }
}
