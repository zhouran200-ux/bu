package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

internal val LocalChatComposerBackdrop = compositionLocalOf<HazeState?> { null }

@OptIn(ExperimentalHazeApi::class)
internal fun Modifier.chatComposerGlass(): Modifier = composed {
    val shape = RoundedCornerShape(24.dp)
    val pureWhite = LocalPureWhite.current
    val dark = !pureWhite && isDarkGlass()
    val background = if (pureWhite) LucentGlass.PureWhitePage else LocalBackdropColor.current
    val tint = if (dark) lucentPanelBackground() else Color.White
    val source = LocalChatComposerBackdrop.current
    val material = if (source != null) Modifier.hazeEffect(source, style = HazeStyle(
        backgroundColor = background,
        tint = HazeTint(tint.copy(alpha = if (dark) 0.82f else 0.88f)),
        blurRadius = 3.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(tint.copy(alpha = 0.94f))
    )) {
        inputScale = HazeInputScale.Auto
    } else Modifier.background(tint.copy(alpha = 0.94f))
    shadow(4.dp, shape, clip = false,
        ambientColor = LucentGlass.PureWhiteShadowAmbient,
        spotColor = LucentGlass.PureWhiteShadowSpot)
        .clip(shape)
        .then(material)
        .border(0.75.dp, foregroundRim(), shape)
}

internal fun Modifier.chatBottomFade(bottomInset: Dp): Modifier = composed {
    val color = if (LocalPureWhite.current) LucentGlass.PureWhitePage else LocalBackdropColor.current
    drawWithCache {
        val fadeHeight = (bottomInset.toPx() + 24.dp.toPx()).coerceAtMost(size.height)
        val top = size.height - fadeHeight
        val brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0f), color.copy(alpha = 0.55f)),
            startY = top,
            endY = size.height
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, fadeHeight))
        }
    }
}
