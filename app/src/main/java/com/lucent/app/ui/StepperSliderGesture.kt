package com.lucent.app.ui

import kotlin.math.abs

internal enum class StepperGestureDirection { Waiting, Horizontal, Vertical }

internal fun stepperGestureDirection(
    deltaX: Float,
    deltaY: Float,
    touchSlop: Float
): StepperGestureDirection {
    val horizontal = abs(deltaX)
    val vertical = abs(deltaY)
    return when {
        vertical > touchSlop && vertical >= horizontal -> StepperGestureDirection.Vertical
        horizontal > touchSlop -> StepperGestureDirection.Horizontal
        else -> StepperGestureDirection.Waiting
    }
}
