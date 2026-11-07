package com.lucent.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal class ThinkingLaneState {
    var expanded by mutableStateOf(false)
        private set
    var retainLiveHeight by mutableStateOf(false)
        private set
    var measuredHeightPx by mutableStateOf(0)
    val scroll = ScrollState(0)

    fun retainStreamingHeight(streaming: Boolean) {
        if (streaming && expanded) retainLiveHeight = true
    }

    fun toggle() {
        expanded = !expanded
        if (!expanded) retainLiveHeight = false
    }

}

internal val LocalNotebookMessageUiState = compositionLocalOf<NotebookMessageUiState?> { null }

internal class NotebookMessageUiState {
    private val thinking = mutableMapOf<String, ThinkingLaneState>()
    private val expandedUsers = mutableStateMapOf<Long, Boolean>()

    fun thinking(key: String, create: () -> ThinkingLaneState): ThinkingLaneState =
        thinking.getOrPut(key, create)

    fun userExpanded(id: Long): Boolean = expandedUsers[id] == true

    fun setUserExpanded(id: Long, expanded: Boolean) {
        if (expanded) expandedUsers[id] = true else expandedUsers.remove(id)
    }
}

@Composable
internal fun rememberThinkingLaneState(
    readingKey: String,
    streaming: Boolean,
    owner: NotebookMessageUiState? = null
): ThinkingLaneState {
    return remember(readingKey, owner) {
        val create = { ThinkingLaneState().apply { retainStreamingHeight(streaming) } }
        owner?.thinking(readingKey, create) ?: create()
    }
}
