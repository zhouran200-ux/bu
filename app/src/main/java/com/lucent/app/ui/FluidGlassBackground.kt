package com.lucent.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalInspectionMode

data class BackgroundEnvironment(val active: Boolean = true, val motionEnabled: Boolean = true)

val LocalBackgroundEnvironment = staticCompositionLocalOf { BackgroundEnvironment() }

@Composable
fun FluidGlassBackground(
    palette: List<Color>,
    backdropColor: Color,
    paletteName: String = "",
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    pureWhite: Boolean = false
) {
    val environment = LocalBackgroundEnvironment.current
    val inspection = LocalInspectionMode.current
    val moving = animated && environment.motionEnabled && !inspection && !pureWhite

    if (pureWhite) {
        Canvas(modifier.fillMaxSize().background(backdropColor)) { }
        return
    }

    val cyclePalettes = remember { LucentPalette.pickerEntries.map { it.colors } }
    val activePalette: List<Color> = when (paletteName) {
        PALETTE_CYCLE -> rememberCyclingPaletteColors(
            palettes = cyclePalettes,
            animated = animated,
            environment = environment
        )
        PALETTE_RANDOM -> rememberRandomPaletteColors(animated = animated, environment = environment)
        else -> palette
    }

    val primary = activePalette.getOrNull(0) ?: MaterialTheme.colorScheme.primary
    val secondary = activePalette.getOrNull(1) ?: MaterialTheme.colorScheme.secondary
    val tertiary = activePalette.getOrNull(2) ?: MaterialTheme.colorScheme.tertiary
    val isDark = backdropColor.luminance() < 0.5f

    val blobAlpha = if (isDark) 0.54f else 0.30f

    if (!moving) {
        Canvas(modifier.fillMaxSize().background(backdropColor)) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = blobAlpha), Color.Transparent),
                    center = Offset(w * 0.25f, h * 0.15f),
                    radius = w * 0.9f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(secondary.copy(alpha = blobAlpha * 0.85f), Color.Transparent),
                    center = Offset(w * 0.85f, h * 0.5f),
                    radius = w * 0.8f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(tertiary.copy(alpha = blobAlpha * 0.75f), Color.Transparent),
                    center = Offset(w * 0.35f, h * 0.85f),
                    radius = w * 0.85f
                )
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundTransition")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientMotion"
    )

    Canvas(modifier.fillMaxSize().background(backdropColor)) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val t = animProgress * (2 * Math.PI.toFloat())
        val x1 = w * (0.28f + 0.12f * kotlin.math.cos(t))
        val y1 = h * (0.20f + 0.08f * kotlin.math.sin(t))
        val x2 = w * (0.75f - 0.12f * kotlin.math.sin(t))
        val y2 = h * (0.52f + 0.10f * kotlin.math.cos(t))
        val x3 = w * (0.35f + 0.08f * kotlin.math.sin(t))
        val y3 = h * (0.82f - 0.08f * kotlin.math.cos(t))

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = blobAlpha), Color.Transparent),
                center = Offset(x1, y1),
                radius = w * 0.95f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = blobAlpha * 0.85f), Color.Transparent),
                center = Offset(x2, y2),
                radius = w * 0.85f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(tertiary.copy(alpha = blobAlpha * 0.75f), Color.Transparent),
                center = Offset(x3, y3),
                radius = w * 0.9f
            )
        )
    }
}
