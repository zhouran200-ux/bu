package com.lucent.app.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.IntOffset

object LucentMotion {

    val Settle = .86f to 420f
    val Quick = .8f to 700f
    val Firm = 1f to 700f
    val Appear = .78f to 400f
    val Control = .75f to 1500f
    val Snap = 1f to 1500f
    val Sheet = .9f to 380f
    val Menu = .78f to 650f
    val Bounce = .7f to 480f

    fun toSpring(pair: Pair<Float, Float>): FiniteAnimationSpec<Float> =
        androidx.compose.animation.core.spring(dampingRatio = pair.first, stiffness = pair.second)

    fun toIntOffset(pair: Pair<Float, Float>): FiniteAnimationSpec<IntOffset> =
        androidx.compose.animation.core.spring(
            dampingRatio = pair.first,
            stiffness = pair.second,
            visibilityThreshold = IntOffset(1, 1)
        )

    fun reduced(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
