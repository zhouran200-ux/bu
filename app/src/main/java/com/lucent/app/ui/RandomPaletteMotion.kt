package com.lucent.app.ui

import androidx.compose.ui.graphics.Color

const val RANDOM_SWITCH_MS = 20_000L
const val RANDOM_FADE_MS = 1_800L
private const val RANDOM_FADE_STEPS = 24

@androidx.compose.runtime.Composable
fun rememberRandomPaletteColors(
    animated: Boolean = true,
    environment: BackgroundEnvironment = LocalBackgroundEnvironment.current
): List<Color> {
    val boards = LucentPalette.pickerEntries
    val colors = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(boards.first().colors)
    }
    val running = animated && environment.active && environment.motionEnabled &&
        !androidx.compose.ui.platform.LocalInspectionMode.current
    if (!running) return colors.value
    val seed = androidx.compose.runtime.remember { kotlin.random.Random.nextInt() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var startNanos = -1L
        var cycle = -1L
        var prevIdx = -1
        var holdColors: List<Color> = colors.value
        var nextColors: List<Color> = holdColors
        var lastStep = -1
        val period = (RANDOM_SWITCH_MS + RANDOM_FADE_MS).toDouble()
        while (true) {
            androidx.compose.animation.core.withInfiniteAnimationFrameNanos { frameNanos ->
                if (startNanos < 0L) {
                    startNanos = frameNanos
                    return@withInfiniteAnimationFrameNanos
                }
                val t = (frameNanos - startNanos) / 1_000_000.0
                val cyc = (t / period).toLong()
                if (cyc != cycle) {
                    cycle = cyc
                    var idx = (((cyc * 2_654_435_761L) xor seed.toLong()) % boards.size).toInt()
                    if (idx < 0) idx = -idx
                    var guard = 0
                    while (idx == prevIdx && guard++ < boards.size) idx = (idx + 1) % boards.size
                    prevIdx = idx
                    holdColors = colors.value
                    nextColors = boards[idx].colors
                    lastStep = -1
                }
                val inCycle = t - cycle * period
                val fade = ((inCycle - RANDOM_SWITCH_MS) / RANDOM_FADE_MS).coerceIn(0.0, 1.0)
                if (fade <= 0.0) {
                    if (lastStep != 0 && colors.value !== holdColors) {
                        lastStep = 0
                        colors.value = holdColors
                    }
                } else {
                    val eased = fade * fade * (3.0 - 2.0 * fade)
                    val step = (eased * RANDOM_FADE_STEPS).toInt().coerceAtMost(RANDOM_FADE_STEPS - 1)
                    if (step != lastStep) {
                        lastStep = step
                        val f = eased.toFloat()
                        colors.value = holdColors.indices.map { i ->
                            androidx.compose.ui.graphics.lerp(holdColors[i], nextColors[i], f)
                        }
                    }
                }
            }
        }
    }
    return colors.value
}
