package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatViewportAnchorTest {
    @Test fun resizeKeepsBottomAcrossKeyboardAnimationFrames() {
        val anchor = ChatViewportAnchor()
        assertFalse(anchor.update(1000, true))
        assertTrue(anchor.update(900, false))
        assertTrue(anchor.update(700, false))
        assertTrue(anchor.update(500, false))
        assertFalse(anchor.update(500, true))
    }
    @Test fun readingHistoryIsNotForcedToBottom() {
        val anchor = ChatViewportAnchor()
        anchor.update(1000, false)
        assertFalse(anchor.update(700, false))
        assertFalse(anchor.update(500, false))
    }
    @Test fun userScrollCancelsResizeFollowing() {
        val anchor = ChatViewportAnchor()
        anchor.update(1000, true)
        assertTrue(anchor.update(900, false))
        anchor.userScrolled()
        assertFalse(anchor.update(700, false))
    }
    @Test fun initialAndExpandedViewportDoNotJump() {
        val anchor = ChatViewportAnchor()
        assertFalse(anchor.update(0, true))
        assertFalse(anchor.update(500, true))
        assertFalse(anchor.update(1000, false))
        assertFalse(anchor.update(700, false))
    }
}
