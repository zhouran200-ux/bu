package com.lucent.app.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles

internal fun modelChoices(fetched: List<String>, saved: List<String>): List<String> {
    val out = LinkedHashSet<String>()
    fetched.forEach { if (it.isNotBlank()) out.add(it.trim()) }
    saved.sorted().forEach { if (it.isNotBlank()) out.add(it.trim()) }
    return out.toList()
}

internal const val PROFILES_PER_PAGE = 10

internal fun profilePageCount(profileCount: Int): Int =
    ((profileCount + PROFILES_PER_PAGE - 1) / PROFILES_PER_PAGE).coerceAtLeast(1)

internal fun profilePageRange(index: Int, lastIndex: Int): IntRange {
    val start = (index / PROFILES_PER_PAGE) * PROFILES_PER_PAGE
    return start..minOf(start + PROFILES_PER_PAGE - 1, lastIndex)
}

internal val PROFILE_SLIDE_SPRING = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
internal val ROW_CARD_RADIUS = 14.dp
internal const val HELD_SCALE = 1.02f
internal const val HELD_ALPHA = 0.94f

internal data class ProfileDrag(
    val order: List<ApiProfile>,
    val keys: List<String>,
    val selected: Int,
    val held: Int,
    val travel: Float
)

internal fun ProfileDrag.stepped(step: Int, range: IntRange = order.indices): ProfileDrag {
    val to = held + step
    if (held !in range || to !in range) return this
    return ProfileDrag(
        order = ApiProfiles.moved(order, held, to),
        keys = ApiProfiles.moved(keys, held, to),
        selected = ApiProfiles.selectionAfterMove(selected, held, to),
        held = to,
        travel = travel
    )
}

