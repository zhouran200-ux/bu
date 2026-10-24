package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
internal fun DocumentScrollCapsule(
    listState: LazyListState,
    itemKeys: List<String>,
    chapterIndices: List<Int>,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    val ink = LocalOnGradient.current
    val model = remember(itemKeys, chapterIndices) { DocumentRailPosition(itemKeys.size, chapterIndices) }
    var session by remember(listState, model) { mutableStateOf<DocumentRailDragSession?>(null) }
    var pending by remember(listState, model) { mutableStateOf<Float?>(null) }
    var show by remember(listState) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val layout = listState.layoutInfo
    val first = layout.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }
    val position = when {
        !listState.canScrollBackward -> 0f
        !listState.canScrollForward -> 1f
        else -> model.fraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
            first?.size ?: 1, layout.mainAxisItemSpacing)
    }
    val displayed = session?.fraction ?: pending ?: position
    val currentDisplayed by rememberUpdatedState(displayed)
    val currentEnabled by rememberUpdatedState(enabled)
    val scrollable = listState.canScrollBackward || listState.canScrollForward
    val dragging = session != null
    val shown by animateFloatAsState(if (enabled && (show || dragging)) 1f else 0f,
        animationSpec = tween(140), label = "documentCapsuleVisibility")

    LaunchedEffect(listState, model, pending, enabled) {
        val requested = pending ?: return@LaunchedEffect
        if (!enabled || listState.layoutInfo.totalItemsCount != model.itemCount) {
            pending = null
            return@LaunchedEffect
        }
        val target = model.target(requested)
        val needsOffset = requested > 0f && requested < 1f && target.withinItem > 0f
        var info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target.index }
        if (!needsOffset || info == null) {
            listState.scrollToItem(target.index)
            info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target.index }
        }
        if (needsOffset) {
            if (info != null) {
                val offset = ((info.size.toDouble() + listState.layoutInfo.mainAxisItemSpacing) *
                    target.withinItem).coerceIn(0.0, Int.MAX_VALUE.toDouble()).roundToInt()
                listState.scrollToItem(target.index, offset)
            }
        }
        pending = null
    }
    LaunchedEffect(listState.isScrollInProgress, dragging, enabled) {
        if (!enabled) {
            session = null
            pending = null
            show = false
        } else if (listState.isScrollInProgress || dragging) {
            show = true
        } else {
            delay(650)
            show = false
        }
    }
    if (!enabled || (!scrollable && !dragging)) return
    BoxWithConstraints(modifier = modifier.width(28.dp).fillMaxHeight()) {
        val hitHeight = 52.dp.coerceAtMost(maxHeight)
        val density = LocalDensity.current
        val hitPixels = with(density) { hitHeight.toPx() }
        val usable = with(density) { (maxHeight - hitHeight).toPx().coerceAtLeast(1f) }
        val currentUsable by rememberUpdatedState(usable)
        val currentHitPixels by rememberUpdatedState(hitPixels)
        val visible by rememberUpdatedState(show || dragging)
        if (show || dragging || shown > 0.01f) {
            Box(Modifier.width(28.dp).fillMaxHeight().pointerInput(listState, model, enabled) {
                detectChatRailDrag(
                    onPress = { point ->
                        val top = currentDisplayed * currentUsable
                        if (!currentEnabled || !visible || point.y !in top..(top + currentHitPixels)) {
                            false
                        } else {
                            session = DocumentRailDragSession(model, currentDisplayed, currentUsable)
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                listState.stopScroll(MutatePriority.UserInput)
                            }
                            true
                        }
                    },
                    onDragStart = { point -> session = session?.start(point.y) },
                    onDrag = { point ->
                        val previous = session
                        if (currentEnabled && previous != null) {
                            val next = previous.move(point.y)
                            session = next
                            if (next.fraction != previous.fraction) pending = next.fraction
                        }
                    },
                    onRelease = { session = null }
                )
            }) {
                Box(
                    modifier = Modifier.offset { IntOffset(0, (displayed * usable).roundToInt()) }
                        .size(28.dp, hitHeight)
                        .graphicsLayer { alpha = shown }
                        .semantics {
                            contentDescription = label
                            progressBarRangeInfo = ProgressBarRangeInfo(displayed, 0f..1f)
                            setProgress { value ->
                                if (value.isFinite()) {
                                    pending = value.coerceIn(0f, 1f)
                                    true
                                } else false
                            }
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(Modifier.padding(end = 3.dp)
                        .size(22.dp, 46.dp.coerceAtMost(hitHeight))
                        .border(2.dp, ink.copy(alpha = if (dragging) 0.98f else 0.85f), RoundedCornerShape(50)))
                }
            }
        }
    }
}
