package com.lucent.app

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeTabTest {

    @Test
    fun bottomBarReadsNotebooksSettings() {
        assertEquals(
            listOf(HomeTab.Notebooks, HomeTab.Settings),
            HomeTab.entries.toList()
        )
    }

    @Test
    fun screenMatchesTabDirectly() {
        assertEquals(HomeTab.Notebooks, HomeTab.of(Screen.Notebooks))
        assertEquals(HomeTab.Settings, HomeTab.of(Screen.Settings))
    }

    @Test
    fun tabReturnsCorrespondingScreen() {
        assertEquals(Screen.Notebooks, HomeTab.Notebooks.screen())
        assertEquals(Screen.Settings, HomeTab.Settings.screen())
    }

    @Test
    fun everyTabHasALabel() {
        HomeTab.entries.forEach { assertTrue(it.label.isNotBlank()) }
    }

    @Test
    fun navigationLabelsUseCompactMetricsRegardlessOfBodySpacing() {
        val base = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
            lineHeight = 48.sp,
            letterSpacing = 3.sp
        )
        val style = capsuleNavLabelStyle(base)

        assertEquals(11.sp, style.fontSize)
        assertEquals(14.sp, style.lineHeight)
        assertEquals(0.sp, style.letterSpacing)
        assertEquals(false, style.platformStyle?.paragraphStyle?.includeFontPadding)
        assertEquals(LineHeightStyle.Alignment.Center, style.lineHeightStyle?.alignment)
        assertEquals(LineHeightStyle.Trim.Both, style.lineHeightStyle?.trim)
        assertEquals(base.fontFamily, style.fontFamily)
        assertEquals(base.fontWeight, style.fontWeight)
    }
}
