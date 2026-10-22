package com.lucent.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

internal const val DEFAULT_REVEAL_MILLIS_PER_CHAR = 3f
private const val MIN_REVEAL_MILLIS = 48
private const val MAX_REVEAL_MILLIS = 220

@Composable
internal fun rememberRevealedText(
    target: String,
    streaming: Boolean,
    millisPerChar: Float = DEFAULT_REVEAL_MILLIS_PER_CHAR
): String {
    if (!streaming) return target
    val reveal = remember { Animatable(target.length.toFloat()) }
    LaunchedEffect(target) {
        val wanted = target.length.toFloat()
        if (reveal.value > wanted) {
            reveal.snapTo(wanted)
            return@LaunchedEffect
        }
        if (reveal.value >= wanted) return@LaunchedEffect
        val distance = wanted - reveal.value
        reveal.animateTo(
            targetValue = wanted,
            animationSpec = tween(
                durationMillis = (distance * millisPerChar).toInt()
                    .coerceIn(MIN_REVEAL_MILLIS, MAX_REVEAL_MILLIS),
                easing = LinearEasing
            )
        )
    }
    val shown = reveal.value.toInt().coerceIn(0, target.length)
    if (shown >= target.length) return target
    return target.substring(0, revealCut(target, shown))
}

internal fun revealCut(text: String, index: Int): Int {
    var cut = index.coerceIn(0, text.length)
    if (cut in 1 until text.length && text[cut].isLowSurrogate() && text[cut - 1].isHighSurrogate()) cut--
    return cut
}
