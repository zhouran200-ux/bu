package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

internal const val DEFAULT_REVEAL_MILLIS_PER_CHAR = 3f

internal class StreamPacer(
    private val drainTimeSeconds: Float = 0.22f,
    private val minCharsPerSecond: Float = 30f,
    private val maxCharsPerSecond: Float = 180f,
    private val smoothing: Float = 0.35f
) {
    var displayedLength: Float = 0f
    var currentSpeed: Float = minCharsPerSecond

    fun reset(targetLength: Float) {
        displayedLength = targetLength
        currentSpeed = minCharsPerSecond
    }

    fun step(targetLength: Int, dtSeconds: Float): Int {
        val wanted = targetLength.toFloat()
        if (displayedLength > wanted) {
            displayedLength = wanted
            return targetLength
        }
        val backlog = wanted - displayedLength
        if (backlog <= 0.001f) {
            displayedLength = wanted
            return targetLength
        }
        val targetSpeed = (backlog / drainTimeSeconds).coerceIn(minCharsPerSecond, maxCharsPerSecond)
        currentSpeed += (targetSpeed - currentSpeed) * smoothing
        val advance = (currentSpeed * dtSeconds).coerceAtLeast(0.01f)
        displayedLength = (displayedLength + advance).coerceAtMost(wanted)
        return displayedLength.toInt().coerceIn(0, targetLength)
    }
}

@Composable
internal fun rememberRevealedText(
    target: String,
    streaming: Boolean,
    millisPerChar: Float = DEFAULT_REVEAL_MILLIS_PER_CHAR
): String {
    if (!streaming) return target
    val latestTarget = rememberUpdatedState(target)
    val pacer = remember { StreamPacer() }
    var displayedLength by remember { mutableFloatStateOf(target.length.toFloat()) }

    LaunchedEffect(streaming) {
        pacer.reset(latestTarget.value.length.toFloat())
        displayedLength = latestTarget.value.length.toFloat()
        var lastFrameNanos = 0L

        while (isActive) {
            val wanted = latestTarget.value.length
            if (pacer.displayedLength > wanted.toFloat()) {
                pacer.displayedLength = wanted.toFloat()
                displayedLength = wanted.toFloat()
                lastFrameNanos = 0L
            }

            if (pacer.displayedLength >= wanted.toFloat()) {
                lastFrameNanos = 0L
                snapshotFlow { latestTarget.value.length }.first { it != pacer.displayedLength.toInt() }
                continue
            }

            withFrameNanos { frameTimeNanos ->
                val dtSeconds = if (lastFrameNanos == 0L) {
                    0.016f
                } else {
                    ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                }
                lastFrameNanos = frameTimeNanos
                pacer.step(latestTarget.value.length, dtSeconds)
                displayedLength = pacer.displayedLength
            }
        }
    }

    val currentText = latestTarget.value
    val cut = revealCut(currentText, displayedLength.toInt())
    if (cut >= currentText.length) return currentText
    return currentText.substring(0, cut)
}

internal fun revealCut(text: String, index: Int): Int {
    var cut = index.coerceIn(0, text.length)
    if (cut in 1 until text.length && text[cut].isLowSurrogate() && text[cut - 1].isHighSurrogate()) cut--
    return cut
}
