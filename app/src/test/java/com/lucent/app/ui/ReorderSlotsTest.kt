package com.lucent.app.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReorderSlotsTest {

    private val keys = listOf(10L, 20L, 30L, 40L)

    private fun slots(): ReorderSlots = ReorderSlots(
        keys = keys,
        offsets = listOf(
            IntOffset(0, 0), IntOffset(0, 100), IntOffset(0, 200), IntOffset(0, 300)
        ),
        sizes = List(4) { IntSize(500, 100) },
        viewportLeft = 0,
        viewportTop = 0,
        viewportRight = 500,
        viewportBottom = 400
    )

    @Test
    fun lookupHelpersAreSafeForUnknownAndNullKeys() {
        val slots = slots()
        assertEquals(2, slots.indexOfKey(30L))
        assertEquals(-1, slots.indexOfKey(null))
        assertEquals(-1, slots.indexOfKey(999L))
        assertNull(slots.offsetOf(999L))
        assertNull(slots.sizeOf(null))
        assertNull(slots.boundsAt(9))
        assertNotNull(slots.offsetOf(40L))
        assertEquals(keys, slots.visibleKeys())
        assertEquals(4, slots.count)
    }

    @Test
    fun noDragKeepsEveryCardAtItsOwnSlot() {
        val slots = slots()
        keys.forEach { key ->
            assertEquals(slots.offsetOf(key), slots.targetFor(key, null, null, null))
            assertEquals(IntOffset.Zero, slots.reflowFor(key, null, null, null))
        }
    }

    @Test
    fun draggingFirstCardDownShiftsPassedCardsUpIntoTheGap() {
        val slots = slots()
        assertEquals(IntOffset(0, 200), slots.targetFor(10L, 10L, 30L, 40L))
        assertEquals(IntOffset(0, -100), slots.reflowFor(20L, 10L, 30L, 40L))
        assertEquals(IntOffset(0, -100), slots.reflowFor(30L, 10L, 30L, 40L))
        assertEquals(IntOffset.Zero, slots.reflowFor(40L, 10L, 30L, 40L))
    }

    @Test
    fun draggingLastCardUpShiftsPassedCardsDownIntoTheGap() {
        val slots = slots()
        assertEquals(IntOffset(0, 100), slots.targetFor(40L, 40L, 10L, 20L))
        assertEquals(IntOffset(0, 100), slots.reflowFor(20L, 40L, 10L, 20L))
        assertEquals(IntOffset(0, 100), slots.reflowFor(30L, 40L, 10L, 20L))
        assertEquals(IntOffset.Zero, slots.reflowFor(10L, 40L, 10L, 20L))
    }

    @Test
    fun gapBeforeOnlyStillResolvesALandingSlot() {
        val slots = slots()
        assertEquals(IntOffset(0, 200), slots.targetFor(40L, 40L, 20L, null))
        assertEquals(IntOffset.Zero, slots.reflowFor(20L, 40L, 20L, null))
        assertEquals(IntOffset(0, 100), slots.reflowFor(30L, 40L, 20L, null))
    }

    @Test
    fun gapAfterOnlyStillResolvesALandingSlot() {
        val slots = slots()
        assertEquals(IntOffset(0, 100), slots.targetFor(40L, 40L, null, 20L))
        assertEquals(IntOffset(0, 100), slots.reflowFor(20L, 40L, null, 20L))
    }

    @Test
    fun noGapInformationFallsBackToTheHoveredSlotWithoutReflow() {
        val slots = slots()
        assertEquals(IntOffset(0, 300), slots.targetFor(40L, 40L, null, null))
        assertEquals(slots.offsetOf(10L), slots.targetFor(10L, 40L, null, null))
        assertEquals(IntOffset.Zero, slots.reflowFor(20L, 40L, null, null))
    }

    @Test
    fun unknownKeysNeverProduceMisplacedOffsets() {
        val slots = slots()
        assertNull(slots.targetFor(999L, 10L, 30L, 40L))
        assertEquals(IntOffset.Zero, slots.reflowFor(999L, 10L, 30L, 40L))
        assertEquals(IntOffset.Zero, slots.reflowFor(20L, 999L, 30L, 40L))
        assertEquals(IntOffset.Zero, slots.reflowFor(20L, 10L, 999L, 998L))
    }

    @Test
    fun emptySlotListHasNoLandingSlot() {
        val empty = ReorderSlots(
            keys = emptyList(),
            offsets = emptyList(),
            sizes = emptyList(),
            viewportLeft = 0, viewportTop = 0, viewportRight = 0, viewportBottom = 0
        )
        assertEquals(-1, empty.indexOfKey(1L))
        assertNull(empty.targetFor(1L, 1L, 1L, null))
        assertEquals(IntOffset.Zero, empty.reflowFor(1L, 1L, 1L, null))
    }

    @Test
    fun dragStateWithoutMovementClearsCompletelyOnRelease() {
        val state = ReorderDragState()
        state.begin(10L)
        assertTrue(state.dragging)
        assertEquals(10L, state.draggingId)
        assertFalse(state.moved)

        val landed = state.finish(null)
        assertEquals(null to null, landed)
        assertFalse(state.dragging)
        assertFalse(state.settling)
        assertNull(state.settleOrder)
        assertNull(state.draggingId)
        assertNull(state.gapBeforeId)
        assertNull(state.gapAfterId)
    }

    @Test
    fun movedDragSettlesIntoTheRequestedOrder() {
        val state = ReorderDragState()
        state.begin(10L)
        state.moved = true
        state.gapBeforeId = 30L
        state.gapAfterId = 40L

        val landed = state.finish(listOf(20L, 30L, 10L, 40L))
        assertEquals(30L to 40L, landed)
        assertFalse(state.dragging)
        assertTrue(state.settling)
        assertEquals(listOf(20L, 30L, 10L, 40L), state.settleOrder)
    }

    @Test
    fun movedDragWithoutOrderKeepsSettlingAndDropsTheGap() {
        val state = ReorderDragState()
        state.begin(10L)
        state.moved = true
        state.gapBeforeId = 30L

        state.finish(null)
        assertTrue(state.settling)
        assertNull(state.settleOrder)
        assertNull(state.gapBeforeId)
        assertNull(state.gapAfterId)
    }

    @Test
    fun cancelledDragRestoresCardsWhenNothingMoved() {
        val state = ReorderDragState()
        state.begin(20L)
        state.cancel()
        assertFalse(state.dragging)
        assertFalse(state.settling)
        assertNull(state.draggingId)
    }

    @Test
    fun cancelledMovedDragStillSettlesWithoutLeavingAGap() {
        val state = ReorderDragState()
        state.begin(20L)
        state.moved = true
        state.gapAfterId = 30L

        state.cancel()
        assertFalse(state.dragging)
        assertTrue(state.settling)
        assertNull(state.settleOrder)
        assertNull(state.gapAfterId)
    }

    @Test
    fun landingClearsEveryTransientField() {
        val state = ReorderDragState()
        state.begin(20L)
        state.moved = true
        state.finish(listOf(20L, 10L))
        state.landed()

        assertFalse(state.settling)
        assertNull(state.settleOrder)
        assertNull(state.draggingId)
        assertFalse(state.moved)
        assertNull(state.gapBeforeId)
        assertEquals(0f, state.dragY)
    }

    @Test
    fun eachBeginInvalidatesThePreviousDragToken() {
        val state = ReorderDragState()
        state.begin(10L)
        val first = state.dragToken
        state.begin(20L)
        assertTrue(state.dragToken > first)
        assertEquals(20L, state.draggingId)
    }
}