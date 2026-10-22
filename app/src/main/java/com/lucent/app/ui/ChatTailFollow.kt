package com.lucent.app.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.conflate

internal data class ChatTailLayout(
    val itemCount: Int,
    val lastVisibleIndex: Int,
    val lastVisibleEnd: Int,
    val viewportEnd: Int,
    val bottomPadding: Int,
    val viewportHeight: Int,
    val canScrollForward: Boolean
) {
    fun forwardDistance(): Float {
        if (!canScrollForward || itemCount <= 0 || viewportHeight <= 0) return 0f
        return if (lastVisibleIndex == itemCount - 1) {
            (lastVisibleEnd.toLong() + bottomPadding - viewportEnd).coerceAtLeast(0).toFloat()
        } else {
            (viewportHeight - bottomPadding).coerceAtLeast(1).toFloat()
        }
    }
}

internal fun LazyListState.chatTailLayout(): ChatTailLayout {
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull()
    return ChatTailLayout(
        itemCount = layout.totalItemsCount,
        lastVisibleIndex = last?.index ?: -1,
        lastVisibleEnd = last?.let { it.offset + it.size } ?: 0,
        viewportEnd = layout.viewportEndOffset,
        bottomPadding = layout.afterContentPadding,
        viewportHeight = layout.viewportSize.height,
        canScrollForward = canScrollForward
    )
}

@Composable
internal fun FollowChatTail(
    listState: LazyListState,
    active: Boolean,
    following: Boolean,
    canFollow: () -> Boolean
) {
    val latestCanFollow by rememberUpdatedState(canFollow)
    LaunchedEffect(listState, active, following) {
        if (!active || !following) return@LaunchedEffect
        snapshotFlow { listState.chatTailLayout() to listState.isScrollInProgress }
            .conflate().collect { (layout, scrolling) ->
                if (!scrolling && layout.forwardDistance() > 0f && latestCanFollow()) {
                    withFrameNanos { }
                    if (!listState.isScrollInProgress && latestCanFollow()) {
                        val distance = listState.chatTailLayout().forwardDistance()
                        if (distance > 0f) listState.scrollBy(distance)
                    }
                }
            }
    }
}
