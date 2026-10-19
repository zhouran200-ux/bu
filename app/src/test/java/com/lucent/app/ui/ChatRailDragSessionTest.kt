package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChatRailDragSessionTest {
    private val geometry = ChatRailGeometry(List(10) { 500 }, 500, 0)

    @Test fun pressAndSlopDoNotChangeTheAnchorOrFraction() {
        val session = ChatRailDragSession(geometry, 0.5f, ChatScrollTarget(4, 250), 600f)
        assertSame(session, session.move(900f))
        val started = session.start(360f)
        assertEquals(0.5f, started.move(360f).fraction)
        assertEquals(ChatScrollTarget(4, 250), started.target())
    }

    @Test fun movingThumbCoordinatesDoNotFeedBackIntoRootPointerMovement() {
        val session = ChatRailDragSession(geometry, 0.5f, ChatScrollTarget(4, 250), 600f).start(320f)
        val first = session.move(350f)
        assertEquals(330f, first.thumbTop(600f), 0.001f)
        val pointerInMovedThumb = 350f - first.thumbTop(600f)
        val stationary = first.move(first.thumbTop(600f) + pointerInMovedThumb)
        assertEquals(first.fraction, stationary.fraction)
        val second = stationary.move(first.thumbTop(600f) + pointerInMovedThumb + 30f)
        assertEquals(360f, second.thumbTop(600f), 0.001f)
    }

    @Test fun resizingTrackKeepsFrozenPixelPositionAndGainUntilRelease() {
        val session = ChatRailDragSession(geometry, 0.5f, ChatScrollTarget(4, 250), 600f).start(320f)
        assertEquals(300f, session.thumbTop(450f))
        assertEquals(300f, session.thumbTop(800f))
        assertEquals(330f, session.move(350f).thumbTop(450f), 0.001f)
        assertEquals(250f, session.thumbTop(250f))
    }

    @Test fun liveMeasurementChangesDoNotReplaceTheHeldGeometry() {
        val session = ChatRailDragSession(geometry, 0.5f, ChatScrollTarget(4, 250), 600f).start(320f)
        val updated = ChatRailGeometry(List(10) { 900 }, 400, 0)
        val moved = session.move(380f)
        assertSame(geometry, moved.geometry)
        assertEquals(ChatScrollTarget(5, 200), moved.target())
        assertTrue(updated.target(moved.fraction) != moved.target())
    }

    @Test fun estimatedBottomKeepsActualAnchorInsteadOfJumpingToEstimatedMaximum() {
        val estimated = ChatRailGeometry(List(10) { 100 }, 500, 0)
        val anchor = ChatScrollTarget(8, 50)
        val session = ChatRailDragSession(estimated, 1f, anchor, 500f).start(520f)
        assertEquals(anchor, session.target())
        assertEquals(ChatScrollTarget(8, 0), session.move(470f).target())
        assertEquals(ChatScrollTarget(4, 50), estimated.target(0.9f))
    }

    @Test fun dragClampsEndpointsWithoutAccumulatingOvershoot() {
        val session = ChatRailDragSession(geometry, 0.5f, ChatScrollTarget(4, 250), 600f).start(320f)
        assertEquals(0f, session.move(-900f).fraction)
        assertEquals(1f, session.move(1900f).fraction)
        assertEquals(0.6f, session.move(1900f).move(380f).fraction, 0.001f)
    }
}
