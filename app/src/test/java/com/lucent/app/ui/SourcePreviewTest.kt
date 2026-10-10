package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourcePreviewTest {

    @Test
    fun blankBodiesHaveNoExcerpt() {
        assertEquals("", previewExcerpt(""))
        assertEquals("", previewExcerpt("   \n\n\t  "))
    }

    @Test
    fun linesCollapseIntoOneRow() {
        assertEquals("第一段 第二段 第三段", previewExcerpt("第一段\n\n第二段\n   第三段   "))
    }

    @Test
    fun markdownMarkersAreNotShownInTheExcerpt() {
        assertEquals("标题 要点", previewExcerpt("# 标题\n- 要点"))
        assertEquals("正文", previewExcerpt("> 正文"))
    }

    @Test
    fun longBodiesAreCutWithAnEllipsis() {
        val body = "字".repeat(300)
        val excerpt = previewExcerpt(body, limit = 96)
        assertTrue(excerpt.endsWith("…"), excerpt)
        assertEquals(97, excerpt.length)
        assertTrue(previewExcerpt("短内容").length <= 96)
    }

    @Test
    fun exactlyLimitLongIsNotCut() {
        val body = "字".repeat(96)
        assertEquals(body, previewExcerpt(body))
    }
}
