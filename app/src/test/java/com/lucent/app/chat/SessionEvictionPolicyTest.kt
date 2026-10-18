package com.lucent.app.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionEvictionPolicyTest {

    @Test
    fun keepsEverySessionWhileWithinTheLimit() {
        val access = linkedMapOf(1L to 10L, 2L to 20L, 3L to 30L)
        assertEquals(emptyList<Long>(), evictionOrder(access, 3))
        assertEquals(emptyList<Long>(), evictionOrder(access, 5))
    }

    @Test
    fun evictsLeastRecentlyAccessedSessionsFirst() {
        val access = linkedMapOf(1L to 10L, 2L to 30L, 3L to 20L, 4L to 40L)
        assertEquals(listOf(1L, 3L), evictionOrder(access, 2))
    }

    @Test
    fun zeroLimitEvictsEverySessionInAccessOrder() {
        val access = linkedMapOf(5L to 50L, 6L to 60L)
        assertEquals(listOf(5L, 6L), evictionOrder(access, 0))
    }

    @Test
    fun earlierAccessWinsWhenTimestampsTie() {
        val access = linkedMapOf(9L to 7L, 8L to 7L, 7L to 1L)
        assertEquals(listOf(7L, 9L), evictionOrder(access, 1))
    }
}
