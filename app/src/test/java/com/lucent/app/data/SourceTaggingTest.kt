package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceTaggingTest {

    @Test
    fun chapterAndSliceMarkersAreEmittedInReadingOrder() {
        val tagged = SourceTagging.tag("测试书", "# 第一章 起点\n\n正文一。\n\n正文二。")

        assertEquals("〔CH1〕\n# 第一章 起点\n\n〔S：1-1〕\n正文一。\n\n正文二。", tagged.text)
        assertEquals(listOf("S1-1"), tagged.slices.map { it.id })
        assertEquals(tagged.slices.single(), tagged.sliceOf("S1-1"))
        assertNull(tagged.sliceOf("S1-2"))
    }

    @Test
    fun realPreambleBecomesItsOwnChapter() {
        val body = "  引言正文。\r\n\r\n# 第一章 起点\r\n\r\n正文。"
        val tagged = SourceTagging.tag("测试书", body)

        assertEquals(listOf("S1-1", "S2-1"), tagged.slices.map { it.id })
        assertEquals("测试书", tagged.chapters.first().title)
        assertEquals(0, tagged.chapters.first().startOffset)
        assertEquals(body.indexOf("# 第一章"), tagged.chapters.first().endOffset)
        assertEquals("  引言正文。", textOf(body, tagged.slices.first()))
    }

    @Test
    fun blankPreambleDoesNotCreateAnExtraChapter() {
        val body = " \r\n\t\r\n  # 第一章 起点  \r\n\r\n正文。"
        val tagged = SourceTagging.tag("测试书", body)

        assertEquals(1, tagged.chapters.size)
        assertEquals(body.indexOf("  #"), tagged.chapters.single().startOffset)
        assertEquals("  # 第一章 起点  ", tagged.chapters.single().title)
        assertEquals(listOf("S1-1"), tagged.slices.map { it.id })
    }

    @Test
    fun bodyWithoutHeadingUsesTheNoteTitle() {
        val tagged = SourceTagging.tag("没有章节的书", "第一段。\n\n第二段。")

        assertEquals("〔CH1〕\n# 没有章节的书\n\n〔S：1-1〕\n第一段。\n\n第二段。", tagged.text)
        assertEquals("正文 1", SourceTagging.tag("", "正文。").chapters.single().title)
    }

    @Test
    fun numberingIsStableAndSlicesNeverCrossChapters() {
        val body = "# 第一章\n\n甲。\n\n# 第二章\n\n乙。"
        val first = SourceTagging.tag("书", body)

        assertEquals(first, SourceTagging.tag("书", body))
        assertEquals(listOf("S1-1", "S2-1"), first.slices.map { it.id })
        assertEquals(listOf("甲。", "乙。"), first.slices.map { textOf(body, it) })
    }

    @Test
    fun overlongParagraphIsNeverSplitAtSentencesOrLines() {
        val paragraph = "  " + "甲".repeat(1000) + "。\r\n" + "乙".repeat(1000) + "。  "
        val body = "# 第一章\r\n\r\n$paragraph\r\n"
        val tagged = SourceTagging.tag("书", body)

        assertEquals(1, tagged.slices.size)
        assertEquals(paragraph, textOf(body, tagged.slices.single()))
        assertTrue(tagged.text.endsWith(paragraph))
    }

    @Test
    fun nearestBoundaryMayBeBelowTheSoftTarget() {
        val first = "甲".repeat(450)
        val second = "乙".repeat(180)
        val body = "$first\n\n$second"

        assertEquals(listOf(first, second), SourceTagging.tag("书", body).slices.map { textOf(body, it) })
    }

    @Test
    fun nearestBoundaryMayBeAboveTheSoftTarget() {
        val first = "甲".repeat(300)
        val second = "乙".repeat(250)
        val third = "丙".repeat(500)
        val body = "$first\n\n$second\n\n$third"

        assertEquals(listOf("$first\n\n$second", third), SourceTagging.tag("书", body).slices.map { textOf(body, it) })
    }

    @Test
    fun equallyNearBoundariesPreferTheEarlierBoundary() {
        val first = "甲".repeat(400)
        val second = "乙".repeat(198)
        val body = "$first\n\n$second"

        assertEquals(listOf(first, second), SourceTagging.tag("书", body).slices.map { textOf(body, it) })
    }

    @Test
    fun exactlyFiveHundredCharactersCloseTheSlice() {
        val first = "甲".repeat(249)
        val second = "乙".repeat(249)
        val third = "丙"
        val body = "$first\n\n$second\n\n$third"
        val tagged = SourceTagging.tag("书", body)

        assertEquals(500, SourceTagging.TARGET_SLICE_CHARS)
        assertEquals(listOf(500, 1), tagged.slices.map { it.endOffset - it.startOffset })
        assertEquals(listOf("$first\n\n$second", third), tagged.slices.map { textOf(body, it) })
    }

    @Test
    fun blankLineSeparatorsAndCrLfAreRetainedInsideSlices() {
        val paragraph = "  😀正文一。  \r\n续行。\r\n \t\r\n\r\n\t正文二。  "
        val body = "# 第一章\r\n\r\n$paragraph\r\n\r\n# 第二章\r\n末段。\r\n"
        val tagged = SourceTagging.tag("书", body)
        val first = tagged.slices.first()

        assertEquals(body.indexOf("  😀"), first.startOffset)
        assertEquals(body.indexOf("\r\n\r\n# 第二章"), first.endOffset)
        assertEquals(paragraph, textOf(body, first))
        assertTrue(tagged.text.contains("〔S：1-1〕\n$paragraph\n\n〔CH2〕"))
        assertEquals(body.indexOf("# 第二章"), tagged.chapters.first().endOffset)
        assertEquals(body.indexOf("# 第二章"), tagged.chapters.last().startOffset)
        assertEquals(body.length, tagged.chapters.last().endOffset)
        assertEquals(body.indexOf("末段。"), tagged.slices.last().startOffset)
        assertEquals("末段。", textOf(body, tagged.slices.last()))
    }

    @Test
    fun repeatedParagraphsAndSurrogatesKeepExactUtf16Offsets() {
        val paragraph = "  😀" + "甲".repeat(496)
        val body = "$paragraph\r\n\r\n$paragraph"
        val tagged = SourceTagging.tag("书", body)

        assertEquals(500, paragraph.length)
        assertEquals(listOf(0, paragraph.length + 4), tagged.slices.map { it.startOffset })
        assertEquals(listOf(paragraph.length, body.length), tagged.slices.map { it.endOffset })
        assertEquals(listOf(paragraph, paragraph), tagged.slices.map { textOf(body, it) })
    }

    @Test
    fun fenceHeadingsAreNotChaptersAndFenceBlankLinesAreNotBoundaries() {
        val fence = "~~~markdown\r\n# 伪章节\r\n\r\n" + "甲".repeat(700) + "\r\n\r\n第一章 伪标题\r\n~~~~"
        val preface = "前".repeat(450)
        val body = "# 真章节\r\n\r\n$preface\r\n\r\n$fence\r\n\r\n后段。\r\n\r\n# 第二章\r\n末段。"
        val tagged = SourceTagging.tag("书", body)
        val fencedSlice = tagged.slices.single { textOf(body, it).contains("~~~markdown") }

        assertEquals(listOf("# 真章节", "# 第二章"), tagged.chapters.map { it.title })
        assertEquals(fence, textOf(body, fencedSlice))
        assertEquals(listOf("S1-1", "S1-2", "S1-3", "S2-1"), tagged.slices.map { it.id })
    }

    @Test
    fun backtickFenceRequiresMatchingMarkerLengthAndBlankClosingSuffix() {
        val marker = 96.toChar().toString()
        val open = marker.repeat(4)
        val short = marker.repeat(3)
        val fence = "$open kotlin\n# 伪章\n\n$short\n\n~~~\n\n$open trailing\n\n# 仍在围栏\n\n$open"
        val body = "# 真章\n\n$fence\n\n# 下一章\n正文。"
        val tagged = SourceTagging.tag("书", body)

        assertEquals(listOf("# 真章", "# 下一章"), tagged.chapters.map { it.title })
        assertEquals(fence, textOf(body, tagged.slices.first()))
    }

    @Test
    fun unclosedFenceProtectsTheRestOfTheDocument() {
        val fence = "~~~\n# 不是章节\n\n" + "甲".repeat(800) + "\n\n第九章 仍是代码"
        val body = "# 真章\n\n$fence"
        val tagged = SourceTagging.tag("书", body)

        assertEquals(1, tagged.chapters.size)
        assertEquals(1, tagged.slices.size)
        assertEquals(fence, textOf(body, tagged.slices.single()))
    }

    @Test
    fun headingOnlyChaptersHaveExactRangesAndNoInventedSlice() {
        val body = "# 第一章\r\n\r\n# 第二章"
        val tagged = SourceTagging.tag("书", body)

        assertEquals(2, tagged.chapters.size)
        assertTrue(tagged.slices.isEmpty())
        assertEquals(body.indexOf("# 第二章"), tagged.chapters.first().endOffset)
        assertEquals(body.length, tagged.chapters.last().endOffset)
    }

    @Test
    fun revisionIncludesVersionConfigurationTitleAndBody() {
        assertEquals(2, SourceTagging.INDEX_VERSION)
        assertEquals(SourceTagging.revisionOf("正文"), SourceTagging.revisionOf("正文"))
        assertNotEquals(SourceTagging.revisionOf("正文"), SourceTagging.revisionOf("正文。"))
        assertTrue(Regex("v2-[0-9a-f]{64}").matches(SourceTagging.revisionOf("正文")))
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(SourceTagging.revisionOf("Aa"), SourceTagging.revisionOf("BB"))
        assertNotEquals(SourceTagging.revisionOf("正文", "旧标题"), SourceTagging.revisionOf("正文", "新标题"))
        assertNotEquals(SourceTagging.revisionOf("c", "ab"), SourceTagging.revisionOf("bc", "a"))
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        listOf("2", "500", "书", "正文").forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        val expected = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals("v2-$expected", SourceTagging.revisionOf("正文", "书"))
    }

    @Test
    fun blankBodyProducesNoMarkers() {
        val tagged = SourceTagging.tag("书", "   \r\n\r\n ")

        assertEquals("", tagged.text)
        assertTrue(tagged.slices.isEmpty())
        assertTrue(tagged.chapters.isEmpty())
    }

    private fun textOf(body: String, slice: SourceTagging.Slice): String =
        body.substring(slice.startOffset, slice.endOffset)
}
