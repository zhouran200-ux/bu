package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatScrollMathTest {

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
