package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SourceTaggingTest {

    @Test
    fun chapterAndSliceMarkersAreEmittedInReadingOrder() {
        val tagged = SourceTagging.tag("测试书", "# 第一章 起点\n\n正文一。\n\n正文二。")

        assertEquals(
            "〔CH1〕\n# 第一章 起点\n\n〔S1-1〕\n正文一。\n\n正文二。",
            tagged.text
        )
        assertEquals(listOf("S1-1"), tagged.slices.map { it.id })
        assertEquals(1, tagged.chapters.size)
    }

    @Test
    fun contentBeforeTheFirstHeadingBecomesItsOwnChapter() {
        val tagged = SourceTagging.tag("测试书", "序言段落。\n\n# 第一章 起点\n\n正文。")

        assertEquals(
            "〔CH1〕\n# 测试书\n\n〔S1-1〕\n序言段落。\n\n〔CH2〕\n# 第一章 起点\n\n〔S2-1〕\n正文。",
            tagged.text
        )
        assertEquals(listOf("S1-1", "S2-1"), tagged.slices.map { it.id })
    }

    @Test
    fun bodyWithoutAnyHeadingUsesTheNoteTitleAsItsOnlyChapter() {
        val tagged = SourceTagging.tag("没有章节的书", "第一段。\n\n第二段。")

        assertEquals("〔CH1〕\n# 没有章节的书\n\n〔S1-1〕\n第一段。\n\n第二段。", tagged.text)
    }

    @Test
    fun sliceNumberingIsStableForTheSameBody() {
        val body = "# 第一章\n\n甲。\n\n# 第二章\n\n乙。"
        val first = SourceTagging.tag("书", body)
        val second = SourceTagging.tag("书", body)

        assertEquals(first.text, second.text)
        assertEquals(listOf("S1-1", "S2-1"), first.slices.map { it.id })
    }

    @Test
    fun revisionChangesWithTheBodyAndIsStableOtherwise() {
        assertEquals(SourceTagging.revisionOf("正文"), SourceTagging.revisionOf("正文"))
        assertNotEquals(SourceTagging.revisionOf("正文"), SourceTagging.revisionOf("正文。"))
        assertTrue(Regex("v1-[0-9a-f]{64}").matches(SourceTagging.revisionOf("正文")))
    }

    @Test
    fun aSingleOverlongSentenceIsSplitAtSentenceBoundaries() {
        val body = "# 第一章\n\n" + "甲".repeat(1000) + "。" + "乙".repeat(1000) + "。"
        val tagged = SourceTagging.tag("书", body)

        assertEquals(2, tagged.slices.size)
        assertEquals(listOf("S1-1", "S1-2"), tagged.slices.map { it.id })
        assertTrue(tagged.text.contains("〔S1-2〕"))
    }

    @Test
    fun revisionIncludesTitleAndDoesNotReuseJavaHashCollisions() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(SourceTagging.revisionOf("Aa"), SourceTagging.revisionOf("BB"))
        assertNotEquals(SourceTagging.revisionOf("正文", "旧标题"), SourceTagging.revisionOf("正文", "新标题"))
        assertNotEquals(SourceTagging.revisionOf("c", "ab"), SourceTagging.revisionOf("bc", "a"))
    }

    @Test
    fun blankBodyProducesNoMarkersAtAll() {
        val tagged = SourceTagging.tag("书", "   \n\n ")

        assertEquals("", tagged.text)
        assertTrue(tagged.slices.isEmpty())
        assertTrue(tagged.chapters.isEmpty())
    }

    @Test
    fun sliceOffsetsStayInsideTheOriginalBody() {
        val body = "# 第一章\n\n正文一。\n\n正文二。"
        val tagged = SourceTagging.tag("书", body)

        tagged.slices.forEach { slice ->
            assertTrue(slice.startOffset >= 0)
            assertTrue(slice.endOffset <= body.length)
            assertTrue(slice.startOffset < slice.endOffset)
        }
    }
}
