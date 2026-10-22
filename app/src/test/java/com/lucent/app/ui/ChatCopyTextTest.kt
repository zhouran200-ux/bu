package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCopyTextTest {
    @Test fun citationMarkersAreRemovedFromCopiedText() {
        assertEquals("前文后文", chatBodyForCopy("前文〔S：3-12〕后文"))
        assertEquals("引用在句末。", chatBodyForCopy("引用在句末。〔REF:7:abcdef:S3-3〕"))
        assertEquals("甲 乙", chatBodyForCopy("甲 〔S1-1〕乙"))
    }

    @Test fun plainProseAndLineBreaksSurvive() {
        val body = "# 标题\n\n第一段\n    缩进保持\n\n第二段"
        assertEquals(body, chatBodyForCopy(body))
    }

    @Test fun trailingWhitespaceLeftBehindByAMarkerIsTrimmed() {
        assertEquals("结尾", chatBodyForCopy("结尾〔S：1-1〕"))
        assertEquals("", chatBodyForCopy("〔S：1-1〕"))
    }
}
