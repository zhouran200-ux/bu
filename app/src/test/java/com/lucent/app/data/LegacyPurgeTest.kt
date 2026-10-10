package com.lucent.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPurgeTest {

    private val sampleBody = "〔CH:1〕 第一百零三章 拥抱\n\n〔S:1-1〕\n她不喜欢有人拍她得屁股。"

    @Test
    fun theShippedSampleDocumentIsRecognised() {
        assertTrue(LegacyPurge.isSampleDocument("用户预置文档", sampleBody))
        assertTrue(LegacyPurge.isSampleDocument(" 用户预置文档 ", sampleBody))
    }

    @Test
    fun anEditedCopyIsLeftAlone() {
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
        assertFalse(LegacyPurge.isSampleDocument("用户预置文档", "引言\n〔CH:1〕 第一百零三章 拥抱"))
    }
}
