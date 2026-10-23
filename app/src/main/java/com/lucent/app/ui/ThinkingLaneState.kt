package com.lucent.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal class ThinkingLaneState(saved: ReadingPosition?, streaming: Boolean) {
    var expanded by mutableStateOf(saved?.itemKey == "expanded")
        private set
    var retainLiveHeight by mutableStateOf(streaming && expanded)
        private set
    var measuredHeightPx by mutableStateOf(0)
    val scroll = ScrollState(saved?.offset ?: 0)
    private var persistEligible = saved != null || streaming

    fun retainStreamingHeight(streaming: Boolean) {
        if (streaming) persistEligible = true
        if (streaming && expanded) retainLiveHeight = true
    }

    fun toggle() {
        persistEligible = true
        expanded = !expanded
        if (!expanded) retainLiveHeight = false
    }

    fun readingPosition() = ReadingPosition(if (expanded) "expanded" else "collapsed", 0, scroll.value)

    fun positionToPersist(canExpand: Boolean): ReadingPosition? {
        if (canExpand) persistEligible = true
        return if (persistEligible) readingPosition() else null
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

    fun resetUserMessages() { expandedUsers.clear() }
}

@Composable
internal fun rememberThinkingLaneState(
    readingKey: String,
    streaming: Boolean,
    owner: NotebookMessageUiState? = null
): ThinkingLaneState {
    val context = LocalContext.current.applicationContext
    return remember(readingKey, owner) {
        val create = { ThinkingLaneState(ReadingPositionMemory.read(context, readingKey), streaming) }
        owner?.thinking(readingKey, create) ?: create()
    }
}
