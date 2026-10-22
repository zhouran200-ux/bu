package com.lucent.app.ui

import kotlin.math.abs
import kotlin.math.floor

internal data class DocumentRailTarget(val index: Int, val withinItem: Float)

internal class DocumentRailPosition(val itemCount: Int, chapters: List<Int>) {
    private val lastIndex = (itemCount - 1).coerceAtLeast(0)
    val chapterFractions = chapters.filter { it in 0 until itemCount }
        .distinct().sorted().map { fraction(it, 0, 1, 0) }

    fun fraction(index: Int, offset: Int, size: Int, spacing: Int): Float {
        if (lastIndex == 0) return 0f
        val within = offset.coerceAtLeast(0).toDouble() / (size.toDouble() + spacing).coerceAtLeast(1.0)
        return ((index + within.coerceIn(0.0, 1.0)) / lastIndex).toFloat().coerceIn(0f, 1f)
    }

    fun target(fraction: Float): DocumentRailTarget {
        val coordinate = fraction.finiteFraction() * lastIndex
        val index = floor(coordinate).toInt().coerceIn(0, lastIndex)
        return DocumentRailTarget(index, (coordinate - index).coerceIn(0f, 1f))
    }
}

internal data class DocumentRailDragSession(
    val position: DocumentRailPosition,
    val fraction: Float,
    val usable: Float,
    val rawFraction: Float = fraction,
    val pointerY: Float? = null,
    val snapped: Float? = null
) {
    fun start(y: Float): DocumentRailDragSession = copy(pointerY = y)

    fun move(y: Float): DocumentRailDragSession {
        val previous = pointerY ?: return this
        if (!y.isFinite()) return this
        val track = usable.coerceAtLeast(1f)
        val raw = (rawFraction + (y - previous) / track * 0.45f).finiteFraction()
        val enter = (6f / track).coerceAtMost(0.015f)
        val exit = enter * 2f
        val held = snapped?.takeIf { abs(raw - it) <= exit }
        val nearest = position.chapterFractions.minByOrNull { abs(raw - it) }
            ?.takeIf { abs(raw - it) <= enter }
        val snap = if (raw == 0f || raw == 1f) null else held ?: nearest
        return copy(fraction = snap ?: raw, rawFraction = raw, pointerY = y, snapped = snap)
    }
}

private fun Float.finiteFraction(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f
