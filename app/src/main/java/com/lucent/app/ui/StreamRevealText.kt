package com.lucent.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

internal const val DEFAULT_REVEAL_MILLIS_PER_CHAR = 3f

private const val MIN_REVEAL_SPEED_SCALE = 0.1f
private const val MAX_REVEAL_SPEED_SCALE = 10f

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

    fun step(
        targetLength: Int,
        dtSeconds: Float,
        millisPerChar: Float = DEFAULT_REVEAL_MILLIS_PER_CHAR
    ): Int {
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
        val scale = revealSpeedScale(millisPerChar)
        val slowest = minCharsPerSecond * scale
        val fastest = (maxCharsPerSecond * scale).coerceAtLeast(slowest)
        currentSpeed = currentSpeed.coerceIn(slowest, fastest)
        val targetSpeed = (backlog / drainTimeSeconds).coerceIn(slowest, fastest)
        currentSpeed += (targetSpeed - currentSpeed) * smoothing
        val advance = (currentSpeed * dtSeconds.coerceAtLeast(0f)).coerceAtLeast(0.01f)
        displayedLength = (displayedLength + advance).coerceAtMost(wanted)
        return displayedLength.toInt().coerceIn(0, targetLength)
    }
}

internal class StreamRevealDriver {
    private val pacer = StreamPacer()
    private var lastTarget = ""
    private var lastStreaming = false
    private var finishing = false
    private var started = false

    var visibleCut: Int = 0
        private set

    val displayedLength: Float get() = pacer.displayedLength

    fun update(target: String, streaming: Boolean, motionEnabled: Boolean): Boolean {
        if (!started) {
            started = true
            lastTarget = target
            lastStreaming = streaming
            snap(target)
            return false
        }
        val streamStarted = streaming && !lastStreaming
        val streamEnded = !streaming && lastStreaming
        val rewritten = target.length < lastTarget.length || !target.startsWith(lastTarget)
        if (!motionEnabled || streamStarted || rewritten || pacer.displayedLength > target.length.toFloat()) {
            snap(target)
            finishing = false
        }
        if (streamEnded && motionEnabled && !rewritten) {
            finishing = pacer.displayedLength < target.length.toFloat()
        }
        if (streaming) finishing = false
        lastTarget = target
        lastStreaming = streaming
        return motionEnabled && (streaming || finishing) && pacer.displayedLength < target.length.toFloat()
    }

    fun frame(target: String, dtSeconds: Float, millisPerChar: Float) {
        visibleCut = revealCut(target, pacer.step(target.length, dtSeconds, millisPerChar))
        if (pacer.displayedLength >= target.length.toFloat()) finishing = false
    }

    fun settle(target: String) {
        if (visibleCut != target.length || pacer.displayedLength != target.length.toFloat()) snap(target)
    }

    private fun snap(target: String) {
        pacer.reset(target.length.toFloat())
        visibleCut = target.length
    }
}

private data class StreamRevealInput(
    val target: String,
    val streaming: Boolean,
    val motionEnabled: Boolean,
    val millisPerChar: Float
)

@Composable
internal fun rememberRevealedText(
    target: String,
    streaming: Boolean,
    millisPerChar: Float = DEFAULT_REVEAL_MILLIS_PER_CHAR
): String {
    val environment = LocalBackgroundEnvironment.current
    val revealMotionEnabled = environment.active && environment.motionEnabled
    val input = StreamRevealInput(
        target = target,
        streaming = streaming,
        motionEnabled = revealMotionEnabled,
        millisPerChar = normalizedRevealMillisPerChar(millisPerChar)
    )
    val latestInput = rememberUpdatedState(input)
    val driver = remember { StreamRevealDriver() }
    var visibleCut by remember { mutableIntStateOf(target.length) }

    LaunchedEffect(driver) {
        var lastFrameNanos = 0L
        while (isActive) {
            val current = latestInput.value
            if (driver.update(current.target, current.streaming, current.motionEnabled)) {
                withFrameNanos { frameTimeNanos ->
                    val dtSeconds = if (lastFrameNanos == 0L) {
                        0.016f
                    } else {
                        ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    }
                    lastFrameNanos = frameTimeNanos
                    driver.frame(current.target, dtSeconds, current.millisPerChar)
                }
            } else {
                lastFrameNanos = 0L
                driver.settle(current.target)
                if (visibleCut != driver.visibleCut) visibleCut = driver.visibleCut
                snapshotFlow { latestInput.value }.first { it != current }
            }
            if (visibleCut != driver.visibleCut) visibleCut = driver.visibleCut
        }
    }

    if (!revealMotionEnabled) return target
    val cut = revealCut(target, visibleCut)
    return if (cut >= target.length) target else target.substring(0, cut)
}

