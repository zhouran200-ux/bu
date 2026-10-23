package com.lucent.app.ui

import com.lucent.app.data.SourceTagging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourcePreviewRowsTest {
    @Test
    fun rowsMirrorReaderKeysAndKeepTheTaggedText() {
        val body = "# 第一章\r\n\r\n第一段。\r\n\r\n第二段。\r\n# 第二章\r\n正文。"
        val tagged = SourceTagging.tag("书", body)
        val rows = sourcePreviewRows(body, tagged)

        assertEquals(sourceReadingItems(body, tagged).map { it.key }, rows.map { it.key })
        assertEquals(tagged.text, rows.joinToString("\n\n") { it.text })
        assertEquals(listOf(true, false, true, false), rows.map { it.isChapter })
    }

    @Test
    fun blankLinesInsideASliceStayInTheSameRow() {
        val body = "# 章\n\n甲。\n\n乙。"
        val tagged = SourceTagging.tag("书", body)
        val rows = sourcePreviewRows(body, tagged)

        assertEquals(listOf("chapter:1", "slice:S1-1"), rows.map { it.key })
        assertEquals("〔S：1-1〕\n甲。\n\n乙。", rows[1].text)
    }

    @Test
    fun rowsStartAtMarkersAndDependOnlyOnTheTaggedText() {
        val body = "序言。\n\n# 第一章\n\n# 第二章\n正文。"
        val tagged = SourceTagging.tag("书", body)
        val rows = sourcePreviewRows(body, tagged)

        assertTrue(rows.all { it.text.startsWith("〔") })
        assertEquals(listOf("chapter:1", "slice:S1-1", "chapter:2", "chapter:3", "slice:S3-1"), rows.map { it.key })
        assertEquals("〔CH2〕\n# 第一章", rows[2].text)
    }

    @Test fun literalMarkersDoNotCreatePhantomRows() {
        val body = "# 章\n\n〔S：1-1〕\n原文标签。\n\n〔CH1〕\n不是章节。"
        val tagged = SourceTagging.tag("书", body)
        val rows = sourcePreviewRows(body, tagged)
        assertEquals(sourceReadingItems(body, tagged).map { it.key }, rows.map { it.key })
        assertEquals(tagged.text, rows.joinToString("\n\n") { it.text })
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }

    @Test
    fun blankSourcesHaveNoPreviewRows() {
        assertTrue(sourcePreviewRows(" \r\n ", SourceTagging.tag("空文档", " \r\n ")).isEmpty())
    }
}
