package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalOnGradient = compositionLocalOf { Color.White }
val LocalOnGradientMuted = compositionLocalOf { Color.White.copy(alpha = 0.65f) }
val LocalPureWhite = compositionLocalOf { false }

val LocalBottomBarInset = compositionLocalOf { 0.dp }
val LocalBackdropColor = compositionLocalOf { Color(0xFF0E0E14) }

object LucentGlass {
    val PureWhitePage = Color(0xFFF7F7F8)
    val PureWhiteSurface = Color(0xFFFFFFFF)
    val PureWhiteInk = Color(0xFF1A1D21)
    val PureWhiteDivider = Color(0xFFD9D9D9)
    val PureWhiteShadowAmbient = Color(0xFF1A1D21).copy(alpha = 0.08f)
    val PureWhiteShadowSpot = Color(0xFF1A1D21).copy(alpha = 0.12f)
    val PureWhiteControlFill = Color(0xFFF4F4F5)
    val PureWhiteControlFillPressed = Color(0xFFE3E3E5)
}

@Composable
fun isDarkGlass(): Boolean = LocalOnGradient.current.luminance() > 0.5f

fun panelOver(backdrop: Color): Color =
    if (backdrop.luminance() < 0.5f) lerp(backdrop, Color.White, 0.055f)
    else lerp(backdrop, Color.Black, 0.045f)

@Composable
fun lucentPanelBackground(): Color =
    if (LocalPureWhite.current) LucentGlass.PureWhiteSurface
    else panelOver(LocalBackdropColor.current)

fun lucentAccent(palette: List<Color>, ink: Color): Color {
    val dark = ink.luminance() > 0.5f
    val base = if (dark) {
        palette.maxByOrNull { it.luminance() } ?: return ink
    } else {
        palette.minByOrNull { it.luminance() } ?: return ink
    }
    val lift = if (dark) Color.White else Color.Black
    var shifted = base
    var guard = 0
    while (guard < 10 && !lucentAccentReadable(shifted, dark)) {
        shifted = lerp(shifted, lift, 0.12f)
        guard++
    }
    return shifted
}

private fun lucentAccentReadable(color: Color, dark: Boolean): Boolean =
    if (dark) color.luminance() >= 0.55f else color.luminance() <= 0.135f

fun lucentScheme(base: ColorScheme, palette: List<Color>, ink: Color, backdrop: Color): ColorScheme {
    val accent = lucentAccent(palette, ink)
    val content = if (accent.luminance() > 0.20f) Color(0xFF14141C) else Color.White
    val dark = ink.luminance() > 0.5f
    val surface = panelOver(backdrop)
    val container = lerp(surface, accent, if (dark) 0.30f else 0.18f)
    val lift = if (dark) 0.05f else 0.035f
    return base.copy(
        primary = accent,
        onPrimary = content,
        primaryContainer = container,
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = content,
        secondaryContainer = container,
        onSecondaryContainer = accent,
        tertiary = accent,
        onTertiary = content,
        tertiaryContainer = container,
        onTertiaryContainer = accent,
        background = backdrop,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceTint = accent,
        surfaceVariant = lerp(surface, ink, if (dark) 0.14f else 0.08f),
        onSurfaceVariant = ink.copy(alpha = 0.72f),
        surfaceDim = lerp(surface, backdrop, 0.55f),
        surfaceBright = lerp(surface, ink, lift * 3f),
        surfaceContainerLowest = lerp(surface, backdrop, 0.6f),
        surfaceContainerLow = surface,
        surfaceContainer = lerp(surface, ink, lift),
        surfaceContainerHigh = lerp(surface, ink, lift * 2f),
        surfaceContainerHighest = lerp(surface, ink, lift * 3f),
        inverseSurface = lerp(surface, ink, 0.88f),
        inverseOnSurface = surface,
        inversePrimary = accent,
        outline = ink.copy(alpha = 0.38f),
        outlineVariant = ink.copy(alpha = 0.16f)
    )
}

fun pureWhiteScheme(base: ColorScheme): ColorScheme {
    val ink = LucentGlass.PureWhiteInk
    val page = LucentGlass.PureWhitePage
    val surface = LucentGlass.PureWhiteSurface
    val divider = LucentGlass.PureWhiteDivider
    return base.copy(
        primary = ink,
        onPrimary = surface,
        primaryContainer = Color(0xFFECECED),
        onPrimaryContainer = ink,
        secondary = ink,
        onSecondary = surface,
        secondaryContainer = Color(0xFFECECED),
        onSecondaryContainer = ink,
        tertiary = ink,
        onTertiary = surface,
        tertiaryContainer = Color(0xFFECECED),
        onTertiaryContainer = ink,
        background = page,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceTint = ink,
        surfaceVariant = Color(0xFFF0F0F1),
        onSurfaceVariant = ink.copy(alpha = 0.60f),
        surfaceDim = Color(0xFFEDEDEE),
        surfaceBright = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = Color(0xFFFAFAFB),
        surfaceContainer = Color(0xFFF4F4F5),
        surfaceContainerHigh = Color(0xFFEFEFF0),
        surfaceContainerHighest = Color(0xFFEAEAEB),
        inverseSurface = ink,
        inverseOnSurface = surface,
        inversePrimary = ink,
        outline = divider,
        outlineVariant = divider.copy(alpha = 0.55f)
    )
}

@Composable
fun lucentGlassRim(strong: Boolean = false): Brush = foregroundRim(strong)

fun Modifier.frostedGlass(cornerRadius: Dp = 20.dp): Modifier = foregroundGlass(cornerRadius)

@Composable
fun Modifier.pressableGlass(
    onClick: () -> Unit,
    cornerRadius: Dp = 20.dp,
    enabled: Boolean = true,
    emphasized: Boolean = false
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressIndication = rememberScaleIndication()
    val shape = RoundedCornerShape(cornerRadius)
    return this.foregroundGlass(cornerRadius)
        .background(if ((pressed && enabled) || emphasized) glassControlColor(true) else Color.Transparent, shape)
        .clickable(interactionSource = interaction, indication = pressIndication, enabled = enabled) { onClick() }
}

@Composable
fun Modifier.selectedCard(selected: Boolean, cornerRadius: Dp): Modifier = composed {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(cornerRadius)
    val enabled = LocalBackgroundEnvironment.current.motionEnabled
    val tint = androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = if (isDarkGlass()) 0.13f else 0.09f) else Color.Transparent,
        animationSpec = if (enabled) androidx.compose.animation.core.tween(160) else androidx.compose.animation.core.snap(),
        label = "selectionWash"
    )
    this.clip(shape).drawBehind {
        drawRoundRect(color = tint.value, cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()))
    }
}
