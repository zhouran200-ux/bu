package com.lucent.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMotionHoldTest {
    @Test fun holdsNestAndOnlyTheLastReleaseResumesMotion() {
        while (BackgroundMotionHold.suspended) BackgroundMotionHold.release()
        assertFalse(BackgroundMotionHold.suspended)
        BackgroundMotionHold.acquire()
        assertTrue(BackgroundMotionHold.suspended)
        BackgroundMotionHold.acquire()
        assertTrue(BackgroundMotionHold.suspended)
        BackgroundMotionHold.release()
        assertTrue(BackgroundMotionHold.suspended)
        BackgroundMotionHold.release()
        assertFalse(BackgroundMotionHold.suspended)
        BackgroundMotionHold.release()
        assertFalse(BackgroundMotionHold.suspended)
    }
}
