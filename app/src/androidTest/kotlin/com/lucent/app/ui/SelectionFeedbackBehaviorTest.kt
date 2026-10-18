package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.ui.settings.ThemeModeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class SelectionFeedbackBehaviorTest {
    @get:Rule val rule = createComposeRule()
    private val selected = mutableStateOf(false)

    @Test fun darkWashAnimatesIntoAndOutOfSelection() = assertFade(dark = true)
    @Test fun lightWashAnimatesIntoAndOutOfSelection() = assertFade(dark = false)
    @Test fun disabledMotionSnapsBothDirectionsInDarkGlass() = assertSnap(dark = true)
    @Test fun disabledMotionSnapsBothDirectionsInLightGlass() = assertSnap(dark = false)

    private fun renderCard(dark: Boolean, motion: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color.White)) {
                CompositionLocalProvider(
                    LocalOnGradient provides if (dark) Color.White else Color.Black,
                    LocalBackgroundEnvironment provides BackgroundEnvironment(motionEnabled = motion)
                ) {
                    Box(Modifier.size(80.dp).background(Color.Black)) {
                        Box(Modifier.size(80.dp).testTag("card").selectedCard(selected.value, 12.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun changeSelection(value: Boolean) {
        rule.runOnIdle { selected.value = value }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    private fun centerByte(): Int {
        val pixels = rule.onNodeWithTag("card").captureToImage().toPixelMap()
        val color = pixels[pixels.width / 2, pixels.height / 2]
        val red = (color.red * 255).roundToInt()
        assertEquals("Neutral wash must not acquire a green cast", red, (color.green * 255).roundToInt())
        assertEquals("Neutral wash must not acquire a blue cast", red, (color.blue * 255).roundToInt())
        assertEquals("Opaque backing must remain opaque", 1f, color.alpha, 0f)
        return red
    }

    private fun assertEndpoint(expected: Int) {
        val actual = centerByte()
        assertTrue("White wash on opaque black: expected sRGB byte $expected ±1 quantization step, got $actual",
            abs(actual - expected) <= 1)
    }

    private fun assertFade(dark: Boolean) {
        renderCard(dark, motion = true)
        val finalByte = if (dark) 33 else 23
        assertEndpoint(0)
        changeSelection(true)
        assertIntermediateFrames(finalByte, entering = true)
        rule.mainClock.advanceTimeBy(200)
        assertEndpoint(finalByte)

        changeSelection(false)
        assertIntermediateFrames(finalByte, entering = false)
        rule.mainClock.advanceTimeBy(200)
        assertEndpoint(0)
    }

    private fun assertIntermediateFrames(finalByte: Int, entering: Boolean) {
        val samples = List(4) {
            rule.mainClock.advanceTimeBy(32)
            centerByte()
        }
        assertTrue("Must render an intermediate wash (entering=$entering): $samples",
            samples.any { it in 2 until finalByte - 1 })
        assertTrue("Wash must progress in the requested direction: $samples",
            samples.zipWithNext().all { (a, b) -> if (entering) a <= b else a >= b })
    }

    private fun assertSnap(dark: Boolean) {
        renderCard(dark, motion = false)
        assertEndpoint(0)
        changeSelection(true)
        assertEndpoint(if (dark) 33 else 23)
        changeSelection(false)
        assertEndpoint(0)
    }

    @Test fun rowSemanticsAndRadioTouchEachDispatchExactlyOneSelectionAndLockedRowDispatchesNone() {
        val savedTheme = mutableStateOf(LucentThemeMode.DARK.key)
        val locked = mutableStateOf(false)
        var callbacks = 0
        rule.setContent {
            MaterialTheme {
                ThemeModeRow(LucentThemeMode.LIGHT, savedTheme.value, locked.value) {
                    callbacks++
                    savedTheme.value = LucentThemeMode.LIGHT.key
                }
            }
        }
        val row = rule.onNodeWithTag("theme-mode-light")
        val radio = rule.onNodeWithTag("theme-radio-light", useUnmergedTree = true)
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsNotSelected()
        radio.assertHasNoClickAction()
        row.performClick()
        rule.runOnIdle { assertEquals(1, callbacks) }
        row.assertIsSelected()

        rule.runOnIdle { savedTheme.value = LucentThemeMode.DARK.key }
        row.assertIsNotSelected()
        radio.performTouchInput { click() }
        rule.runOnIdle { assertEquals(2, callbacks) }
        row.assertIsSelected()

        row.performClick()
        rule.runOnIdle {
            assertEquals(3, callbacks)
            locked.value = true
        }
        row.assertIsNotEnabled()
        row.performTouchInput { click() }
        radio.performTouchInput { click() }
        rule.runOnIdle { assertEquals(3, callbacks) }
    }
}
