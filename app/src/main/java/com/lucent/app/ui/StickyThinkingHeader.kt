package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lucent.app.chat.UiChatMessage

internal data class ThinkingTopItem(val key: String, val index: Int)

internal fun thinkingTopItem(key: Any, index: Int, offset: Int, size: Int, viewportTop: Int): ThinkingTopItem? {
    if (offset > viewportTop || offset.toLong() + size <= viewportTop || key !is String) return null
    return ThinkingTopItem(key, index)
}

internal fun thinkingRemainingHeight(itemOffset: Int, thinkingHeight: Int, viewportTop: Int): Int =
    if (thinkingHeight <= 0) 0 else
        (itemOffset.toLong() + thinkingHeight - viewportTop).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

@Composable
internal fun StickyThinkingHeader(
    listState: LazyListState,
    messages: List<UiChatMessage>,
    notebookId: Long,
    owner: NotebookMessageUiState,
    streamingAnswerId: Long?,
    liveThought: State<String>,
    active: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topItem by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.firstNotNullOfOrNull { item ->
                thinkingTopItem(item.key, item.index, item.offset, item.size, layout.viewportStartOffset)
            }
        }
    }
    if (!active) return
    val top = topItem ?: return
    val message = messages.getOrNull(top.index - 1)?.takeIf { top.key == "message:${it.id}" } ?: return
    if (message.role != "assistant") return
    val streaming = message.id == streamingAnswerId
    val lane = rememberThinkingLaneState("chat:$notebookId:thought:${message.id}", streaming, owner)
    val hasThought = if (streaming) liveThought.value.isNotBlank() else !message.reasoningText.isNullOrBlank()
    if (!lane.expanded || (!streaming && !hasThought)) return
    val layout = listState.layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.key == top.key } ?: return
    val thinkingRemaining = thinkingRemainingHeight(item.offset, lane.measuredHeightPx, layout.viewportStartOffset)
    if (thinkingRemaining <= 0) return
    SubcomposeLayout(modifier = modifier.fillMaxWidth().clipToBounds()) { constraints ->
        val header = subcompose(message.id) {
            Box(Modifier.fillMaxWidth().padding(start = 14.dp, end = 26.dp)) {
                Surface(color = glassPanelColor().copy(alpha = 0.22f), tonalElevation = 0.dp, shadowElevation = 0.dp) {
                    ThinkingHeader(
                        title = thinkingTitle(streaming, hasThought, message.answerSeconds),
                        expanded = true,
                        canExpand = true,
                        onToggle = { onToggle(); lane.toggle() },
                        modifier = Modifier.fillMaxWidth().testTag("sticky_thinking_header")
                    )
                }
            }
        }.single().measure(constraints.copy(minHeight = 0))
        layout(constraints.maxWidth, header.height) {
            // Overlay only: hiding never changes list padding, item sizes, or scroll position.
            header.placeRelative(0, (thinkingRemaining - header.height).coerceAtMost(0))
        }
    }
}
