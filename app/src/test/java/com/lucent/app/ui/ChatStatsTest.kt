package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStatsTest {

    @Test
    fun compactCountScalesLargeNumbersAndMarksUnknown() {
        assertEquals("—", ChatStats.compactCount(0))
        assertEquals("823", ChatStats.compactCount(823))
        assertEquals("1k", ChatStats.compactCount(1000))
        assertEquals("1.2k", ChatStats.compactCount(1234))
        assertEquals("12k", ChatStats.compactCount(12000))
        assertEquals("1.2M", ChatStats.compactCount(1234567))
    }

    @Test
    fun secondsLabelSwitchesToMinutesPastAnHour() {
        assertNull(ChatStats.secondsLabel(0))
        assertEquals("回复 12秒", ChatStats.secondsLabel(12))
        assertEquals("回复 1分05秒", ChatStats.secondsLabel(65))
    }

    @Test
    fun cacheSegmentIsHiddenWhenTheProviderReportsNothing() {
        assertNull(ChatStats.cacheLabel(cachedTokens = 0, promptTokens = 0))
        assertEquals("缓存 —/8.6k", ChatStats.cacheLabel(cachedTokens = 0, promptTokens = 8600))
        assertEquals("缓存 6.1k/8.6k", ChatStats.cacheLabel(cachedTokens = 6100, promptTokens = 8600))
    }

    @Test
    fun summaryJoinsOnlyThePartsThatExist() {
        assertEquals("", ChatStats.summary(seconds = 0, cachedTokens = 0, promptTokens = 0, chars = 0))
        assertEquals("回复 9秒 · 1.4k字", ChatStats.summary(seconds = 9, cachedTokens = 0, promptTokens = 0, chars = 1380))
        assertEquals(
            "回复 9秒 · 缓存 6.1k/8.6k · 1.4k字",
            ChatStats.summary(seconds = 9, cachedTokens = 6100, promptTokens = 8600, chars = 1380)
        )
    }

    @Test
    fun previewFlattensNewlinesAndTruncates() {
        assertEquals("第一行 第二行", ChatStats.preview("第一行\n第二行"))
        assertEquals("（空）", ChatStats.preview("   "))
        assertEquals("abc…", ChatStats.preview("abcdef", limit = 3))
    }
}
