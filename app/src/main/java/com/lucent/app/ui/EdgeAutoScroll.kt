package com.lucent.app.ui

internal fun edgeScrollVelocity(pointer: Float, start: Float, end: Float, zone: Float, maxSpeed: Float): Float {
    if (!pointer.isFinite() || !start.isFinite() || !end.isFinite() || end <= start || !zone.isFinite() || !maxSpeed.isFinite() || zone <= 0f || maxSpeed <= 0f) return 0f
    val band = zone.coerceAtMost((end - start) / 2f)
    val top = ((start + band - pointer) / band).coerceIn(0f, 1f)
    val bottom = ((pointer - end + band) / band).coerceIn(0f, 1f)
    return (bottom * bottom - top * top) * maxSpeed
}

