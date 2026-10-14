package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelPickerDrawerTest {
    @Test fun shortOrUpwardDragDoesNotDismiss() {
        assertFalse(shouldDismissModelDrawer(20f, 0f, 56f))
        assertFalse(shouldDismissModelDrawer(0f, -2000f, 56f))
        assertFalse(shouldDismissModelDrawer(0f, 2000f, 56f))
    }
    @Test fun downwardDistanceOrFlingDismisses() {
        assertTrue(shouldDismissModelDrawer(56f, 0f, 56f))
        assertTrue(shouldDismissModelDrawer(4f, 1200f, 56f))
    }
}
