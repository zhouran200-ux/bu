package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy

private fun averageItemSize(listState: LazyListState): Int {
    val visible = listState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return 1
    return (visible.fastSumBy { it.size } / visible.size).coerceAtLeast(1)
}

fun chatScrollMetrics(listState: LazyListState): ChatScrollMetrics {
    val info = listState.layoutInfo
    val visible = info.visibleItemsInfo
    if (info.totalItemsCount == 0 || visible.isEmpty()) {
        return ChatScrollMetrics(fraction = 0f, thumbFraction = 1f, scrollable = false)
    }
    val lead = visible.first()
    return ChatScrollMath.metrics(
        totalItems = info.totalItemsCount,
        averageItemSize = averageItemSize(listState),
        spacing = info.mainAxisItemSpacing,
        viewport = info.viewportEndOffset - info.viewportStartOffset,
        leadIndex = lead.index,
        leadOffset = lead.offset
    )
}

suspend fun scrollChatToFraction(listState: LazyListState, fraction: Float) {
    val info = listState.layoutInfo
    if (info.totalItemsCount == 0) return
    val target = ChatScrollMath.targetForFraction(
        totalItems = info.totalItemsCount,
        averageItemSize = averageItemSize(listState),
        spacing = info.mainAxisItemSpacing,
        viewport = info.viewportEndOffset - info.viewportStartOffset,
        fraction = fraction
    )
    listState.scrollToItem(target.index, target.offset)
}

@Composable
fun ChatScrollRail(
    listState: LazyListState,
    visible: Boolean,
    expanded: Boolean,
    dragging: Boolean,
    onRequestExpand: () -> Unit,
    onOpenHistory: () -> Unit,
    onDragStart: () -> Unit,
    onDragFraction: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ink = LocalOnGradient.current
    val metrics = chatScrollMetrics(listState)
    val motionOn = LocalBackgroundEnvironment.current.motionEnabled

    val animatedWidth by animateFloatAsState(
        targetValue = if (expanded) 10f else 4f,
        animationSpec = if (motionOn) LucentMotion.toSpring(LucentMotion.Settle) else snap(),
        label = "railWidth"
    )
    val shownAlpha by animateFloatAsState(
        targetValue = if (visible && metrics.scrollable) 1f else 0f,
        animationSpec = if (motionOn) LucentMotion.toSpring(LucentMotion.Settle) else snap(),
        label = "railAlpha"
    )

    val trackColor = ink.copy(alpha = 0.14f)
    val thumbColor = ink.copy(alpha = if (dragging) 0.85f else 0.42f)
    val thumbCorner = with(LocalDensity.current) { 6.dp.toPx() }

    fun fractionAt(y: Float, height: Float): Float {
        val trackHeight = (height - 2f * thumbCorner).coerceAtLeast(1f)
        return ((y - thumbCorner) / trackHeight).coerceIn(0f, 1f)
    }

    fun insideThumb(y: Float, height: Float): Boolean {
        val trackHeight = (height - 2f * thumbCorner).coerceAtLeast(1f)
        val thumbHeight = (metrics.thumbFraction * trackHeight).coerceAtLeast(thumbCorner * 2f)
        val thumbTop = thumbCorner + metrics.fraction * (trackHeight - thumbHeight)
        return y in thumbTop..(thumbTop + thumbHeight)
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = shownAlpha }
            .width(if (expanded) 30.dp else 20.dp)
            .fillMaxHeight()
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .pointerInput(expanded, metrics.scrollable, metrics.thumbFraction, metrics.fraction) {
                if (!metrics.scrollable) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val height = size.height.toFloat()
                    if (!expanded) {
                        onRequestExpand()
                        return@awaitEachGesture
                    }
                    val longPressMs = viewConfiguration.longPressTimeoutMillis
                    val startedAt = System.currentTimeMillis()
                    var dragArmed = false
                    var released = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        if (!change.pressed) {
                            released = true
                            break
                        }
                        if (!dragArmed && System.currentTimeMillis() - startedAt >= longPressMs) {
                            dragArmed = true
                            Haptics.tick(context)
                            onDragStart()
                        }
                        if (dragArmed) {
                            onDragFraction(fractionAt(change.position.y, height))
                            change.consume()
                        }
                    }
                    when {
                        dragArmed -> onDragEnd()
                        released && !insideThumb(down.position.y, height) -> onOpenHistory()
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(animatedWidth.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .drawBehind {
                    drawTrack(trackColor, thumbColor, metrics, thumbCorner)
                }
        )
    }
}

private fun DrawScope.drawTrack(
    trackColor: Color,
    thumbColor: Color,
    metrics: ChatScrollMetrics,
    corner: Float
) {
    val w = size.width
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(0f, 0f),
        size = Size(w, size.height),
        cornerRadius = CornerRadius(corner, corner)
    )
    val trackHeight = (size.height - 2f * corner).coerceAtLeast(1f)
    val thumbHeight = (metrics.thumbFraction * trackHeight).coerceAtLeast(corner * 2f)
    val thumbTop = corner + metrics.fraction * (trackHeight - thumbHeight)
    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(0f, thumbTop - corner),
        size = Size(w, thumbHeight),
        cornerRadius = CornerRadius(corner, corner)
    )
}
