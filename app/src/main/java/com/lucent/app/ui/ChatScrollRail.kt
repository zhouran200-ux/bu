package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FloatingScrollCapsule(
    listState: LazyListState,
    itemKeys: List<String>,
    enabled: Boolean,
    onDragStart: () -> Unit = {},
    label: String = "快速滚动",
    modifier: Modifier = Modifier
) {
    val ink = LocalOnGradient.current
    val latestDragStart by rememberUpdatedState(onDragStart)
    val measured = remember(listState) { mutableStateMapOf<Any, Int>() }
    var dragging by remember(listState) { mutableStateOf(false) }
    var heldFraction by remember(listState) { mutableFloatStateOf(0f) }
    var show by remember(listState) { mutableStateOf(false) }
    val info = listState.layoutInfo
    val scrollable = listState.canScrollBackward || listState.canScrollForward
    val viewport = info.viewportSize.height.coerceAtLeast(1)
    val sizes = (0 until info.totalItemsCount).map { index -> itemKeys.getOrNull(index)?.let { measured[it] } ?: viewport }
    val geometry = remember(sizes, viewport, info.mainAxisItemSpacing) {
        ChatRailGeometry(sizes, viewport, info.mainAxisItemSpacing)
    }
    val position = geometry.fraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
        listState.canScrollBackward, listState.canScrollForward)
    val currentGeometry by rememberUpdatedState(geometry)
    val currentPosition by rememberUpdatedState(position)
    val currentEnabled by rememberUpdatedState(enabled)
    val shown by animateFloatAsState(if (enabled && scrollable && (show || dragging)) 1f else 0f,
        animationSpec = tween(140), label = "scrollCapsuleVisibility")

    LaunchedEffect(itemKeys) {
        measured.keys.retainAll(itemKeys.toSet())
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key to it.size } }.collect { visible ->
            if (!dragging) visible.forEach { measured[it.first] = it.second }
        }
    }
    LaunchedEffect(listState.isScrollInProgress, dragging, enabled) {
        if (!enabled) {
            show = false
            dragging = false
        } else if (listState.isScrollInProgress || dragging) show = true
        else {
            delay(650)
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
    BoxWithConstraints(modifier = modifier.width(28.dp).fillMaxHeight()) {
        val density = LocalDensity.current
        val hitHeight = 52.dp.coerceAtMost(maxHeight)
        val usable = with(density) { (maxHeight - hitHeight).toPx().coerceAtLeast(1f) }
        val latestUsable by rememberUpdatedState(usable)
        val fraction = if (dragging) heldFraction else position
        var coordinates by remember(listState) { mutableStateOf<LayoutCoordinates?>(null) }
        if (show || dragging || shown > 0.01f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (fraction * usable).roundToInt()) }
                    .size(width = 28.dp, height = hitHeight)
                    .onGloballyPositioned { coordinates = it }
                    .graphicsLayer { alpha = shown }
                    .semantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..1f)
                        setProgress { value -> latestDragStart(); seek(value.coerceIn(0f, 1f)); true }
                    }
                    .pointerInput(listState) {
                        var start = 0f
                        var originY = 0f
                        var frozenUsable = 1f
                        var frozen: ChatRailGeometry? = null
                        fun rootY(point: Offset): Float? = coordinates?.takeIf { it.isAttached }?.localToRoot(point)?.y
                        detectVerticalDragGestures(
                            onDragStart = { point ->
                                start = currentPosition
                                heldFraction = start
                                originY = rootY(point) ?: 0f
                                frozenUsable = latestUsable
                                frozen = currentGeometry
                                dragging = true
                                latestDragStart()
                            },
                            onDragEnd = { dragging = false; frozen = null },
                            onDragCancel = { dragging = false; frozen = null },
                            onVerticalDrag = { change, _ ->
                                if (currentEnabled) {
                                    change.consume()
                                    rootY(change.position)?.let { y ->
                                        heldFraction = railDragFraction(start, y - originY, frozenUsable)
                                        frozen?.let { seek(heldFraction, it) }
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(Modifier.padding(end = 3.dp)
                    .size(width = 22.dp, height = 46.dp.coerceAtMost(hitHeight))
                    .border(2.dp, ink.copy(alpha = if (dragging) 0.98f else 0.85f), RoundedCornerShape(50)))
            }
        }
    }
}
