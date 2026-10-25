package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun cutKeepsZwjEmojiSequencesTogether() {
        // man + ZWJ + woman + ZWJ + girl
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals(family.length, 8)
        assertEquals(0, revealCut(family, 2))
        assertEquals(0, revealCut(family, 3))
        assertEquals(0, revealCut(family, 5))
        assertEquals(0, revealCut(family, 6))
        assertEquals(8, revealCut(family, 8))
    }

    @Test fun cutKeepsSkinToneAndKeycapClustersIntact() {
        val thumb = "\uD83D\uDC4D\uD83C\uDFFD"
        assertEquals(0, revealCut(thumb, 2))
        assertEquals(0, revealCut(thumb, 3))
        assertEquals(4, revealCut(thumb, 4))

        val keycap = "1\uFE0F\u20E3"
        assertEquals(0, revealCut(keycap, 1))
        assertEquals(0, revealCut(keycap, 2))
        assertEquals(3, revealCut(keycap, 3))
    }

    @Test fun cutKeepsFlagPairsTogether() {
        val flags = "\uD83C\uDDE8\uD83C\uDDF3\uD83C\uDDFA\uD83C\uDDF8"
        assertEquals(8, flags.length)
        assertEquals(0, revealCut(flags, 2))
        assertEquals(4, revealCut(flags, 4))
        assertEquals(4, revealCut(flags, 6))
    }

    @Test fun cutKeepsCombiningMarksWithTheirBase() {
        val accented = "e\u0301x"
        assertEquals(0, revealCut(accented, 1))
        assertEquals(2, revealCut(accented, 2))
    }

    @Test fun cutDoesNotSplitCarriageReturnLineFeed() {
        assertEquals(1, revealCut("a\r\nb", 2))
        assertEquals(3, revealCut("a\r\nb", 3))
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

    @Test fun millisPerCharActuallyChangesTheRevealSpeed() {
        val fast = StreamPacer()
        val slow = StreamPacer()
        fast.reset(0f)
        slow.reset(0f)
        repeat(30) {
            fast.step(400, 0.016f, 1f)
            slow.step(400, 0.016f, 30f)
        }
        assertTrue("A smaller value must reveal faster", fast.displayedLength > slow.displayedLength)
    }

    @Test fun defaultPacingKeepsTheHistoricalSpeedCeiling() {
        assertEquals(1f, revealSpeedScale(DEFAULT_REVEAL_MILLIS_PER_CHAR), 0.0001f)
        val pacer = StreamPacer()
        pacer.reset(0f)
        repeat(200) { pacer.step(10_000, 0.016f) }
        assertEquals(180f, pacer.currentSpeed, 0.5f)
    }

    @Test fun invalidPacingFallsBackToTheDefault() {
        assertEquals(DEFAULT_REVEAL_MILLIS_PER_CHAR, normalizedRevealMillisPerChar(0f), 0.0001f)
        assertEquals(DEFAULT_REVEAL_MILLIS_PER_CHAR, normalizedRevealMillisPerChar(-2f), 0.0001f)
        assertEquals(DEFAULT_REVEAL_MILLIS_PER_CHAR, normalizedRevealMillisPerChar(Float.NaN), 0.0001f)
        assertEquals(1.5f, normalizedRevealMillisPerChar(1.5f), 0.0001f)
    }

    @Test fun completionKeepsRevealingTheTailInsteadOfJumping() {
        val driver = StreamRevealDriver()
        val text = "字".repeat(120)
        assertFalse(driver.update("", true, true))
        assertTrue(driver.update(text, true, true))
        repeat(2) { driver.frame(text, 0.016f, DEFAULT_REVEAL_MILLIS_PER_CHAR) }
        assertTrue("Reveal must trail the streamed text", driver.visibleCut < text.length)

        assertTrue("The pending tail must keep animating after completion", driver.update(text, false, true))
        repeat(400) {
            if (driver.update(text, false, true)) {
                driver.frame(text, 0.016f, DEFAULT_REVEAL_MILLIS_PER_CHAR)
            }
        }
        assertEquals(text.length, driver.visibleCut)
        assertFalse("A settled reveal must not request more frames", driver.update(text, false, true))
    }

    @Test fun reducedMotionRevealsEverythingImmediately() {
        val driver = StreamRevealDriver()
        val text = "abcdefghij".repeat(10)
        assertFalse(driver.update("", true, false))
        assertFalse(driver.update(text, true, false))
        assertEquals(text.length, driver.visibleCut)
        assertFalse(driver.update(text, false, false))
    }

    @Test fun rewrittenFailureTextSnapsInsteadOfRevealingAPrefix() {
        val driver = StreamRevealDriver()
        val partial = "partial answer"
        assertFalse(driver.update("", true, true))
        driver.update(partial, true, true)
        driver.frame(partial, 0.016f, DEFAULT_REVEAL_MILLIS_PER_CHAR)
        assertTrue(driver.visibleCut < partial.length)

        val failed = "请求失败，请重试"
        assertFalse(driver.update(failed, false, true))
        assertEquals(failed.length, driver.visibleCut)
    }

    @Test fun startingANewStreamRevealsFromItsOwnLength() {
        val driver = StreamRevealDriver()
        val first = "y".repeat(50)
        assertFalse(driver.update("", true, true))
        driver.update(first, true, true)
        driver.frame(first, 0.016f, DEFAULT_REVEAL_MILLIS_PER_CHAR)

        driver.update(first, false, true)
        val second = "z".repeat(5)
        assertFalse(driver.update(second, true, true))
        assertEquals(second.length, driver.visibleCut)
    }
}
