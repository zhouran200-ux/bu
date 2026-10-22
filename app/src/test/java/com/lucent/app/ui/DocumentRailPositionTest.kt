package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DocumentRailPositionTest {
    private val model = DocumentRailPosition(101, listOf(0, 25, 50, 75))

    @Test fun remoteMeasurementsCannotChangeLogicalCoordinates() {
        assertEquals(0.255f, model.fraction(25, 250, 496, 4), 0.00001f)
        assertEquals(DocumentRailTarget(25, 0.5f), model.target(0.255f))
        assertEquals(0.25f, model.fraction(25, 0, 20, 4))
        assertEquals(0.25f, model.fraction(25, 0, 20000, 4))
    }

    @Test fun emptySingleAndInvalidInputsStayInBounds() {
        for (count in listOf(0, 1)) {
            val empty = DocumentRailPosition(count, listOf(-1, 0, 10))
            assertEquals(DocumentRailTarget(0, 0f), empty.target(1f))
            assertEquals(0f, empty.fraction(0, 100, 0, 0))
        }
        assertEquals(DocumentRailTarget(0, 0f), model.target(Float.NaN))
        assertEquals(DocumentRailTarget(0, 0f), model.target(-2f))
        assertEquals(DocumentRailTarget(100, 0f), model.target(2f))
        assertEquals(1f, model.fraction(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, 10))
    }

    @Test fun dragIsSlowerAndRepeatedDragsCanReachTheEnd() {
        val noChapters = DocumentRailPosition(101, emptyList())
        val pressed = DocumentRailDragSession(noChapters, 0f, 500f)
        assertSame(pressed, pressed.move(900f))
        val first = pressed.start(0f).move(500f)
        assertEquals(0.45f, first.fraction, 0.00001f)
        val second = DocumentRailDragSession(noChapters, first.fraction, 500f).start(0f).move(500f)
        assertEquals(0.9f, second.fraction, 0.00001f)
        assertEquals(1f, DocumentRailDragSession(noChapters, second.fraction, 500f).start(0f).move(500f).fraction)
    }

    @Test fun chapterSnapHoldsUntilExitThresholdAndDoesNotOscillate() {
        val started = DocumentRailDragSession(model, 0.23f, 500f).start(0f)
        val snapped = started.move(15f)
        assertEquals(0.25f, snapped.fraction)
        assertEquals(0.25f, snapped.snapped)
        val held = snapped.move(40f)
        assertEquals(0.25f, held.fraction)
        val released = held.move(55f)
        assertNull(released.snapped)
        assertTrue(released.fraction > 0.274f)
        assertEquals(released.fraction, released.move(55f).fraction)
    }

    @Test fun endpointsWinAndReversingDoesNotHaveOvershootDebt() {
        val bottom = DocumentRailDragSession(model, 0.99f, 500f).start(0f).move(500f)
        assertEquals(1f, bottom.fraction)
        assertNull(bottom.snapped)
        assertTrue(bottom.move(490f).fraction < 1f)
        val top = DocumentRailDragSession(model, 0.01f, 500f).start(0f).move(-500f)
        assertEquals(0f, top.fraction)
        assertNull(top.snapped)
        assertSame(top, top.move(Float.NaN))
    }

    @Test fun chapterIndicesAreValidatedAndDenseChaptersHoldCurrentSnap() {
        val dense = DocumentRailPosition(101, listOf(-1, 50, 50, 51, 101))
        assertEquals(listOf(0.5f, 0.51f), dense.chapterFractions)
        val snapped = DocumentRailDragSession(dense, 0.49f, 500f).start(0f).move(5f)
        assertEquals(0.5f, snapped.fraction)
        assertEquals(0.5f, snapped.move(23f).fraction)
    }
}
