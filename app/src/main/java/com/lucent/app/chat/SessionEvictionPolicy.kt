package com.lucent.app.chat

internal fun evictionOrder(accessTimes: Map<Long, Long>, keep: Int): List<Long> {
    val excess = accessTimes.size - keep
    if (excess <= 0) return emptyList()
    return accessTimes.entries.sortedBy { it.value }.take(excess).map { it.key }
}
