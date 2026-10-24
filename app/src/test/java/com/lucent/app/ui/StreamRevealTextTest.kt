package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRevealTextTest {
    @Test fun cutStaysInsideTheString() {
        assertEquals(0, revealCut("abc", -4))
        assertEquals(3, revealCut("abc", 9))
        assertEquals(2, revealCut("abc", 2))
    }

    @Test fun cutNeverSplitsASurrogatePair() {
        val emoji = "\uD83D\uDE00"
        val text = "a" + emoji + "b"
        assertEquals(1, revealCut(text, 2))
        assertEquals(3, revealCut(text, 3))
    }

    @Test fun pacerStepsSmoothlyTowardsTarget() {
        val pacer = StreamPacer()
        pacer.reset(0f)
        val firstStep = pacer.step(20, 0.016f)
        assertTrue(firstStep in 0..2)
        assertTrue(pacer.displayedLength > 0f)
        assertTrue(pacer.displayedLength < 20f)
    }

    @Test fun pacerRampsSpeedWhenBacklogIsHigh() {
        val pacer = StreamPacer()
        pacer.reset(0f)
        val initialSpeed = pacer.currentSpeed
        pacer.step(200, 0.016f)
        assertTrue(pacer.currentSpeed > initialSpeed)
    }

    @Test fun pacerSnapsWhenTargetShrinks() {
        val pacer = StreamPacer()
        pacer.reset(50f)
        val step = pacer.step(10, 0.016f)
        assertEquals(10, step)
        assertEquals(10f, pacer.displayedLength, 0.001f)
    }

    @Test fun pacerReachesExactTargetWhenDrained() {
        val pacer = StreamPacer()
        pacer.reset(0f)
        var result = 0
        repeat(60) {
            result = pacer.step(30, 0.016f)
        }
        assertEquals(30, result)
        assertEquals(30f, pacer.displayedLength, 0.001f)
    }
}
