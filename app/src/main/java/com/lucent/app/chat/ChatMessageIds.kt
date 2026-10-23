package com.lucent.app.chat

internal class ChatMessageIds(
    private val readHighWater: () -> Long,
    private val persistHighWater: (Long) -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var floor = 0L

    @Synchronized fun seed(databaseMaximum: Long) {
        floor = maxOf(floor, databaseMaximum, readHighWater())
    }

    @Synchronized fun reserve(count: Int): List<Long> {
        require(count > 0)
        val previous = maxOf(floor, readHighWater(), clock().coerceAtLeast(0L))
        check(previous <= Long.MAX_VALUE - count) { "Chat message ID space exhausted" }
        val high = previous + count
        check(persistHighWater(high)) { "Cannot persist chat message IDs" }
        floor = high
        return (1..count).map { previous + it }
    }
}
