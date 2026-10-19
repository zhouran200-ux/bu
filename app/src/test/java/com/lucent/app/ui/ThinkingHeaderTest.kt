package com.lucent.app.ui

import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingHeaderTest {
    @Test fun completedReplyWithoutThoughtKeepsUnsupportedTitle() {
        assertEquals(S.thinkingUnsupported, thinkingTitle(false, false, 0))
        assertEquals(S.thinkingUnsupported, thinkingTitle(false, false, 18))
        assertEquals(S.thinkingStreaming, thinkingTitle(true, false, 0))
        assertEquals(S.thinkingDoneSeconds(18), thinkingTitle(false, true, 18))
        assertEquals(S.thinkingDone, thinkingTitle(false, true, 0))
    }

    @Test fun firstStreamOpensAnUntouchedEmptySlotButRespectsExplicitChoices() {
        val untouched = ThinkingLaneState(null, streaming = false)
        assertFalse(untouched.expanded)
        untouched.retainStreamingHeight(true)
        assertTrue(untouched.expanded)
        untouched.retainStreamingHeight(false)
        assertTrue(untouched.retainLiveHeight)
        untouched.toggle()
        untouched.retainStreamingHeight(true)
        assertFalse(untouched.expanded)
        assertFalse(untouched.retainLiveHeight)

        val savedClosed = ThinkingLaneState(ReadingPosition("collapsed", 0, 0), streaming = false)
        savedClosed.retainStreamingHeight(true)
        assertFalse(savedClosed.expanded)
    }

    @Test fun untouchedUnsupportedSlotDoesNotPersistAPhantomCollapseChoice() {
        val slot = ThinkingLaneState(null, false)
        assertNull(slot.positionToPersist(false))
        val reopened = ThinkingLaneState(slot.positionToPersist(false), true)
        assertTrue(reopened.expanded)
        val history = ThinkingLaneState(null, false)
        assertEquals("collapsed", history.positionToPersist(true)?.itemKey)
        val manual = ThinkingLaneState(null, true)
        manual.toggle()
        assertEquals("collapsed", manual.positionToPersist(false)?.itemKey)
    }

    @Test fun originalAndStickyUseTheSameStateForTheSameReadingKey() {
        val owner = NotebookMessageUiState()
        val original = owner.thinking("chat:1:thought:2") { ThinkingLaneState(null, true) }
        val sticky = owner.thinking("chat:1:thought:2") { error("Must reuse the existing lane") }
        org.junit.Assert.assertSame(original, sticky)
        sticky.toggle()
        assertFalse(original.expanded)
        original.toggle()
        assertTrue(sticky.expanded)
    }

    @Test fun onlyTheItemActuallyCrossingViewportTopCanOwnStickyHeader() {
        assertEquals(ThinkingTopItem("message:2", 2, 170), thinkingTopItem("message:2", 2, -30, 200, 0))
        assertEquals(ThinkingTopItem("message:2", 2, 200), thinkingTopItem("message:2", 2, 0, 200, 0))
        assertNull(thinkingTopItem("message:2", 2, 1, 200, 0))
        assertNull(thinkingTopItem("message:2", 2, -200, 200, 0))
        assertNull(thinkingTopItem("message:2", 2, -250, 200, 0))
    }

    @Test fun topOwnershipDoesNotDependOnScrollDirectionOrEarlierAssistant() {
        val forward = thinkingTopItem("message:3", 3, -15, 180, 0)
        val reverse = thinkingTopItem("message:3", 3, -15, 180, 0)
        assertEquals(forward, reverse)
        assertEquals("message:3", forward?.key)
        assertNull(thinkingTopItem("message:2", 2, -200, 180, 0))
        assertNull(thinkingTopItem("message:4", 4, 181, 180, 0))
    }
}
