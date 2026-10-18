package com.lucent.app.ui

import com.lucent.app.i18n.S
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ChatStatsTest {
    @Test fun exactCountsAndExplicitZeroAreNotAbbreviated() {
        assertEquals(S.statsCache("7500", 10000), ChatStats.cacheLabel(7500, 10000))
        assertEquals(S.statsCache("0", 10000), ChatStats.cacheLabel(0, 10000, true))
        assertEquals(S.statsCache("—", 10000), ChatStats.cacheLabel(0, 10000, false))
        assertNull(ChatStats.cacheLabel(0, 0))
        assertEquals(S.statsChars(1000), ChatStats.charLabel(1000))
    }
    @Test fun requestedOrderUsesLocalClockAndMeasuredTimings() {
        val time = Calendar.getInstance().apply { set(2026, 9, 15, 18, 40, 0) }.timeInMillis
        val expected = listOf(
            S.statsFirstToken(S.statsWholeSeconds(1)),
            S.statsDurationText(S.statsWholeSeconds(10)),
            S.statsCache("7500", 10000),
            S.statsChars(1000),
            "18:40"
        ).joinToString(" · ")
        assertEquals(expected, ChatStats.summary(99, 7500, 10000, 1000, 1000, 10000, true, time))
    }
    @Test fun legacyRecordsNeverInventFirstTokenLatency() {
        val expected = listOf(
            S.statsDuration(30),
            S.statsCache("—", 118800),
            S.statsChars(2800)
        ).joinToString(" · ")
        assertEquals(expected, ChatStats.summary(30, 0, 118800, 2800))
        assertEquals("", ChatStats.summary(0, 0, 0, 0))
    }
    @Test fun shortMeasurementsRetainSubsecondResolution() {
        val expected = listOf(
            S.statsFirstToken(S.statsUnderTenth),
            S.statsDurationText(S.statsFractionalSeconds(0, 3))
        ).joinToString(" · ")
        assertEquals(expected, ChatStats.summary(0, 0, 0, 0, 20, 350))
    }
    @Test fun referencesAndWhitespaceAreNotCountedAndEmojiIsOneCharacter() {
        assertEquals(4, ChatStats.bodyCharCount("你 好\n😀!〔REF:42:0001:S1-1〕〔S：1-1〕"))
    }
}
