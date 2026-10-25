package com.lucent.app.ui

import com.lucent.app.i18n.S
import kotlinx.coroutines.flow.conflate
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
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
    val motionOn = LocalBackgroundEnvironment.current.motionEnabled
    val reading = LocalReadingTypography.current
    val thought = liveThought?.value ?: storedThought
    val visibleThought = rememberRevealedText(thought, streaming)
    val canExpand = streaming || thought.isNotBlank()
    val expanded = lane.expanded && canExpand
    LaunchedEffect(streaming, lane.expanded) { lane.retainStreamingHeight(streaming) }
    if (streaming && !lane.fullExpanded) {
        LaunchedEffect(lane.scroll) {
            snapshotFlow { lane.scroll.maxValue }.conflate().collect { max ->
                if (max > 0 && lane.scroll.value != max) lane.scroll.scrollTo(max)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()
        .onSizeChanged { lane.measuredHeightPx = it.height }
        .testTag("thinking_lane")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ThinkingHeader(
                title = thinkingTitle(streaming, thought.isNotBlank(), answerSeconds),
                expanded = expanded,
                canExpand = canExpand,
                onToggle = { onToggle(); lane.toggle() }
            )
            if (expanded) {
                ThinkingFullExpandButton(
                    fullExpanded = lane.fullExpanded,
                    onToggle = { onToggle(); lane.toggleFullExpanded() },
                    modifier = Modifier.testTag("thinking_full_expand_toggle")
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = if (motionOn) fadeIn(tween(160)) + expandVertically(animationSpec = LucentMotion.toIntSize(LucentMotion.Settle)) else fadeIn(tween(0)),
            exit = if (motionOn) fadeOut(tween(140)) + shrinkVertically(animationSpec = LucentMotion.toIntSize(LucentMotion.Settle)) else fadeOut(tween(0))
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
                ScopedReadingTypography {
                    Text(
                        text = visibleThought.ifBlank { S.thinkingWaiting },
                        color = if (visibleThought.isBlank()) {
                            onGradientMuted.copy(alpha = 0.7f)
                        } else {
                            onGradientMuted
                        },
                        fontSize = 13.sp,
                        lineHeight = (19f * reading.lineFactor).sp,
                        letterSpacing = reading.letterSpacing.sp,
                        fontFamily = reading.fontFamily,
                        modifier = if (lane.fullExpanded) {
                            Modifier
                                .fillMaxWidth()
                                .testTag("thinking_text")
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .testTag("thinking_text")
                                .heightIn(max = THINKING_LANE_MAX_HEIGHT)
                                .verticalScroll(lane.scroll)
                        }
                    )
                }
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
    val motionOn = LocalBackgroundEnvironment.current.motionEnabled
    val alpha = if (motionOn) {
        val pulse = rememberInfiniteTransition(label = "thoughtPulse")
        val animated by pulse.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(650, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "thoughtPulseAlpha"
        )
        animated
    } else {
        0.75f
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
