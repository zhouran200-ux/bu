package com.lucent.app.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

class ReorderSlots internal constructor(
    private val keys: List<Long>,
    private val offsets: List<IntOffset>,
    private val sizes: List<IntSize>,
    val viewportLeft: Int,
    val viewportTop: Int,
    val viewportRight: Int,
    val viewportBottom: Int
) {
    val count: Int get() = keys.size

    fun indexOfKey(key: Long?): Int = if (key == null) -1 else keys.indexOf(key)

    fun boundsAt(index: Int): Pair<IntOffset, IntSize>? =
        if (index in keys.indices) offsets[index] to sizes[index] else null

    fun offsetOf(key: Long?): IntOffset? = offsets.getOrNull(indexOfKey(key))

    fun sizeOf(key: Long?): IntSize? = sizes.getOrNull(indexOfKey(key))

    fun visibleKeys(): List<Long> = keys

    internal fun gapAtY(y: Float, dragged: Long): Pair<Long?, Long?> {
        var before: Long? = null
        for (index in keys.indices) {
            if (keys[index] == dragged) continue
            val mid = offsets[index].y + sizes[index].height / 2f
            if (y <= mid) return before to keys[index]
            before = keys[index]
        }
        return before to null
    }

    fun slotFor(dragging: Long?, gapBeforeId: Long?, gapAfterId: Long?): Pair<IntOffset, IntSize>? =
        boundsAt(landingIndex(dragging, gapBeforeId, gapAfterId))

    fun targetFor(key: Long, dragging: Long?, gapBeforeId: Long?, gapAfterId: Long?): IntOffset? {
        val me = indexOfKey(key)
        if (me < 0) return null
        if (dragging == null) return offsets[me]
        if (key == dragging) return slotFor(dragging, gapBeforeId, gapAfterId)?.first ?: offsets[me]
        val reflow = reflowFor(key, dragging, gapBeforeId, gapAfterId)
        return IntOffset(offsets[me].x + reflow.x, offsets[me].y + reflow.y)
    }

    fun reflowFor(key: Long, dragging: Long?, gapBeforeId: Long?, gapAfterId: Long?): IntOffset {
        if (dragging == null) return IntOffset.Zero
        val after = indexOfKey(gapAfterId)
        val before = indexOfKey(gapBeforeId)
        if (after < 0 && before < 0) return IntOffset.Zero
        val target = if (after >= 0) after else before + 1
        val from = indexOfKey(dragging)
        val me = indexOfKey(key)
        if (from < 0 || me < 0 || me == from) return IntOffset.Zero
        val neighbour = when {
            from < target && me in (from + 1) until target -> me - 1
            target < from && me in target until from -> me + 1
            else -> return IntOffset.Zero
        }
        val destination = boundsAt(neighbour)?.first ?: return IntOffset.Zero
        return destination - offsets[me]
    }

    private fun landingIndex(dragging: Long?, gapBeforeId: Long?, gapAfterId: Long?): Int {
        if (dragging == null) return -1
        val after = indexOfKey(gapAfterId)
        val before = indexOfKey(gapBeforeId)
        if (after < 0 && before < 0) return -1
        val target = if (after >= 0) after else before + 1
        val from = indexOfKey(dragging)
        if (from < 0 || target == from) return -1
        val landing = if (from < target) target - 1 else target
        return if (landing in keys.indices) landing else -1
    }
}

fun <T> reorderedAround(
    current: List<T>,
    moving: List<T>,
    beforeId: Long?,
    afterId: Long?,
    idOf: (T) -> Long
): List<T> {
    if (moving.isEmpty()) return current
    val movingIds = moving.map(idOf).toHashSet()
    val remainder = current.filterNot { idOf(it) in movingIds }
    val at = when {
        afterId != null && afterId !in movingIds -> remainder.indexOfFirst { idOf(it) == afterId }
        beforeId != null && beforeId !in movingIds -> remainder.indexOfFirst { idOf(it) == beforeId } + 1
        else -> -1
    }
    if (at < 0 || at > remainder.size) return current
    return remainder.toMutableList().also { it.addAll(at, moving) }
}
