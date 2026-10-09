package com.lucent.app.ui

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
}
