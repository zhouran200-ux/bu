package com.lucent.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ChatStatsTest {
    @Test fun exactCountsAndExplicitZeroAreNotAbbreviated() {
        assertEquals("缓存 7500/10000T", ChatStats.cacheLabel(7500, 10000))
        assertEquals("缓存 0/10000T", ChatStats.cacheLabel(0, 10000, true))
        assertEquals("缓存 —/10000T", ChatStats.cacheLabel(0, 10000, false))
        assertNull(ChatStats.cacheLabel(0, 0))
        assertEquals("1000字", ChatStats.charLabel(1000))
    }
    @Test fun requestedOrderUsesLocalClockAndMeasuredTimings() {
        val time = Calendar.getInstance().apply { set(2026, 9, 15, 18, 40, 0) }.timeInMillis
        assertEquals("首字1秒 · 用时 10秒 · 缓存 7500/10000T · 1000字 · 18:40",
            ChatStats.summary(99, 7500, 10000, 1000, 1000, 10000, true, time))
    }
    @Test fun legacyRecordsNeverInventFirstTokenLatency() {
        assertEquals("用时 30秒 · 缓存 —/118800T · 2800字", ChatStats.summary(30, 0, 118800, 2800))
        assertEquals("", ChatStats.summary(0, 0, 0, 0))
    }
    @Test fun shortMeasurementsRetainSubsecondResolution() {
        assertEquals("首字<0.1秒 · 用时 0.3秒", ChatStats.summary(0, 0, 0, 0, 20, 350))
    }
    @Test fun referencesAndWhitespaceAreNotCountedAndEmojiIsOneCharacter() {
        assertEquals(4, ChatStats.bodyCharCount("你 好\n😀!〔REF:42:0001:S1-1〕〔S：1-1〕"))
    }
}
