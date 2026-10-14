package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatScrollMathTest {

    @Test
    fun emptyListIsNotScrollable() {
        val m = ChatScrollMath.metrics(
            totalItems = 0, averageItemSize = 0, spacing = 0,
            viewport = 1000, leadIndex = 0, leadOffset = 0
        )
        assertFalse(m.scrollable)
        assertEquals(0f, m.fraction)
    }

    @Test
    fun shortContentThatFitsIsNotScrollable() {
        val m = ChatScrollMath.metrics(
            totalItems = 2, averageItemSize = 100, spacing = 10,
            viewport = 1000, leadIndex = 0, leadOffset = 0
        )
        assertFalse(m.scrollable)
    }

    @Test
    fun atTopFractionIsZeroAtBottomApproachesOne() {
        val top = ChatScrollMath.metrics(
            totalItems = 50, averageItemSize = 200, spacing = 0,
            viewport = 1000, leadIndex = 0, leadOffset = 0
        )
        assertTrue(top.scrollable)
        assertEquals(0f, top.fraction)

        val bottom = ChatScrollMath.metrics(
            totalItems = 50, averageItemSize = 200, spacing = 0,
            viewport = 1000, leadIndex = 45, leadOffset = 0
        )
        assertEquals(1f, bottom.fraction)
    }

    @Test
    fun thumbShrinksWithLongerContentButHasFloor() {
        val longDoc = ChatScrollMath.metrics(
            totalItems = 1000, averageItemSize = 200, spacing = 0,
            viewport = 1000, leadIndex = 0, leadOffset = 0
        )
        assertEquals(0.08f, longDoc.thumbFraction)
    }

    @Test
    fun targetAtZeroIsFirstItem() {
        val t = ChatScrollMath.targetForFraction(
            totalItems = 50, averageItemSize = 200, spacing = 0,
            viewport = 1000, fraction = 0f
        )
        assertEquals(0, t.index)
        assertEquals(0, t.offset)
    }

    @Test
    fun targetAtOneLandsNearBottomWithinBounds() {
        val total = 50
        val t = ChatScrollMath.targetForFraction(
            totalItems = total, averageItemSize = 200, spacing = 0,
            viewport = 1000, fraction = 1f
        )
        assertTrue(t.index in 0 until total)
        assertTrue(t.offset >= 0)
    }

    @Test
    fun targetFractionIsClampedForOutOfRangeInput() {
        val low = ChatScrollMath.targetForFraction(50, 200, 0, 1000, -5f)
        assertEquals(0, low.index)
        val high = ChatScrollMath.targetForFraction(50, 200, 0, 1000, 9f)
        assertTrue(high.index in 0 until 50)
    }

    @Test
    fun programmaticScrollDoesNotChangeFollowState() {
        assertTrue(
            ChatScrollMath.shouldFollowAfterScroll(
                wasFollowing = true, atBottom = true, userScrolling = false
            )
        )
        assertFalse(
            ChatScrollMath.shouldFollowAfterScroll(
                wasFollowing = false, atBottom = true, userScrolling = false
            )
        )
    }

    @Test
    fun userScrollUpStopsFollowingUserScrollToBottomResumes() {
        assertFalse(
            ChatScrollMath.shouldFollowAfterScroll(
                wasFollowing = true, atBottom = false, userScrolling = true
            )
        )
        assertTrue(
            ChatScrollMath.shouldFollowAfterScroll(
                wasFollowing = false, atBottom = true, userScrolling = true
            )
        )
    }

    @Test
    fun pausedReadingStaysPausedAfterReleaseAndIncomingLayoutChanges() {
        assertFalse(ChatScrollMath.shouldFollowAfterScroll(false, false, true))
        assertFalse(ChatScrollMath.shouldFollowAfterScroll(false, false, false))
        assertFalse(ChatScrollMath.shouldFollowAfterScroll(false, true, false))
        assertTrue(ChatScrollMath.shouldFollowAfterScroll(false, true, true))
    }
}
