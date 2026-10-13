package com.lucent.app.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaletteLibraryTest {

    @Test
    fun themePickerOffersExactly18Tints() {
        val tints = LucentThemeMode.pickerEntries.filter {
            it != LucentThemeMode.SYSTEM && it != LucentThemeMode.LIGHT && it != LucentThemeMode.DARK
        }
        assertEquals(18, tints.size)
        val light = tints.map { it.backdrop(systemDark = false).toArgb() }
        assertEquals(light.size, light.toSet().size)
        val dark = tints.map { it.backdrop(systemDark = true).toArgb() }
        assertEquals(dark.size, dark.toSet().size)
    }

    @Test
    fun backgroundPickerOffersExactly36UniqueBoards() {
        val boards = LucentPalette.pickerEntries
        assertEquals(36, boards.size)
        assertEquals(boards.size, boards.map { it.name }.toSet().size)
        val hexes = boards.flatMap { it.colors.map { c -> c.toArgb() } }
        assertEquals(hexes.size, hexes.toSet().size, "duplicated hex in offered boards")
        assertEquals(42, LucentPalette.entries.size)
    }

    @Test
    fun everyThemeAgreesWithItsOwnBackdropLuminance() {
        for (mode in LucentThemeMode.entries) {
            if (mode == LucentThemeMode.SYSTEM) continue
            for (systemDark in listOf(false, true)) {
                val expected = mode.backdrop(systemDark).luminance() < 0.5f
                assertEquals(expected, mode.isDark(systemDark), "${mode.key} disagrees with its backdrop")
            }
        }
    }

    @Test
    fun systemThemeFollowsThePlatformFlag() {
        assertTrue(LucentThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(LucentThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun anchorThemesKeepTheirKnownDarkness() {
        assertTrue(LucentThemeMode.DARK.isDark(systemDark = false))
        assertFalse(LucentThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(LucentThemeMode.MONET_NIGHT.isDark(systemDark = false))
        assertTrue(LucentThemeMode.MONET_ABYSS.isDark(systemDark = false))
        assertFalse(LucentThemeMode.MONET_WHEAT.isDark(systemDark = true))
        assertFalse(LucentThemeMode.MONET_SNOWDROP.isDark(systemDark = true))
    }

    @Test
    fun themeSectionsSplitOnTheirOwnDarkness() {
        for (mode in LucentThemeMode.entries) {
            if (mode == LucentThemeMode.SYSTEM) continue
            val expected = if (mode.isDark(systemDark = false)) {
                LucentThemeSection.DARK
            } else {
                LucentThemeSection.LIGHT
            }
            assertEquals(expected, mode.section, "${mode.key} lands in the wrong settings group")
        }
    }

    @Test
    fun everyPaletteAccentStaysReadableOnBothPanels() {
        val darkPanel = panelOver(LucentThemeMode.DARK_BACKDROP)
        val lightPanel = panelOver(LucentThemeMode.LIGHT_BACKDROP)
        for (palette in LucentPalette.entries) {
            val onDark = lucentAccent(palette.colors, Color.White)
            val onLight = lucentAccent(palette.colors, Color(0xFF20202B))
            assertTrue(
                contrastRatio(onDark, darkPanel) >= 4.5f,
                "${palette.name} dark accent is too dim on the dark panel"
            )
            assertTrue(
                contrastRatio(onLight, lightPanel) >= 4.5f,
                "${palette.name} light accent is too pale on the light panel"
            )
        }
    }

    @Test
    fun accentFlipsItsLightnessWithTheInk() {
        val board = LucentPalette.SUNSET.colors
        assertTrue(lucentAccent(board, Color.White).luminance() > 0.5f)
        assertTrue(lucentAccent(board, Color(0xFF20202B)).luminance() < 0.25f)
        assertEquals(Color(0xFF20202B), lucentAccent(emptyList(), Color(0xFF20202B)))
    }

    @Test
    fun accentChangesWhenThePaletteChanges() {
        val ink = Color(0xFF20202B)
        val sunset = lucentAccent(LucentPalette.SUNSET.colors, ink)
        val ocean = lucentAccent(LucentPalette.OCEAN.colors, ink)
        val forest = lucentAccent(LucentPalette.FOREST.colors, ink)
        assertTrue(sunset != ocean && ocean != forest)
    }

    @Test
    fun schemePaintsSurfaceFromBackdropAndKeepsErrorIndependent() {
        val ink = Color.White
        val scheme = lucentScheme(
            base = darkColorScheme(),
            palette = LucentPalette.FOREST.colors,
            ink = ink,
            backdrop = LucentThemeMode.DARK_BACKDROP
        )
        assertEquals(panelOver(LucentThemeMode.DARK_BACKDROP), scheme.surface)
        assertEquals(LucentThemeMode.DARK_BACKDROP, scheme.background)
        assertEquals(ink, scheme.onSurface)
        assertEquals(scheme.primary, scheme.secondary)
        assertTrue(scheme.error != scheme.primary, "error must stay its own semantic colour")
    }

    @Test
    fun pureWhiteFillsStayAchromaticAndOpaque() {
        val scheme = pureWhiteScheme(darkColorScheme())
        val fills = listOf(
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.background,
            scheme.surface,
            scheme.primaryContainer,
            scheme.secondaryContainer,
            scheme.tertiaryContainer,
            scheme.surfaceVariant,
            scheme.surfaceDim,
            scheme.surfaceBright,
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest
        )
        for (fill in fills) {
            assertEquals(1f, fill.alpha, "pure-white surfaces must not be translucent")
            val spread = maxOf(fill.red, maxOf(fill.green, fill.blue)) -
                minOf(fill.red, minOf(fill.green, fill.blue))
            assertTrue(spread < 0.05f, "pure-white surfaces must stay achromatic")
        }
        assertEquals(LucentGlass.PureWhitePage, scheme.background)
        assertEquals(LucentGlass.PureWhiteSurface, scheme.surface)
        assertEquals(LucentGlass.PureWhiteInk, scheme.onSurface)
    }

    @Test
    fun pureWhiteIsDrivenByItsToggleInsteadOfTheThemePicker() {
        assertFalse(LucentThemeMode.pickerEntries.contains(LucentThemeMode.PURE_WHITE))
        assertEquals(LucentThemeMode.PURE_WHITE, LucentThemeMode.fromKey("pure_white"))
        assertFalse(LucentThemeMode.PURE_WHITE.isDark(systemDark = true))
        assertEquals(listOf(Color(0xFFFFFFFF), Color(0xFFD9D9D9)), LucentThemeMode.PURE_WHITE.swatch())
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val high = maxOf(first.luminance(), second.luminance())
        val low = minOf(first.luminance(), second.luminance())
        return (high + 0.05f) / (low + 0.05f)
    }
}
