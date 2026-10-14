package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlainTextMarkdownTest {

    @Test
    fun everySourceLineBecomesItsOwnParagraph() {
        assertEquals(
            "第一句。\n\n第二句。\n\n第三句。",
            PlainTextMarkdown.normalize("第一句。\n第二句。\n第三句。")
        )
    }

    @Test
    fun existingBlankLineRunsCollapseInsteadOfMultiplyingParagraphs() {
        assertEquals("一\n\n二", PlainTextMarkdown.normalize("一\n\n\n\n二"))
        assertEquals("一\n\n二", PlainTextMarkdown.normalize("一\n\n二"))
        assertEquals("一\n\n二", PlainTextMarkdown.normalize("一\r\n\r\n二"))
    }

    @Test
    fun chapterLinesBecomeHeadingsWithTheirTitle() {
        assertEquals("# 第57章 剥皮\n\n正文一", PlainTextMarkdown.normalize("第57章 剥皮\n正文一"))
        assertEquals("# 第五十八章 新线索", PlainTextMarkdown.normalize("第五十八章 新线索"))
        assertEquals("# 第５９章：归来", PlainTextMarkdown.normalize("第５９章：归来"))
        assertEquals("# 第3节 设定", PlainTextMarkdown.normalize("第3节 设定"))
    }

    @Test
    fun chapterMentionsInsideSentencesStayOrdinaryParagraphs() {
        assertEquals("他翻到了第58章，继续。", PlainTextMarkdown.normalize("他翻到了第58章，继续。"))
        assertEquals("第58章的内容仍未结束。", PlainTextMarkdown.normalize("第58章的内容仍未结束。"))
        assertEquals("#不是标题", PlainTextMarkdown.normalize("#不是标题"))
    }

    @Test
    fun existingMarkdownHeadingsAreKeptAsTheyAre() {
        assertEquals("## 第一章 开始\n\n正文", PlainTextMarkdown.normalize("## 第一章 开始\n正文"))
    }

    @Test
    fun indentationAndBomAndTrailingSpacesAreNormalised() {
        assertEquals("　　缩进段落", PlainTextMarkdown.normalize("　　缩进段落"))
        assertEquals("# 第一章 开始", PlainTextMarkdown.normalize("\uFEFF第一章 开始   "))
    }

    @Test
    fun emptyAndBlankInputProducesNothing() {
        assertEquals("", PlainTextMarkdown.normalize(""))
        assertEquals("", PlainTextMarkdown.normalize("   \n\n\t  "))
    }

    @Test
    fun titlesAreDetectedByTheSharedHelper() {
        assertNull(DocumentHeadings.plainTitle("普通正文"))
        assertNull(DocumentHeadings.plainTitle("在第57章 剥皮里"))
        assertNull(DocumentHeadings.plainTitle("第58章的内容仍未结束。"))
        assertEquals("第57章 剥皮", DocumentHeadings.titleOf("第57章 剥皮"))
        assertEquals("第一章 开始", DocumentHeadings.titleOf("  ## 第一章 开始 ###  "))
    }
}