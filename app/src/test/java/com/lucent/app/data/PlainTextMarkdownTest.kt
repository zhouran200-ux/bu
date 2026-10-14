package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlainTextMarkdownTest {

    @Test
    fun everySourceLineEndingWithPunctuationBecomesItsOwnParagraph() {
        assertEquals(
            "第一句。\n\n第二句。\n\n第三句！",
            PlainTextMarkdown.normalize("第一句。\n第二句。\n第三句！")
        )
    }

    @Test
    fun hardWrappedLinesAreJoinedBackIntoOneParagraph() {
        val raw = "\u201C老婆一直打你电话，\n不通。家里的座机也打不通了。你生气了吗老公？\u201D"
        assertEquals(
            "\u201C老婆一直打你电话，不通。家里的座机也打不通了。你生气了吗老公？\u201D",
            PlainTextMarkdown.normalize(raw)
        )
    }

    @Test
    fun hardWrapSplitInsideAWordIsJoinedWithoutASpace() {
        assertEquals(
            "可能昨天晚上没充电就睡了，手机自己关掉了。",
            PlainTextMarkdown.normalize("可能昨天晚上没充\n电就睡了，手机自己关掉了。")
        )
    }

    @Test
    fun latinHardWrapKeepsAWordBoundary() {
        assertEquals(
            "This sentence is wrapped here and continues.",
            PlainTextMarkdown.normalize("This sentence is wrapped here\nand continues.")
        )
    }

    @Test
    fun blankLinesStillSeparateParagraphsAndCollapse() {
        assertEquals("一\n\n二", PlainTextMarkdown.normalize("一\n\n\n\n二"))
        assertEquals("一\n\n二", PlainTextMarkdown.normalize("一\r\n\r\n二"))
    }

    @Test
    fun chapterLinesBecomeHeadingsIncludingThoseWithoutASeparator() {
        assertEquals("# 第一百零三章 拥抱", PlainTextMarkdown.normalize("第一百零三章 拥抱"))
        assertEquals("# 第一百零八章小发现", PlainTextMarkdown.normalize("第一百零八章小发现"))
        assertEquals("# 第一百一十四章舌吻", PlainTextMarkdown.normalize("第一百一十四章舌吻"))
        assertEquals("# 第57章 剥皮", PlainTextMarkdown.normalize("第57章 剥皮"))
        assertEquals("# 第五十八章 新线索", PlainTextMarkdown.normalize("第五十八章 新线索"))
        assertEquals("# 第５９章：归来", PlainTextMarkdown.normalize("第５９章：归来"))
    }

    @Test
    fun chapterHeadingsStartANewParagraphForTheFollowingText() {
        assertEquals("# 第一百零五章 审核\n\n正文。", PlainTextMarkdown.normalize("第一百零五章 审核\n正文。"))
    }

    @Test
    fun numberedListItemsAreNotGluedToEachOtherOrToTheIntroLine() {
        val raw = "后面列出了迹像：\n1.爱打扮了。\n2.不让看手机。\n3.回家不打电话"
        assertEquals(
            "后面列出了迹像：\n\n1.爱打扮了。\n\n2.不让看手机。\n\n3.回家不打电话",
            PlainTextMarkdown.normalize(raw)
        )
    }

    @Test
    fun listIntroLineAndFirstItemStaySeparateEvenWithoutPunctuation() {
        assertEquals(
            "迹像如下：\n\n1.第一条",
            PlainTextMarkdown.normalize("迹像如下：\n1.第一条")
        )
    }

    @Test
    fun bodyLinesAreNotMistakenForHeadings() {
        assertEquals(
            "第二天上班。因为前一天出差，车被停在公司了。",
            PlainTextMarkdown.normalize("第二天上班。因为前一天出差，车被停在公司了。")
        )
        assertEquals(
            "第一条腿放上去放稳是最重要的，预示着好的开始是成功的一半。",
            PlainTextMarkdown.normalize("第一条腿放上去放稳是最重要的，预示着好的开始是成功的一半。")
        )
        assertEquals("第58章的内容仍未结束。", PlainTextMarkdown.normalize("第58章的内容仍未结束。"))
        assertEquals("他翻到了第58章，继续。", PlainTextMarkdown.normalize("他翻到了第58章，继续。"))
        assertEquals("#不是标题", PlainTextMarkdown.normalize("#不是标题"))
    }

    @Test
    fun existingMarkdownHeadingsAreKeptAsTheyAre() {
        assertEquals("## 第一章 开始\n\n正文", PlainTextMarkdown.normalize("## 第一章 开始\n正文"))
    }

    @Test
    fun surroundingWhitespaceAndBomAreNormalisedAway() {
        assertEquals("缩进段落", PlainTextMarkdown.normalize("　　缩进段落"))
        assertEquals("缩进段落", PlainTextMarkdown.normalize("    缩进段落"))
        assertEquals("段落", PlainTextMarkdown.normalize("段落   "))
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
        assertEquals("第一百零八章小发现", DocumentHeadings.titleOf("第一百零八章小发现"))
    }

    @Test
    fun paragraphEndsAreDetectedForContinuation() {
        assertTrue(DocumentHeadings.endsParagraph("他走了。"))
        assertTrue(DocumentHeadings.endsParagraph("你生气了吗老公？\u201D"))
        assertTrue(DocumentHeadings.endsParagraph(""))
        assertTrue(!DocumentHeadings.endsParagraph("老婆一直打你电话，"))
        assertTrue(!DocumentHeadings.endsParagraph("可能昨天晚上没充"))
    }
}