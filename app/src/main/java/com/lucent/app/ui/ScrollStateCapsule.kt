package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

internal fun scrollRailFraction(value: Int, maxValue: Int): Float =
    if (maxValue <= 0) 0f else (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

internal fun scrollRailTarget(fraction: Float, maxValue: Int): Int =
    if (maxValue <= 0) 0 else (fraction.coerceIn(0f, 1f) * maxValue).roundToInt().coerceIn(0, maxValue)

@Composable
internal fun ScrollStateCapsule(
    scrollState: ScrollState,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    val ink = LocalOnGradient.current
    val scope = rememberCoroutineScope()
    var session by remember(scrollState) { mutableStateOf<ScrollCapsuleDrag?>(null) }
    var show by remember(scrollState) { mutableStateOf(false) }
    val dragging = session != null
    val displayed = session?.fraction ?: scrollRailFraction(scrollState.value, scrollState.maxValue)
    val currentDisplayed by rememberUpdatedState(displayed)
    val currentEnabled by rememberUpdatedState(enabled)
    val scrollable = scrollState.maxValue > 0
    val shown by animateFloatAsState(
        if (enabled && (show || dragging)) 1f else 0f,
        animationSpec = tween(140),
        label = "scrollStateCapsuleVisibility"
    )

    LaunchedEffect(scrollState.isScrollInProgress, dragging, enabled) {
        if (!enabled) {
            session = null
            show = false
        } else if (scrollState.isScrollInProgress || dragging) {
            show = true
        } else {
            delay(650)
            show = false
        }
    }
    if (!enabled || (!scrollable && !dragging)) return
    BoxWithConstraints(modifier = modifier.width(28.dp).fillMaxHeight()) {
        val density = LocalDensity.current
        val hitHeight = 52.dp.coerceAtMost(maxHeight)
        val usable = with(density) { (maxHeight - hitHeight).toPx().coerceAtLeast(1f) }
        val currentUsable by rememberUpdatedState(usable)
        val currentHit by rememberUpdatedState(with(density) { hitHeight.toPx() })
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .pointerInput(scrollState, enabled) {
                    detectChatRailDrag(
                        onPress = { point ->
                            val top = currentDisplayed * currentUsable
                            if (!currentEnabled || point.y !in top..(top + currentHit)) {
                                false
                            } else {
                                session = ScrollCapsuleDrag(currentDisplayed)
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    scrollState.stopScroll(MutatePriority.UserInput)
                                }
                                true
                            }
                        },
                        onDragStart = { point -> session = session?.start(point.y) },
                        onDrag = { point ->
                            val previous = session
                            if (currentEnabled && previous != null) {
                                val next = previous.move(point.y, currentUsable)
                                session = next
                                if (next.fraction != previous.fraction) {
                                    val target = scrollRailTarget(next.fraction, scrollState.maxValue)
                                    scrollState.dispatchRawDelta((target - scrollState.value).toFloat())
                                }
                            }
                        },
                        onRelease = { session = null }
                    )
                }
        ) {
            if (show || dragging || shown > 0.01f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, (displayed * usable).roundToInt()) }
                        .size(28.dp, hitHeight)
                        .graphicsLayer { alpha = shown }
                        .semantics {
                            contentDescription = label
                            progressBarRangeInfo = ProgressBarRangeInfo(displayed, 0f..1f)
                            setProgress { value ->
                                if (value.isFinite()) {
                                    val target = scrollRailTarget(value, scrollState.maxValue)
                                    scrollState.dispatchRawDelta((target - scrollState.value).toFloat())
                                    true
                                } else {
                                    false
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(22.dp, 46.dp.coerceAtMost(hitHeight))
                            .border(
                                2.dp,
                                ink.copy(alpha = if (dragging) 0.98f else 0.85f),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
        }
    }
}

private data class ScrollCapsuleDrag(
    val startFraction: Float,
    val originY: Float? = null,
    val fraction: Float = startFraction
) {
    fun start(pointerY: Float): ScrollCapsuleDrag = copy(originY = pointerY)

    fun move(pointerY: Float, usable: Float): ScrollCapsuleDrag {
        val origin = originY ?: return this
        return copy(fraction = railDragFraction(startFraction, pointerY - origin, usable))
    }
}
