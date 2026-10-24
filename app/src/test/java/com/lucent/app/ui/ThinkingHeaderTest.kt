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

    @Test fun eachVisitStartsCollapsedAndCompletionDoesNotUndoManualExpansion() {
        val lane = ThinkingLaneState()
        assertFalse(lane.expanded)
        assertFalse(lane.fullExpanded)
        assertEquals(0, lane.scroll.value)
        lane.toggleFullExpanded()
        assertFalse(lane.fullExpanded)
        lane.retainStreamingHeight(true)
        assertFalse(lane.expanded)
        lane.toggle()
        assertFalse(lane.fullExpanded)
        lane.toggleFullExpanded()
        assertTrue(lane.fullExpanded)
        lane.toggleFullExpanded()
        assertFalse(lane.fullExpanded)
        lane.toggleFullExpanded()
        assertTrue(lane.fullExpanded)
        lane.retainStreamingHeight(true)
        assertTrue(lane.retainLiveHeight)
        lane.retainStreamingHeight(false)
        assertTrue(lane.expanded)
        assertTrue(lane.fullExpanded)
        assertTrue(lane.retainLiveHeight)
        lane.toggle()
        assertFalse(lane.expanded)
        assertFalse(lane.fullExpanded)
        assertFalse(lane.retainLiveHeight)
    }

    @Test fun lazyItemAndStickyRemountReuseVisitStateButNextVisitDoesNot() {
        val owner = NotebookMessageUiState()
        val original = owner.thinking("chat:1:thought:2") { ThinkingLaneState() }
        original.toggle()
        original.toggleFullExpanded()
        original.scroll.dispatchRawDelta(37f)
        val remounted = owner.thinking("chat:1:thought:2") { error("Must reuse the visit lane") }
        org.junit.Assert.assertSame(original, remounted)
        assertTrue(remounted.expanded)
        assertTrue(remounted.fullExpanded)
        assertEquals(37, remounted.scroll.value)
        remounted.toggleFullExpanded()
        assertFalse(original.fullExpanded)
        remounted.toggle()
        assertFalse(original.expanded)
        original.toggle()
        assertTrue(remounted.expanded)
        assertFalse(remounted.fullExpanded)
        val nextVisit = NotebookMessageUiState().thinking("chat:1:thought:2") { ThinkingLaneState() }
        assertFalse(nextVisit.expanded)
        assertFalse(nextVisit.fullExpanded)
        assertEquals(0, nextVisit.scroll.value)
        val otherNotebook = NotebookMessageUiState().thinking("chat:2:thought:2") { ThinkingLaneState() }
        assertFalse(otherNotebook.expanded)
        assertFalse(otherNotebook.fullExpanded)
    }

    @Test fun onlyTheItemActuallyCrossingViewportTopCanOwnStickyHeader() {
        assertEquals(ThinkingTopItem("message:2", 2), thinkingTopItem("message:2", 2, -30, 200, 0))
        assertEquals(ThinkingTopItem("message:2", 2), thinkingTopItem("message:2", 2, 0, 200, 0))
        assertNull(thinkingTopItem("message:2", 2, 1, 200, 0))
        assertNull(thinkingTopItem("message:2", 2, -200, 200, 0))
        assertNull(thinkingTopItem("message:2", 2, -250, 200, 0))
    }

    @Test fun stickyHeaderOnlyLivesWhileTheThinkingRegionIsStillOnScreen() {
        assertEquals(150, thinkingRemainingHeight(0, 150, 0))
        assertEquals(120, thinkingRemainingHeight(-30, 150, 0))
        assertEquals(66, thinkingRemainingHeight(-10, 100, 24))
        assertEquals(0, thinkingRemainingHeight(-150, 150, 0))
        assertEquals(0, thinkingRemainingHeight(-400, 150, 0))
        assertEquals(0, thinkingRemainingHeight(-30, 0, 0))
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
