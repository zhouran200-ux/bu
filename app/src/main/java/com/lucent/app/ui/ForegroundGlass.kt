package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

internal val LocalGlassBackdrop = compositionLocalOf<HazeState?> { null }

internal data class ForegroundMaterialSpec(val tintAlpha: Float, val fallbackAlpha: Float, val blur: Float)

internal fun foregroundMaterialSpec(dark: Boolean, reading: Boolean): ForegroundMaterialSpec = when {
    reading && dark -> ForegroundMaterialSpec(0.30f, 0.94f, 28f)
    reading -> ForegroundMaterialSpec(0.56f, 0.94f, 28f)
    dark -> ForegroundMaterialSpec(0.16f, 0.88f, 24f)
    else -> ForegroundMaterialSpec(0.30f, 0.88f, 24f)
}

@Composable
internal fun foregroundRim(strong: Boolean = false): Brush {
    if (LocalPureWhite.current) return SolidColor(LucentGlass.PureWhiteDivider.copy(alpha = 0.75f))
    val dark = isDarkGlass()
    val top = if (dark) 0.30f else 0.62f
    return Brush.linearGradient(listOf(
        Color.White.copy(alpha = top * if (strong) 1f else 0.8f),
        LocalOnGradient.current.copy(alpha = 0.035f),
        Color.White.copy(alpha = if (dark) 0.12f else 0.28f)
    ))
}

@Composable
internal fun glassControlColor(selected: Boolean = false): Color {
    if (LocalPureWhite.current) return if (selected) LucentGlass.PureWhiteControlFillPressed else LucentGlass.PureWhiteControlFill
    return LocalOnGradient.current.copy(alpha = if (selected) 0.15f else 0.065f)
}

@Composable
internal fun glassPanelColor(): Color =
    if (LocalPureWhite.current) LucentGlass.PureWhiteSurface
    else lerp(lucentPanelBackground(), LocalOnGradient.current, if (isDarkGlass()) 0.07f else 0.015f)

internal fun Modifier.glassPanel(cornerRadius: Dp = 24.dp): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val pureWhite = LocalPureWhite.current
    val rim = foregroundRim()
    shadow(8.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.05f),
        spotColor = Color.Black.copy(alpha = 0.08f))
        .clip(shape)
        .background(glassPanelColor())
        .then(if (pureWhite) Modifier else Modifier.background(Brush.verticalGradient(listOf(
            Color.White.copy(alpha = 0.045f), Color.Transparent
        ))))
        .border(0.75.dp, rim, shape)
}

internal fun Modifier.foregroundGlass(cornerRadius: Dp = 20.dp, reading: Boolean = false): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val rim = foregroundRim()
    if (LocalPureWhite.current) {
        return@composed shadow(3.dp, shape, clip = false,
            ambientColor = LucentGlass.PureWhiteShadowAmbient, spotColor = LucentGlass.PureWhiteShadowSpot)
            .clip(shape).background(LucentGlass.PureWhiteSurface).border(0.75.dp, rim, shape)
    }
    val dark = isDarkGlass()
    val spec = foregroundMaterialSpec(dark, reading)
    val background = LocalBackdropColor.current
    val tint = if (dark) lucentPanelBackground() else Color.White
    val source = LocalGlassBackdrop.current
    val material = if (source != null) Modifier.hazeEffect(source, style = HazeStyle(
        backgroundColor = background,
        tint = HazeTint(tint.copy(alpha = spec.tintAlpha)),
        blurRadius = spec.blur.dp,
        noiseFactor = 0.025f,
        fallbackTint = HazeTint(tint.copy(alpha = spec.fallbackAlpha))
    )) else Modifier.background(tint.copy(alpha = spec.fallbackAlpha))
    shadow(3.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.035f),
        spotColor = Color.Black.copy(alpha = 0.06f))
        .clip(shape).then(material)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.035f else 0.07f), Color.Transparent)))
        .border(0.75.dp, rim, shape)
}
