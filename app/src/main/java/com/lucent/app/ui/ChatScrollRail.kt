package com.lucent.app.ui

import com.lucent.app.i18n.S

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingScrollCapsule(
    listState: LazyListState,
    itemKeys: List<String>,
    enabled: Boolean,
    onDragStart: () -> Unit = {},
    label: String = S.fastScroll,
    modifier: Modifier = Modifier
) {
    val ink = LocalOnGradient.current
    val latestDragStart by rememberUpdatedState(onDragStart)
    val totalCount by remember(listState) { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    val viewport by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            (layout.viewportSize.height - layout.beforeContentPadding - layout.afterContentPadding).coerceAtLeast(1)
        }
    }
    val spacing by remember(listState) { derivedStateOf { listState.layoutInfo.mainAxisItemSpacing } }
    val scrollable by remember(listState) {
        derivedStateOf { listState.canScrollBackward || listState.canScrollForward }
    }
    val measured = remember(listState) { mutableMapOf<Any, Int>() }
    var sizesVersion by remember(listState) { mutableIntStateOf(0) }
    var dragSession by remember(listState) { mutableStateOf<ChatRailDragSession?>(null) }
    val dragging = dragSession != null
    val scope = rememberCoroutineScope()
    var show by remember(listState) { mutableStateOf(false) }
    val sizes = remember(listState, itemKeys, totalCount, viewport, sizesVersion) {
        (0 until totalCount).map { index -> itemKeys.getOrNull(index)?.let { measured[it] } ?: viewport }
    }
    val geometry = remember(sizes, viewport, spacing) {
        ChatRailGeometry(sizes, viewport, spacing)
    }
    val position = geometry.fraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
        listState.canScrollBackward, listState.canScrollForward)
    val currentGeometry by rememberUpdatedState(geometry)
    val currentPosition by rememberUpdatedState(position)
    val currentEnabled by rememberUpdatedState(enabled)
    val shown by animateFloatAsState(if (enabled && (scrollable || dragging) && (show || dragging)) 1f else 0f,
        animationSpec = tween(140), label = "scrollCapsuleVisibility")

    LaunchedEffect(listState, itemKeys) {
        measured.keys.retainAll(itemKeys.toSet())
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key to it.size } }.collect { visible ->
            var changed = false
            visible.forEach { (key, size) ->
                if (measured[key] != size) {
                    measured[key] = size
                    changed = true
                }
            }
            if (changed) sizesVersion++
        }
    }
    LaunchedEffect(listState, listState.isScrollInProgress, dragging, enabled) {
        if (!enabled) {
            show = false
            dragSession = null
        } else if (listState.isScrollInProgress || dragging) show = true
        else {
            delay(650)
            show = false
        }
    }
    fun seek(fraction: Float, target: ChatScrollTarget = currentGeometry.target(fraction)) {
        when {
            fraction <= 0f -> listState.requestScrollToItem(0)
            fraction >= 1f -> listState.requestScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            else -> {
                listState.requestScrollToItem(target.index, target.offset)
            }
        }
    }
    if (!enabled || (!scrollable && !dragging)) return
    BoxWithConstraints(modifier = modifier.width(28.dp).fillMaxHeight()) {
        val density = LocalDensity.current
        val hitHeight = 52.dp.coerceAtMost(maxHeight)
        val usable = with(density) { (maxHeight - hitHeight).toPx().coerceAtLeast(1f) }
        val latestUsable by rememberUpdatedState(usable)
        val thumbTop = dragSession?.thumbTop(usable) ?: (position * usable)
        var coordinates by remember(listState) { mutableStateOf<LayoutCoordinates?>(null) }
        if (show || dragging || shown > 0.01f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbTop.roundToInt()) }
                    .size(width = 28.dp, height = hitHeight)
                    .onGloballyPositioned { coordinates = it }
                    .graphicsLayer { alpha = shown }
                    .semantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo(dragSession?.fraction ?: position, 0f..1f)
                        setProgress { value -> latestDragStart(); seek(value.coerceIn(0f, 1f)); true }
                    }
                    .pointerInput(listState, itemKeys, enabled) {
                        fun rootY(point: Offset): Float? =
                            coordinates?.takeIf { it.isAttached }?.localToRoot(point)?.y
                        detectChatRailDrag(
                            onPress = { point ->
                                if (!currentEnabled || rootY(point) == null) {
                                    false
                                } else {
                                    val layout = listState.layoutInfo
                                    val visible = layout.visibleItemsInfo.associate { it.key to it.size }
                                    val readable = (layout.viewportSize.height - layout.beforeContentPadding -
                                        layout.afterContentPadding).coerceAtLeast(1)
                                    val snapshot = ChatRailGeometry(
                                        (0 until layout.totalItemsCount).map { index ->
                                            itemKeys.getOrNull(index)?.let { visible[it] ?: measured[it] } ?: readable
                                        },
                                        readable,
                                        layout.mainAxisItemSpacing
                                    )
                                    dragSession = ChatRailDragSession(
                                        geometry = snapshot,
                                        startFraction = currentPosition,
                                        anchor = ChatScrollTarget(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset),
                                        usable = latestUsable
                                    )
                                    latestDragStart()
                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        listState.stopScroll(MutatePriority.UserInput)
                                    }
                                    true
                                }
                            },
                            onDragStart = { point ->
                                rootY(point)?.let { dragSession = dragSession?.start(it) }
                            },
                            onDrag = { point ->
                                val previous = dragSession
                                val y = rootY(point)
                                if (currentEnabled && previous != null && y != null) {
                                    val next = previous.move(y)
                                    if (next.fraction != previous.fraction) {
                                        dragSession = next
                                        seek(next.fraction, next.target())
                                    }
                                }
                            },
                            onRelease = { dragSession = null }
                        )
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    Modifier
                        .padding(end = 3.dp)
                        .size(width = 22.dp, height = 46.dp.coerceAtMost(hitHeight))
                        .foregroundGlass(cornerRadius = 23.dp, reading = true, borderWidth = 0.dp)
                        .border(
                            width = 1.2.dp,
                            color = ink.copy(alpha = if (dragging) 0.88f else 0.55f),
                            shape = RoundedCornerShape(50)
                        )
                )
            }
        }
    }
}
