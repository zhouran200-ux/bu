package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdgeAutoScrollTest {
    @Test fun emptyNotebookNeverInventsASource() {
        assertEquals(0, notebookSourceCount(null))
        assertEquals(0, notebookSourceCount(0))
        assertEquals(1, notebookSourceCount(1))
        assertEquals(0, notebookSourceCount(-2))
    }
    @Test fun edgeVelocityIsDirectionalProgressiveAndCapped() {
        assertEquals(0f, edgeScrollVelocity(500f, 0f, 1000f, 100f, 900f))
        val near = edgeScrollVelocity(950f, 0f, 1000f, 100f, 900f)
        val closer = edgeScrollVelocity(990f, 0f, 1000f, 100f, 900f)
        assertTrue(closer > near && near > 0)
        assertEquals(900f, edgeScrollVelocity(1200f, 0f, 1000f, 100f, 900f))
        assertEquals(-900f, edgeScrollVelocity(-200f, 0f, 1000f, 100f, 900f))
    }
    @Test fun invalidOrTinyViewportDoesNotExplode() {
        assertEquals(0f, edgeScrollVelocity(Float.NaN, 0f, 10f, 4f, 900f))
        assertEquals(0f, edgeScrollVelocity(1f, 0f, 0f, 4f, 900f))
        assertEquals(0f, edgeScrollVelocity(5f, 0f, 10f, 100f, 900f))
        assertEquals(0f, edgeScrollVelocity(5f, 0f, 10f, Float.NaN, 900f))
        assertEquals(0f, edgeScrollVelocity(5f, 0f, 10f, 4f, Float.POSITIVE_INFINITY))
    }
}
