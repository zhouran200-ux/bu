package com.lucent.app.ui

import org.junit.Assert.assertEquals
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
}