internal fun normalizedRevealMillisPerChar(value: Float): Float =
    value.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_REVEAL_MILLIS_PER_CHAR

internal fun revealSpeedScale(millisPerChar: Float): Float =
    (DEFAULT_REVEAL_MILLIS_PER_CHAR / normalizedRevealMillisPerChar(millisPerChar))
        .coerceIn(MIN_REVEAL_SPEED_SCALE, MAX_REVEAL_SPEED_SCALE)

internal fun revealCut(text: String, index: Int): Int {
    var cut = index.coerceIn(0, text.length)
    if (cut in 1 until text.length && text[cut].isLowSurrogate() && text[cut - 1].isHighSurrogate()) cut--
    if (cut == 0 || cut == text.length) return cut

    val current = Character.codePointAt(text, cut)
    val previous = Character.codePointAt(text, previousCodePointStart(text, cut))
    val unsafeBoundary = isGraphemeExtension(current) ||
        current == ZERO_WIDTH_JOINER ||
        previous == ZERO_WIDTH_JOINER ||
        (previous == CARRIAGE_RETURN && current == LINE_FEED) ||
        (isRegionalIndicator(current) && countRegionalIndicatorsBefore(text, cut) % 2 == 1)

    return if (unsafeBoundary) graphemeStartBefore(text, cut) else cut
}

private fun previousCodePointStart(text: String, index: Int): Int {
    val previous = (index - 1).coerceAtLeast(0)
    return if (previous > 0 && text[previous].isLowSurrogate() && text[previous - 1].isHighSurrogate()) {
        previous - 1
    } else {
        previous
    }
}

private fun graphemeStartBefore(text: String, boundary: Int): Int {
    var start = previousCodePointStart(text, boundary)
    if (Character.codePointAt(text, start) == ZERO_WIDTH_JOINER && start > 0) {
        start = previousCodePointStart(text, start)
    }
    while (start > 0 && isGraphemeExtension(Character.codePointAt(text, start))) {
        start = previousCodePointStart(text, start)
    }
    while (start > 0) {
        val joiner = previousCodePointStart(text, start)
        if (joiner == 0 || Character.codePointAt(text, joiner) != ZERO_WIDTH_JOINER) break
        start = previousCodePointStart(text, joiner)
        while (start > 0 && isGraphemeExtension(Character.codePointAt(text, start))) {
            start = previousCodePointStart(text, start)
        }
    }
    return start
}

private fun countRegionalIndicatorsBefore(text: String, boundary: Int): Int {
    var cursor = boundary
    var count = 0
    while (cursor > 0) {
        val start = previousCodePointStart(text, cursor)
        if (!isRegionalIndicator(Character.codePointAt(text, start))) break
        count++
        cursor = start
    }
    return count
}

private fun isGraphemeExtension(codePoint: Int): Boolean {
    val type = Character.getType(codePoint)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt() ||
        codePoint in 0xFE00..0xFE0F ||
        codePoint in 0xE0100..0xE01EF ||
        codePoint in 0x1F3FB..0x1F3FF ||
        codePoint in 0xE0020..0xE007F
}

private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

private const val ZERO_WIDTH_JOINER = 0x200D
private const val CARRIAGE_RETURN = 0x000D
private const val LINE_FEED = 0x000A
