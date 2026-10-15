package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class StepperSliderGestureTest {
    @Test
    fun downAndSubSlopJitterKeepWaiting() {
        listOf(0f to 0f, 3f to -5f, -5f to 3f, 10f to 10f).forEach { (x, y) ->
            assertEquals(StepperGestureDirection.Waiting, stepperGestureDirection(x, y, 10f))
        }
    }

    @Test
    fun clearHorizontalMovementInEitherDirectionClaimsSlider() {
        listOf(11f to 2f, -11f to -2f, 20f to 11f).forEach { (x, y) ->
            assertEquals(StepperGestureDirection.Horizontal, stepperGestureDirection(x, y, 10f))
        }
    }

    @Test
    fun verticalMovementInEitherDirectionRemainsAvailableForScrolling() {
        listOf(2f to 11f, -2f to -11f, 11f to 20f).forEach { (x, y) ->
            assertEquals(StepperGestureDirection.Vertical, stepperGestureDirection(x, y, 10f))
        }
    }

    @Test
    fun ambiguousDiagonalPastSlopFavorsScrolling() {
        listOf(11f to 11f, -11f to 11f, 11f to -11f, -11f to -11f).forEach { (x, y) ->
            assertEquals(StepperGestureDirection.Vertical, stepperGestureDirection(x, y, 10f))
        }
    }

    @Test
    fun displacementIsMeasuredFromDownRatherThanIndividualSmallDeltas() {
        assertEquals(StepperGestureDirection.Waiting, stepperGestureDirection(4f, 1f, 10f))
        assertEquals(StepperGestureDirection.Waiting, stepperGestureDirection(8f, 2f, 10f))
        assertEquals(StepperGestureDirection.Horizontal, stepperGestureDirection(12f, 3f, 10f))
    }
}
