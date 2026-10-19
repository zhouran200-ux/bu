package com.lucent.app.ui

internal data class ChatRailDragSession(
    val geometry: ChatRailGeometry,
    val startFraction: Float,
    val anchor: ChatScrollTarget,
    val usable: Float,
    val fraction: Float = startFraction,
    val originY: Float? = null
) {
    fun start(pointerY: Float): ChatRailDragSession = copy(originY = pointerY)

    fun move(pointerY: Float): ChatRailDragSession {
        val origin = originY ?: return this
        return copy(fraction = railDragFraction(startFraction, pointerY - origin, usable))
    }

    fun thumbTop(available: Float): Float =
        (fraction * usable).coerceIn(0f, available.coerceAtLeast(0f))

    fun target(): ChatScrollTarget = geometry.targetFrom(anchor, fraction - startFraction)
}
