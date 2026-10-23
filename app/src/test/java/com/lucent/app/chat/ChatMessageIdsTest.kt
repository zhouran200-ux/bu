package com.lucent.app.chat

import org.junit.Assert.*
import org.junit.Test

class ChatMessageIdsTest {
    @Test fun frozenAndRolledBackClockAndRestartNeverReuseReservedIds() {
        var persisted = 0L
        var clock = 100L
        fun allocator() = ChatMessageIds({ persisted }, { persisted = it; true }, { clock })
        val ids = allocator()
        ids.seed(500L)
        val firstConversation = ids.reserve(2)
        val secondConversation = ids.reserve(2)
        clock = 1L
        val rolledBack = ids.reserve(2)
        val restarted = allocator().apply { seed(500L) }.reserve(2)
        assertEquals((501L..508L).toList(), firstConversation + secondConversation + rolledBack + restarted)
        assertEquals(508L, persisted)
    }

    @Test fun failedDurableReservationExposesNoIds() {
        var writable = false
        var persisted = 0L
        val ids = ChatMessageIds({ persisted }, { if (writable) { persisted = it; true } else false }, { 0L })
        assertThrows(IllegalStateException::class.java) { ids.reserve(2) }
        assertEquals(0L, persisted)
        writable = true
        assertEquals(listOf(1L, 2L), ids.reserve(2))
    }

    @Test fun exhaustionDoesNotWrapIntoExistingIds() {
        val ids = ChatMessageIds({ Long.MAX_VALUE - 1 }, { error("must not persist") }, { 0L })
        assertThrows(IllegalStateException::class.java) { ids.reserve(2) }
    }
}
