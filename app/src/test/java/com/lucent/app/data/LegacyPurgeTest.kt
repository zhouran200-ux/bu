package com.lucent.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPurgeTest {

    private val sampleBody = buildSampleBody()

    @Test
    fun theShippedSampleDocumentIsRecognised() {
        assertTrue(LegacyPurge.isSampleDocument("用户预置文档", sampleBody))
        assertTrue(LegacyPurge.isSampleDocument(" 用户预置文档 ", sampleBody))
    }

    @Test
    fun anEditedCopyIsLeftAlone() {
        assertFalse(LegacyPurge.isSampleDocument("用户预置文档", sampleBody + "后续修改"))
        assertFalse(LegacyPurge.isSampleDocument("用户预置文档", "我自己改过的正文"))
        assertFalse(LegacyPurge.isSampleDocument("我的资料", sampleBody))
    }

    @Test
    fun ordinaryDocumentsNeverMatch() {
        assertFalse(LegacyPurge.isSampleDocument("", ""))
        assertFalse(LegacyPurge.isSampleDocument("第一章", "第一章 正文"))
        assertFalse(LegacyPurge.isSampleDocument("用户预置文档备份", sampleBody))
    }

    @Test
    fun aBodyThatOnlyMentionsTheChapterLaterIsNotTheSample() {
        assertFalse(LegacyPurge.isSampleDocument("用户预置文档", "引言\n" + sampleBody))
    }

    @Test
    fun aBodyShorterThanThePrefixNeverMatches() {
        assertFalse(LegacyPurge.isSampleDocument("用户预置文档", sampleBody.take(10)))
    }

    private companion object {
        private val MARKERS = listOf(
            "\u3014CH:1\u3015 ",
            "\u7b2c\u4e00\u767e\u96f6\u4e09\u7ae0 ",
            "\u62e5\u62b1",
            "\n\n\u3014S:1-1\u3015\n",
            "\u5979\u4e0d\u559c\u6b22\u6709\u4eba\u62cd\u5979\u5f97\u5c81\u80a1\u3002"
        )

        fun buildSampleBody(): String = MARKERS.joinToString("")
    }
}
