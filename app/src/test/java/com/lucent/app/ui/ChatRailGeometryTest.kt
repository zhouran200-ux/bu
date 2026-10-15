package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatRailGeometryTest {
    @Test fun targetUsesEachMessagesHeightInsteadOfVisibleAverage() {
        val geometry = ChatRailGeometry(listOf(10, 2000, 100, 10), 500, 0)
        assertEquals(ChatScrollTarget(0, 0), geometry.target(0f))
        assertEquals(ChatScrollTarget(1, 800), geometry.target(0.5f))
        assertEquals(ChatScrollTarget(1, 1610), geometry.target(1f))
    }
    @Test fun longMessageOffsetContributesToPosition() {
        val geometry = ChatRailGeometry(listOf(10, 2000, 100, 10), 500, 0)
        assertEquals(0.5f, geometry.fraction(1, 800, true, true), 0.001f)
        assertEquals(0f, geometry.fraction(1, 900, false, true))
        assertEquals(1f, geometry.fraction(1, 900, true, false))
    }
    @Test fun dragDoesNotJumpOnGrabAndHasStableEndpoints() {
        assertEquals(0.6f, railDragFraction(0.6f, 0f, 600f))
        assertEquals(0.7f, railDragFraction(0.6f, 60f, 600f), 0.001f)
        assertEquals(0f, railDragFraction(0.6f, -9999f, 600f))
        assertEquals(1f, railDragFraction(0.6f, 9999f, 600f))
    }
    @Test fun emptyShortAndExtremeGeometryRemainSafe() {
        assertEquals(ChatScrollTarget(0, 0), ChatRailGeometry(emptyList(), 500, 0).target(1f))
        assertEquals(ChatScrollTarget(0, 0), ChatRailGeometry(listOf(10), 500, 0).target(1f))
        val huge = ChatRailGeometry(listOf(Int.MAX_VALUE, Int.MAX_VALUE), 500, 16)
        assertTrue(huge.target(0.9f).offset >= 0)
    }
}
