package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ChatScrollRail(
    listState: LazyListState,
    itemKeys: List<String>,
    enabled: Boolean,
    onDragStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = LocalOnGradient.current
    val latestDragStart by rememberUpdatedState(onDragStart)
    val measured = remember(listState) { mutableStateMapOf<Any, Int>() }
    var dragging by remember { mutableStateOf(false) }
    var heldFraction by remember { mutableFloatStateOf(0f) }
    var show by remember { mutableStateOf(false) }
    val info = listState.layoutInfo
    val scrollable = listState.canScrollBackward || listState.canScrollForward
    val defaultHeight = info.viewportSize.height.coerceAtLeast(1)
    val sizes = (0 until info.totalItemsCount).map { index -> itemKeys.getOrNull(index)?.let { measured[it] } ?: defaultHeight }
    val geometry = remember(sizes, defaultHeight, info.mainAxisItemSpacing) {
        ChatRailGeometry(sizes, defaultHeight, info.mainAxisItemSpacing)
    }
    val position = geometry.fraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
        listState.canScrollBackward, listState.canScrollForward)
    val currentGeometry by rememberUpdatedState(geometry)
    val currentPosition by rememberUpdatedState(position)
    val thumbPx = with(LocalDensity.current) { 40.dp.toPx() }
    val insetPx = with(LocalDensity.current) { 8.dp.toPx() }
    val railWidth = with(LocalDensity.current) { 3.dp.toPx() }
    val shown by animateFloatAsState(if (enabled && scrollable && (show || dragging)) 1f else 0f,
        animationSpec = tween(150), label = "chatRailVisibility")

    LaunchedEffect(itemKeys) {
        measured.keys.retainAll(itemKeys.toSet())
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { Triple(it.index, it.key, it.size) } }.collect { visible ->
            if (!dragging) visible.forEach { measured[it.second] = it.third }
        }
    }
    LaunchedEffect(listState.isScrollInProgress, dragging, enabled) {
        if (enabled && (listState.isScrollInProgress || dragging)) show = true
        else {
            delay(1200)
            show = false
        }
    }
    fun seek(fraction: Float, snapshot: ChatRailGeometry = currentGeometry) {
        when {
            fraction <= 0f -> listState.requestScrollToItem(0)
            fraction >= 1f -> listState.requestScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            else -> {
                val target = snapshot.target(fraction)
                listState.requestScrollToItem(target.index, target.offset)
            }
        }
    }
    if (!enabled || !scrollable) return
    Box(
        modifier = modifier.width(28.dp).fillMaxHeight().padding(vertical = 8.dp)
            .semantics {
                contentDescription = "对话快速滚动"
                progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..1f)
                setProgress { value -> latestDragStart(); seek(value.coerceIn(0f, 1f)); true }
            }
            .pointerInput(listState) {
                var start = 0f
                var travel = 0f
                var frozen: ChatRailGeometry? = null
                detectVerticalDragGestures(
                    onDragStart = {
                        start = currentPosition
                        heldFraction = start
                        travel = 0f
                        frozen = currentGeometry
                        dragging = true
                        latestDragStart()
                    },
                    onDragEnd = { dragging = false; frozen = null },
                    onDragCancel = { dragging = false; frozen = null },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        travel += amount
                        heldFraction = railDragFraction(start, travel, size.height - thumbPx - insetPx * 2f)
                        frozen?.let { seek(heldFraction, it) }
                    }
                )
            }
    ) {
        Box(Modifier.width(28.dp).fillMaxHeight().graphicsLayer { alpha = shown }.drawBehind {
            val thumbHeight = thumbPx.coerceAtMost((size.height - insetPx * 2f).coerceAtLeast(0f))
            val fraction = if (dragging) heldFraction else position
            val top = insetPx + fraction * (size.height - insetPx * 2f - thumbHeight).coerceAtLeast(0f)
            drawRoundRect(ink.copy(alpha = if (dragging) 0.65f else 0.35f),
                topLeft = Offset(size.width - railWidth - 3.dp.toPx(), top),
                size = Size(railWidth, thumbHeight), cornerRadius = CornerRadius(railWidth))
        })
    }
}
