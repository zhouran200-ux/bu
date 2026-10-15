package com.lucent.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val SliderTrackHeight = 10.dp
private val SliderThumbRadius = 8.5.dp
private val SliderHitHeight = 36.dp
private val SliderButtonSize = 26.dp

@Composable
fun StepperSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {}
) {
    val ink = LocalOnGradient.current
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val motion = LocalBackgroundEnvironment.current.motionEnabled
    val low = valueRange.start
    val high = valueRange.endInclusive
    val span = (high - low).coerceAtLeast(0.0001f)
    val safeStep = step.coerceAtLeast(0.0001f)
    val thumbPx = with(LocalDensity.current) { SliderThumbRadius.toPx() }

    var dragging by remember { mutableStateOf(false) }
    var liveValue by remember(value) { mutableFloatStateOf(value) }
    val shown = if (dragging) liveValue else value
    val fraction by animateFloatAsState(
        targetValue = ((shown - low) / span).coerceIn(0f, 1f),
        animationSpec = if (motion && !dragging) LucentMotion.toSpring(LucentMotion.Settle) else snap(),
        label = "stepperFraction"
    )

    val commit = rememberUpdatedState<(Float) -> Unit> { raw ->
        val snapped = (low + ((raw - low) / safeStep).roundToInt() * safeStep).coerceIn(low, high)
        if (snapped != liveValue) {
            liveValue = snapped
            Haptics.tick(context)
        }
        onValueChange(snapped)
    }
    val finish = rememberUpdatedState(onValueChangeFinished)
    val currentValue = rememberUpdatedState(value)
    val restoreValue = rememberUpdatedState(onValueChange)

    fun nudge(delta: Float) {
        commit.value(shown + delta)
        finish.value()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = ink,
            fontSize = LucentTitles.ItemSize,
            fontWeight = LucentTitles.ItemWeight
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = valueLabel,
                color = ink.copy(alpha = 0.72f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SliderEdgeButton(
                icon = Icons.Rounded.Remove,
                description = "减小$title",
                enabled = shown > low + safeStep / 2f,
                onClick = { nudge(-safeStep) }
            )
            Spacer(modifier = Modifier.width(6.dp))
            SliderTrack(
                fraction = fraction,
                accent = accent,
                ink = ink,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$title $valueLabel" }
                    .pointerInput(low, span, safeStep, thumbPx) {
                        fun fractionAt(x: Float): Float =
                            ((x - thumbPx) / (size.width - 2f * thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            val initialValue = currentValue.value
                            var direction = StepperGestureDirection.Waiting
                            var changedDraft = false
                            var completed = false
                            try {
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Main)
                                        .changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed) break
                                    if (direction == StepperGestureDirection.Waiting) {
                                        val delta = change.position - down.position
                                        direction = stepperGestureDirection(
                                            delta.x, delta.y, viewConfiguration.touchSlop
                                        )
                                    }
                                    if (direction == StepperGestureDirection.Vertical) break
                                    if (!change.pressed) {
                                        if (direction == StepperGestureDirection.Waiting) {
                                            val finalChange = awaitPointerEvent(PointerEventPass.Final)
                                                .changes.firstOrNull { it.id == down.id } ?: break
                                            if (finalChange.isConsumed) break
                                            finalChange.consume()
                                        } else {
                                            change.consume()
                                        }
                                        changedDraft = true
                                        commit.value(low + fractionAt(change.position.x) * span)
                                        completed = true
                                        finish.value()
                                        break
                                    }
                                    if (direction == StepperGestureDirection.Horizontal) {
                                        dragging = true
                                        change.consume()
                                        changedDraft = true
                                        commit.value(low + fractionAt(change.position.x) * span)
                                    } else {
                                        val finalChange = awaitPointerEvent(PointerEventPass.Final)
                                            .changes.firstOrNull { it.id == down.id } ?: break
                                        if (finalChange.isConsumed) break
                                    }
                                }
                            } finally {
                                if (!completed && changedDraft) {
                                    liveValue = initialValue
                                    restoreValue.value(initialValue)
                                }
                                dragging = false
                            }
                        }
                    }
            )
            Spacer(modifier = Modifier.width(6.dp))
            SliderEdgeButton(
                icon = Icons.Rounded.Add,
                description = "增大$title",
                enabled = shown < high - safeStep / 2f,
                onClick = { nudge(safeStep) }
            )
        }
    }
}

@Composable
private fun SliderEdgeButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val ink = LocalOnGradient.current
    Box(
        modifier = Modifier
            .size(width = 30.dp, height = 36.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(SliderButtonSize)
                .graphicsLayer { alpha = if (enabled) 1f else 0.32f }
                .clip(CircleShape)
                .border(1.2.dp, ink.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = ink,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SliderTrack(
    fraction: Float,
    accent: Color,
    ink: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbRadius = with(density) { SliderThumbRadius.toPx() }
    val trackHeight = with(density) { SliderTrackHeight.toPx() }
    val halo = with(density) { 1.5.dp.toPx() }
    val rim = with(density) { 1.dp.toPx() }
    val dark = isDarkGlass()
    val inactive = ink.copy(alpha = if (dark) 0.18f else 0.12f)
    val thumbFill = Color.White.copy(alpha = if (dark) 0.92f else 1f)
    val thumbRim = if (dark) Color.White.copy(alpha = 0.28f) else ink.copy(alpha = 0.12f)
    val thumbShadow = Color.Black.copy(alpha = if (dark) 0.35f else 0.10f)

    Box(modifier = modifier.fillMaxWidth().height(SliderHitHeight)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val radius = trackHeight / 2f
            val usable = (size.width - 2f * thumbRadius).coerceAtLeast(0f)
            val centerX = thumbRadius + fraction.coerceIn(0f, 1f) * usable
            drawRoundRect(
                color = inactive,
                topLeft = Offset(0f, centerY - radius),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(radius, radius)
            )
            if (centerX > 0.5f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(0f, centerY - radius),
                    size = Size(centerX, trackHeight),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
            drawCircle(
                color = thumbShadow,
                radius = thumbRadius + halo * 1.8f,
                center = Offset(centerX, centerY),
                alpha = 0.45f
            )
            drawCircle(
                color = thumbShadow,
                radius = thumbRadius + halo,
                center = Offset(centerX, centerY)
            )
            drawCircle(color = thumbFill, radius = thumbRadius, center = Offset(centerX, centerY))
            drawCircle(
                color = thumbRim,
                radius = thumbRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = rim)
            )
        }
    }
}
