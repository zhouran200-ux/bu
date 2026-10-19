package com.lucent.app.ui

import kotlin.math.roundToLong

internal class ChatRailGeometry(sizes: List<Int>, private val viewport: Int, private val spacing: Int) {
    private val heights = sizes.map { it.coerceAtLeast(1) }
    private val starts = LongArray(heights.size)
    init {
        for (index in 1 until heights.size) starts[index] = starts[index - 1] + heights[index - 1] + spacing
    }
    private val extent = if (heights.isEmpty()) 0L else starts.last() + heights.last()
    private val maximum = (extent - viewport).coerceAtLeast(0L)

    fun fraction(index: Int, offset: Int, canBack: Boolean, canForward: Boolean): Float = when {
        !canBack -> 0f
        !canForward -> 1f
        maximum == 0L || index !in starts.indices -> 0f
        else -> ((starts[index] + offset).toDouble() / maximum).toFloat().coerceIn(0f, 1f)
    }

    fun target(fraction: Float): ChatScrollTarget {
        if (starts.isEmpty()) return ChatScrollTarget(0, 0)
        return targetAt((fraction.coerceIn(0f, 1f).toDouble() * maximum).roundToLong())
    }

    fun targetFrom(anchor: ChatScrollTarget, deltaFraction: Float): ChatScrollTarget {
        if (deltaFraction == 0f) return anchor
        val start = starts.getOrNull(anchor.index) ?: return target(0f)
        val distance = start + anchor.offset + deltaFraction.toDouble() * maximum
        return targetAt(distance.roundToLong().coerceAtLeast(0L))
    }

    private fun targetAt(distance: Long): ChatScrollTarget {
        if (starts.isEmpty()) return ChatScrollTarget(0, 0)
        var low = 0
        var high = starts.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (starts[middle] <= distance) low = middle else high = middle - 1
        }
        return ChatScrollTarget(low, (distance - starts[low]).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}

internal fun railDragFraction(start: Float, delta: Float, usable: Float): Float =
    (start + delta / usable.coerceAtLeast(1f)).coerceIn(0f, 1f)
