package com.lucent.app.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReorderAutoScrollTest {
    @Test
    fun actualConsumedScrollKeepsGrabbedItemUnderStationaryFinger() {
        val state = ReorderDragState()
        state.grab(10L, 100f, 125f, IntSize(400, 80))
        state.movePointer(150f, 8f)
        state.compensateScroll(37.5f)
        assertEquals(275f, state.pointerY)
        assertEquals(250f, state.overlayTop)
        assertEquals(62.5f, state.sourceTop)
        assertEquals(187.5f, state.dragY)
        assertEquals(state.pointerY, state.overlayTop + state.grabbedY)
        state.compensateScroll(-12.5f)
        assertEquals(250f, state.overlayTop)
        assertEquals(175f, state.dragY)
    }

    @Test
    fun clampedScrollDoesNotMoveOverlayOrInventCompensation() {
        val state = ReorderDragState()
        state.grab(10L, 100f, 125f, IntSize(400, 80))
        state.compensateScroll(0f)
        assertEquals(0f, state.dragY)
        assertEquals(100f, state.overlayTop)
        assertFalse(state.moved)
    }

    @Test
    fun slopAccumulatesAcrossSmallPointerEventsButNotScrollCompensation() {
        val state = ReorderDragState()
        state.grab(10L, 100f, 125f, IntSize(400, 80))
        repeat(3) { state.movePointer(3f, 8f) }
        assertTrue(state.moved)
        assertEquals(109f, state.overlayTop)
    }

    @Test
    fun stoppedOrCancelledDragRejectsLateScrollAndPointerEvents() {
        for (cancel in listOf(false, true)) {
            val state = ReorderDragState()
            state.grab(10L, 100f, 125f, IntSize(400, 80))
            state.movePointer(40f, 8f)
            if (cancel) state.cancel() else state.finish(listOf(20L, 10L))
            val translation = state.dragY
            val pointer = state.pointerY
            state.compensateScroll(30f)
            state.movePointer(50f, 8f)
            assertFalse(state.dragging)
            assertEquals(translation, state.dragY)
            assertEquals(pointer, state.pointerY)
        }
    }

    @Test
    fun newGrabResetsTravelAndCapturesFreshGeometry() {
        val state = ReorderDragState()
        state.grab(10L, 100f, 125f, IntSize(400, 80))
        state.movePointer(70f, 8f)
        state.compensateScroll(1000f)
        val token = state.dragToken
        state.grab(20L, 200f, 210f, IntSize(500, 90))
        state.movePointer(2f, 8f)
        assertTrue(state.dragToken > token)
        assertFalse(state.moved)
        assertEquals(202f, state.overlayTop)
        assertEquals(10f, state.grabbedY)
        assertEquals(IntSize(500, 90), state.draggedSize)
    }

    @Test
    fun gapCanAdvanceAcrossNewViewportsWithoutOriginalItem() {
        fun slots(keys: List<Long>, top: Int) = ReorderSlots(
            keys, keys.indices.map { IntOffset(0, top + it * 100) },
            keys.map { IntSize(400, 80) }, 0, 0, 400, 400
        )
        assertEquals(30L to 40L, slots(listOf(20L, 30L, 40L), 0).gapAtY(230f, 10L))
        assertEquals(60L to 70L, slots(listOf(50L, 60L, 70L), 0).gapAtY(230f, 10L))
        assertEquals(null to 50L, slots(listOf(50L, 60L), 0).gapAtY(-20f, 10L))
        assertEquals(60L to null, slots(listOf(50L, 60L), 0).gapAtY(500f, 10L))
        assertEquals(20L to 30L, slots(listOf(10L, 20L, 30L), 0).gapAtY(170f, 10L))
        assertEquals(null to null, slots(emptyList(), 0).gapAtY(100f, 10L))
    }

    @Test
    fun offscreenDragDropsIntoWholeListRatherThanVisibleSubset() {
        val all = (1L..20L).toList()
        assertEquals((2L..15L).toList() + 1L + (16L..20L).toList(),
            reorderedAround(all, listOf(1L), 15L, 16L) { it })
    }
}
