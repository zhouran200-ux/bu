package com.lucent.app.ui

import androidx.compose.ui.unit.TextUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LucentTitlesTest {

    private val sizes: List<TextUnit>
        get() = listOf(
            LucentTitles.ScreenSize,
            LucentTitles.EntrySize,
            LucentTitles.SectionSize,
            LucentTitles.ItemSize
        )

    @Test
    fun levelsDescendFromScreenToItem() {
        val values = sizes.map { it.value }
        assertTrue(values == values.sortedDescending(), "sizes must descend: $values")
        assertTrue(values.distinct().size == values.size, "sizes must differ: $values")
    }

    @Test
    fun everyStepStaysWithinOneThirdOfTheLevelAbove() {
        val values = sizes.map { it.value }
        for (i in 1 until values.size) {
            val ratio = values[i - 1] / values[i]
            assertTrue(ratio in 1.0..1.3, "step ${values[i - 1]} -> ${values[i]} is too wide ($ratio)")
        }
    }

    @Test
    fun scaleSitsInsideTheReadableBand() {
        for (size in sizes) {
            assertTrue(size.value in 12.0..24.0, "${size} is outside 12..24sp")
        }
    }

    @Test
    fun depthMapsToTheExpectedLevel() {
        assertEquals(LucentTitles.ScreenSize, LucentTitles.sizeForDepth(1))
        assertEquals(LucentTitles.EntrySize, LucentTitles.sizeForDepth(2))
        assertEquals(LucentTitles.SectionSize, LucentTitles.sizeForDepth(3))
        assertEquals(LucentTitles.ItemSize, LucentTitles.sizeForDepth(4))
    }

    @Test
    fun depthBeyondTheFourthLevelStopsShrinking() {
        for (depth in 4..9) {
            assertEquals(LucentTitles.ItemSize, LucentTitles.sizeForDepth(depth), "depth $depth")
        }
        assertEquals(LucentTitles.ScreenSize, LucentTitles.sizeForDepth(0))
    }
}
