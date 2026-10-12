package com.lucent.app.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

object LucentMotion {

    val Settle = .86f to 420f
    val Firm = 1f to 700f
    val Control = .75f to 1500f
    val Sheet = .9f to 380f

    fun toSpring(pair: Pair<Float, Float>): FiniteAnimationSpec<Float> =
        spring(dampingRatio = pair.first, stiffness = pair.second)

    fun toColor(pair: Pair<Float, Float>): FiniteAnimationSpec<Color> =
        spring(dampingRatio = pair.first, stiffness = pair.second)

    fun toIntOffset(pair: Pair<Float, Float>): FiniteAnimationSpec<IntOffset> =
        spring(
            dampingRatio = pair.first,
            stiffness = pair.second,
            visibilityThreshold = IntOffset(1, 1)
        )

    fun toIntSize(pair: Pair<Float, Float>): FiniteAnimationSpec<IntSize> =
        spring(
            dampingRatio = pair.first,
            stiffness = pair.second,
            visibilityThreshold = IntSize(1, 1)
        )
}
