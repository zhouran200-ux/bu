package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepperSliderInteractionTest {
    @get:Rule val rule = createComposeRule()
    private val value = mutableFloatStateOf(5f)
    private val changes = mutableListOf<Float>()
    private var finishes = 0
    private var scroll: androidx.compose.foundation.ScrollState? = null

    private fun render() {
        rule.setContent {
            val state = rememberScrollState()
            scroll = state
            Column(Modifier.fillMaxSize().verticalScroll(state)) {
                Spacer(Modifier.height(100.dp))
                StepperSlider("test slider", value.floatValue, 0f..10f, 1f, "value", {
                    changes += it
                    value.floatValue = it
                }, onValueChangeFinished = { finishes++ })
                Spacer(Modifier.height(2000.dp))
            }
        }
    }

    @Test fun pressingWithoutDirectionDoesNotChangeValueAndTapCommitsOneSnappedValue() {
        render()
        val track = rule.onNodeWithContentDescription("test slider value")
        track.performTouchInput { down(Offset(width * 0.75f, centerY)) }
        rule.runOnIdle {
            assertEquals(5f, value.floatValue)
            assertTrue(changes.isEmpty())
            assertEquals(0, finishes)
        }
        track.performTouchInput { up() }
        rule.runOnIdle {
            assertTrue(value.floatValue in 7f..8f)
            assertEquals(1, changes.size)
            assertEquals(1, finishes)
            assertEquals(value.floatValue, value.floatValue.toInt().toFloat())
        }
    }

    @Test fun verticalMovementScrollsParentWithoutChangingSlider() {
        render()
        rule.onNodeWithContentDescription("test slider value").performTouchInput {
            swipe(Offset(width * 0.6f, centerY), Offset(width * 0.6f, centerY - 90f), 500)
        }
        rule.runOnIdle {
            assertEquals(5f, value.floatValue)
            assertTrue(changes.isEmpty())
            assertEquals(0, finishes)
            assertTrue(scroll!!.value > 0)
        }
    }

    @Test fun horizontalDragCommitsAndCancelledDragRestoresOriginalWithoutFinish() {
        render()
        val track = rule.onNodeWithContentDescription("test slider value")
        track.performTouchInput { swipe(Offset(width * 0.3f, centerY), Offset(width * 0.85f, centerY), 500) }
        rule.runOnIdle {
            assertTrue(value.floatValue >= 8f)
            assertEquals(1, finishes)
            assertEquals(0, scroll!!.value)
            changes.clear()
            value.floatValue = 5f
        }
        track.performTouchInput {
            down(Offset(width * 0.5f, centerY))
            moveTo(Offset(width * 0.8f, centerY), 300)
        }
        rule.runOnIdle { assertTrue(value.floatValue > 5f) }
        track.performTouchInput { cancel() }
        rule.runOnIdle {
            assertEquals(5f, value.floatValue)
            assertEquals(5f, changes.last())
            assertEquals(1, finishes)
        }
    }
}
