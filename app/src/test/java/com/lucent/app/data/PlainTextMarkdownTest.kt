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
        assertEquals(
            "他站起身，看了看窗外。",
            PlainTextMarkdown.normalize("他站起身，\n看了看窗外。")
        )
    }

    @Test
    fun hardWrapSplitInsideAWordIsJoinedWithoutASpace() {
        assertEquals(
            "这是一段被拆开的文字，拼回一句。",
            PlainTextMarkdown.normalize("这是一段被拆开的文\n字，拼回一句。")
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
    fun chapterFormsUsedByChineseWebNovelsAllBecomeHeadings() {
        assertEquals("# 第一章 起点", PlainTextMarkdown.normalize("第一章 起点"))
        assertEquals("# 第 一 章 起点", PlainTextMarkdown.normalize("第 一 章 起点"))
        assertEquals("# 第1章 起点", PlainTextMarkdown.normalize("第1章 起点"))
        assertEquals("# 第01章 起点", PlainTextMarkdown.normalize("第01章 起点"))
        assertEquals("# 第１章 起点", PlainTextMarkdown.normalize("第１章 起点"))
        assertEquals("# 01章 起点", PlainTextMarkdown.normalize("01章 起点"))
        assertEquals("# 1章 起点", PlainTextMarkdown.normalize("1章 起点"))
        assertEquals("# 第108章无分隔", PlainTextMarkdown.normalize("第108章无分隔"))
        assertEquals("# 第一百零八章 无分隔", PlainTextMarkdown.normalize("第一百零八章 无分隔"))
        assertEquals("# 第12回 回目", PlainTextMarkdown.normalize("第12回 回目"))
        assertEquals("# 第3节 小节", PlainTextMarkdown.normalize("第3节 小节"))
        assertEquals("# 第2卷 卷二", PlainTextMarkdown.normalize("第2卷 卷二"))
        assertEquals("# 第4篇 篇名", PlainTextMarkdown.normalize("第4篇 篇名"))
        assertEquals("# 第5集 集名", PlainTextMarkdown.normalize("第5集 集名"))
        assertEquals("# 第6话 话名", PlainTextMarkdown.normalize("第6话 话名"))
    }

    @Test
    fun specialChapterMarkersBecomeHeadings() {
        assertEquals("# 序章", PlainTextMarkdown.normalize("序章"))
        assertEquals("# 楔子", PlainTextMarkdown.normalize("楔子"))
        assertEquals("# 引子", PlainTextMarkdown.normalize("引子"))
        assertEquals("# 尾声", PlainTextMarkdown.normalize("尾声"))
        assertEquals("# 番外篇 别传", PlainTextMarkdown.normalize("番外篇 别传"))
        assertEquals("# Chapter 12", PlainTextMarkdown.normalize("Chapter 12"))
        assertEquals("# 【第7章】 带括号", PlainTextMarkdown.normalize("【第7章】 带括号"))
    }

    @Test
    fun chapterHeadingsStartANewParagraphForTheFollowingText() {
        assertEquals("# 第一章 起点\n\n正文。", PlainTextMarkdown.normalize("第一章 起点\n正文。"))
    }

    @Test
    fun bodyLinesAreNotMistakenForHeadings() {
        assertEquals(
            "第二天上班。因为出差，车停在公司了。",
            PlainTextMarkdown.normalize("第二天上班。因为出差，车停在公司了。")
        )
        assertEquals(
            "第一条腿放稳是最重要的，好的开始是成功的一半。",
            PlainTextMarkdown.normalize("第一条腿放稳是最重要的，好的开始是成功的一半。")
        )
        assertEquals("第58章的内容仍未结束。", PlainTextMarkdown.normalize("第58章的内容仍未结束。"))
        assertEquals("他翻到了第58章，继续。", PlainTextMarkdown.normalize("他翻到了第58章，继续。"))
        assertEquals("12章鱼很好吃。", PlainTextMarkdown.normalize("12章鱼很好吃。"))
        assertEquals("楔子是他写的。", PlainTextMarkdown.normalize("楔子是他写的。"))
        assertEquals("#不是标题", PlainTextMarkdown.normalize("#不是标题"))
    }

    @Test
    fun existingMarkdownHeadingsAreKeptAsTheyAre() {
        assertEquals("## 第一章 起点\n\n正文", PlainTextMarkdown.normalize("## 第一章 起点\n正文"))
    }

    @Test
    fun sceneSeparatorLinesBecomeMarkdownRules() {
        assertEquals("上一段。\n\n---\n\n下一段。", PlainTextMarkdown.normalize("上一段。\n……\n下一段。"))
        assertEquals("---", PlainTextMarkdown.normalize("……"))
        assertEquals("---", PlainTextMarkdown.normalize("......"))
        assertEquals("---", PlainTextMarkdown.normalize("——"))
    }

    @Test
    fun ordinaryEllipsisInsideASentenceIsNotASceneBreak() {
        assertEquals("他走了……然后回头。", PlainTextMarkdown.normalize("他走了……然后回头。"))
        assertEquals("也许吧…", PlainTextMarkdown.normalize("也许吧…"))
    }

    @Test
    fun numberedListItemsAreNotGluedToEachOtherOrToTheIntroLine() {
        val raw = "迹像如下：\n1.第一条。\n2.第二条。\n3.第三条"
        assertEquals(
            "迹像如下：\n\n1.第一条。\n\n2.第二条。\n\n3.第三条",
            PlainTextMarkdown.normalize(raw)
        )
    }

    @Test
    fun dialogueIsItalicWhileNarrationStaysUpright() {
        assertEquals("他说：\u201C*好的。*\u201D然后走了。", PlainTextMarkdown.italiciseDialogue("他说：\u201C好的。\u201D然后走了。"))
        assertEquals("\u201C*好的。*\u201D", PlainTextMarkdown.italiciseDialogue("\u201C好的。\u201D"))
        assertEquals("他点了点头。", PlainTextMarkdown.italiciseDialogue("他点了点头。"))
        assertEquals("---", PlainTextMarkdown.italiciseDialogue("---"))
        assertEquals("# 第一章 起点", PlainTextMarkdown.italiciseDialogue("# 第一章 起点"))
    }

    @Test
    fun surroundingWhitespaceAndBomAreNormalisedAway() {
        assertEquals("缩进段落", PlainTextMarkdown.normalize("　　缩进段落"))
        assertEquals("缩进段落", PlainTextMarkdown.normalize("    缩进段落"))
        assertEquals("段落", PlainTextMarkdown.normalize("段落   "))
        assertEquals("# 第一章 起点", PlainTextMarkdown.normalize("\uFEFF第一章 起点   "))
    }

    @Test
    fun emptyAndBlankInputProducesNothing() {
        assertEquals("", PlainTextMarkdown.normalize(""))
        assertEquals("", PlainTextMarkdown.normalize("   \n\n\t  "))
    }

    @Test
    fun titlesAreDetectedByTheSharedHelper() {
        assertNull(DocumentHeadings.plainTitle("普通正文"))
        assertNull(DocumentHeadings.plainTitle("在第57章 里"))
        assertNull(DocumentHeadings.plainTitle("第58章的内容仍未结束。"))
        assertEquals("第57章 起点", DocumentHeadings.titleOf("第57章 起点"))
        assertEquals("第108章无分隔", DocumentHeadings.titleOf("第108章无分隔"))
        assertEquals("第一章 起点", DocumentHeadings.titleOf("  ## 第一章 起点 ###  "))
    }

    @Test
    fun paragraphEndsAreDetectedForContinuation() {
        assertTrue(DocumentHeadings.endsParagraph("他走了。"))
        assertTrue(DocumentHeadings.endsParagraph("你回来了吗？\u201D"))
        assertTrue(DocumentHeadings.endsParagraph(""))
        assertTrue(!DocumentHeadings.endsParagraph("他站起身，"))
        assertTrue(!DocumentHeadings.endsParagraph("这是一段被拆开的文"))
    }
}