package com.lucent.app.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles

internal val PROFILE_SLIDE_SPRING = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
internal val ROW_CARD_RADIUS = 14.dp
internal const val HELD_SCALE = 1.02f
internal const val HELD_ALPHA = 0.94f
internal const val REORDER_COMMIT_TIMEOUT_MS = 1200L
