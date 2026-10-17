package com.lucent.app.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal data class AppThemeColors(
    val scheme: ColorScheme,
    val ink: Color,
    val backdrop: Color,
    val palette: List<Color>
)

@Composable
internal fun resolveAppThemeColors(
    themeChoice: LucentThemeMode,
    systemDark: Boolean,
    pureWhite: Boolean,
    isDarkTheme: Boolean,
    dynamicColorOn: Boolean,
    paletteName: String,
    backgroundAnimated: Boolean,
    backgroundEnvironment: BackgroundEnvironment
): AppThemeColors {
    val context = LocalContext.current
    val dynamicActive = !pureWhite && dynamicColorOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors: ColorScheme
    val onGradient: Color
    val backdropColor: Color
    val paletteColors: List<Color>
    if (pureWhite) {
        val baseScheme = lightColorScheme()
        colors = pureWhiteScheme(baseScheme)
        onGradient = LucentGlass.PureWhiteInk
        backdropColor = LucentGlass.PureWhitePage
        paletteColors = listOf(LucentGlass.PureWhitePage, LucentGlass.PureWhiteSurface)
    } else if (dynamicActive) {
        val scheme = if (isDarkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        colors = scheme
        onGradient = scheme.onBackground
        backdropColor = scheme.background
        paletteColors = com.lucent.app.ui.dynamicBlobPalette(scheme)
    } else {
        onGradient = if (isDarkTheme) Color.White else Color(0xFF20202B)
        backdropColor = themeChoice.backdrop(systemDark)
        paletteColors = if (paletteName == com.lucent.app.ui.PALETTE_RANDOM) {
            com.lucent.app.ui.rememberRandomPaletteColors(
                animated = backgroundAnimated,
                environment = backgroundEnvironment
            )
        } else if (paletteName == PALETTE_CYCLE) {
            rememberCyclingPaletteColors(
                LucentPalette.pickerEntries.map { it.colors },
                animated = backgroundAnimated,
                environment = backgroundEnvironment
            )
        } else {
            LucentPalette.entries.firstOrNull { it.name == paletteName }?.colors
                ?: LucentPalette.SUNSET.colors
        }
        val baseScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
        colors = lucentScheme(baseScheme, paletteColors, onGradient, backdropColor)
    }

    return AppThemeColors(colors, onGradient, backdropColor, paletteColors)
}
