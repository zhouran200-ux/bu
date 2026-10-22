package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollStateCapsuleTest {
    @Test fun fractionStaysAtZeroWhenTheContentCannotScroll() {
        assertEquals(0f, scrollRailFraction(0, 0), 0f)
        assertEquals(0f, scrollRailFraction(40, 0), 0f)
        assertEquals(0f, scrollRailFraction(0, -10), 0f)
    }

    @Test fun fractionMapsTheOffsetOntoTheRail() {
        assertEquals(0f, scrollRailFraction(0, 200), 0f)
        assertEquals(0.25f, scrollRailFraction(50, 200), 0f)
        assertEquals(1f, scrollRailFraction(200, 200), 0f)
        assertEquals(1f, scrollRailFraction(400, 200), 0f)
    }

    @Test fun targetRoundTripsAndClampsIntoRange() {
        assertEquals(100, scrollRailTarget(0.5f, 200))
        assertEquals(0, scrollRailTarget(-1f, 200))
        assertEquals(200, scrollRailTarget(2f, 200))
        assertEquals(0, scrollRailTarget(0.5f, 0))
    }
}
