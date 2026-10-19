package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTailLayoutTest {
    private fun layout(
        lastIndex: Int = 4,
        end: Int = 450,
        forward: Boolean = true,
        height: Int = 500,
        inset: Int = 100
    ) = ChatTailLayout(5, lastIndex, end, height, inset, height, forward)

    @Test fun tailUsesMeasuredOverflowIncludingComposerInset() {
        assertEquals(50f, layout().forwardDistance(), 0f)
        assertEquals(130f, layout(end = 530).forwardDistance(), 0f)
    }

    @Test fun unmeasuredTailAdvancesOneReadableViewportWithoutReanchoring() {
        assertEquals(400f, layout(lastIndex = 2).forwardDistance(), 0f)
        assertEquals(280f, layout(lastIndex = 2, inset = 220).forwardDistance(), 0f)
    }

    @Test fun wrappedTextLayoutCanAdvanceWithoutAnyByteCountChange() {
        assertEquals(0f, layout(end = 400, forward = false).forwardDistance(), 0f)
        assertEquals(44f, layout(end = 444).forwardDistance(), 0f)
    }

    @Test fun settledOrShrinkingContentNeverRequestsBackwardMovement() {
        assertEquals(0f, layout(forward = false).forwardDistance(), 0f)
        assertEquals(0f, layout(end = 200).forwardDistance(), 0f)
        assertEquals(0f, layout(height = 0).forwardDistance(), 0f)
        assertEquals(0f, ChatTailLayout(0, -1, 0, 500, 100, 500, false).forwardDistance(), 0f)
    }
}
