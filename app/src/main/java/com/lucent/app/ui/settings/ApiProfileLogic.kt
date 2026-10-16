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

internal val PROFILE_SLIDE_SPRING = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
internal val ROW_CARD_RADIUS = 14.dp
internal const val HELD_SCALE = 1.02f
internal const val HELD_ALPHA = 0.94f
internal const val REORDER_COMMIT_TIMEOUT_MS = 1200L

private val CompactFieldHeight = 42.dp
private val CompactFieldLabelWidth = 56.dp

internal data class ProfileDrag(
    val order: List<ApiProfile>,
    val keys: List<String>,
    val selected: Int,
    val held: Int,
    val travel: Float
)

internal fun ProfileDrag.stepped(step: Int): ProfileDrag {
    val to = held + step
    if (held !in order.indices || to !in order.indices) return this
    return ProfileDrag(
        order = ApiProfiles.moved(order, held, to),
        keys = ApiProfiles.moved(keys, held, to),
        selected = ApiProfiles.selectionAfterMove(selected, held, to),
        held = to,
        travel = travel
    )
}

