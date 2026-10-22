package com.lucent.app.ui

import com.lucent.app.i18n.S
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val THINKING_LANE_MAX_HEIGHT = 220.dp

@Composable
internal fun ThinkingLane(
    readingKey: String,
    liveThought: State<String>?,
    storedThought: String,
    tag: String?,
    streaming: Boolean,
    answerSeconds: Int,
    lane: ThinkingLaneState = rememberThinkingLaneState(readingKey, streaming),
    onToggle: () -> Unit = {}
) {
    val accent = MaterialTheme.colorScheme.primary
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current.applicationContext
    val thought = liveThought?.value ?: storedThought
    val visibleThought = rememberRevealedText(thought, streaming)
    val canExpand = streaming || thought.isNotBlank()
    val latestCanExpand by rememberUpdatedState(canExpand)
    val expanded = lane.expanded && canExpand
    LaunchedEffect(streaming, lane.expanded) { lane.retainStreamingHeight(streaming) }
    if (streaming) {
        LaunchedEffect(lane.scroll) {
            snapshotFlow { lane.scroll.maxValue }.conflate().collect { max ->
                if (max > 0 && lane.scroll.value != max) lane.scroll.scrollTo(max)
            }
        }
    }
    LaunchedEffect(readingKey, lane) {
        snapshotFlow { lane.expanded to latestCanExpand }.conflate().collect {
            delay(250)
            lane.positionToPersist(latestCanExpand)?.let { ReadingPositionMemory.write(context, readingKey, it) }
        }
    }
    DisposableEffect(readingKey, lane) {
        onDispose {
            lane.positionToPersist(latestCanExpand)?.let { ReadingPositionMemory.write(context, readingKey, it) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()
        .onSizeChanged { lane.measuredHeightPx = it.height }
        .testTag("thinking_lane")) {
        ThinkingHeader(
            title = thinkingTitle(streaming, thought.isNotBlank(), answerSeconds),
            expanded = expanded,
            canExpand = canExpand,
            onToggle = { onToggle(); lane.toggle() }
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(animationSpec = LucentMotion.toIntSize(LucentMotion.Settle)),
            exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = LucentMotion.toIntSize(LucentMotion.Settle))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(start = 10.dp, bottom = 8.dp)
                    .drawBehind {
                        drawLine(
                            color = accent.copy(alpha = 0.28f),
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .padding(start = 10.dp)
            ) {
                if (!tag.isNullOrBlank()) {
                    Text(
                        text = tag,
                        color = onGradientMuted.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = visibleThought.ifBlank { S.thinkingWaiting },
                    color = if (visibleThought.isBlank()) {
                        onGradientMuted.copy(alpha = 0.7f)
                    } else {
                        onGradientMuted
                    },
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("thinking_text")
                        .heightIn(max = THINKING_LANE_MAX_HEIGHT)
                        .verticalScroll(lane.scroll)
                )
                if (streaming || lane.retainLiveHeight) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (streaming) ThoughtPulse(color = accent)
                    else Spacer(modifier = Modifier.size(6.dp))
                }
            }
        }
    }
}

@Composable
internal fun ThoughtPulse(color: Color) {
    val pulse = rememberInfiniteTransition(label = "thoughtPulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thoughtPulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
