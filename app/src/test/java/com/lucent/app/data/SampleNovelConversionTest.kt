package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleNovelConversionTest {

    private val sample = """
        第一章 起点
        她走了过来。他点了点头。
        他站起身，
        看了看窗外。
        ……
        迹像如下：
        1.第一条描述。
        2.第二条描述。
        第 二 章 空格式标题
        他说：“好的。”
    """.trimIndent()

    private fun convert() = PlainTextMarkdown.normalize(sample)

    @Test
    fun chapterTitlesWithAndWithoutSpacingBecomeHeadings() {
        val markdown = convert()
        assertTrue(markdown.contains("# 第一章 起点"), markdown)
        assertTrue(markdown.contains("# 第 二 章 空格式标题"), markdown)
    }

    @Test
    fun hardWrappedParagraphIsJoined() {
        assertTrue(convert().contains("他站起身，看了看窗外。"), convert())
    }

    @Test
    fun sceneSeparatorBecomesARuleOnItsOwnLine() {
        assertTrue(convert().contains("\n\n---\n\n"), convert())
    }

    @Test
    fun listItemsStaySeparateFromIntroAndEachOther() {
        val blocks = convert().split("\n\n")
        assertTrue(blocks.contains("迹像如下："), blocks.toString())
        assertTrue(blocks.contains("1.第一条描述。"), blocks.toString())
        assertTrue(blocks.contains("2.第二条描述。"), blocks.toString())
    }

    @Test
    fun headingCountMatchesTheSampleChapterCount() {
        assertEquals(2, convert().lines().count { it.startsWith("# ") })
    }

    @Test
    fun headingsAreMarkdownHeadingsOnTheirOwnBlock() {
        val blocks = convert().split("\n\n")
        assertEquals(0, blocks.indexOfFirst { it.startsWith("# ") })
        assertEquals("# 第一章 起点", blocks[0])
        assertTrue(blocks[1].isNotBlank())
    }

    @Test
    fun onlyTheQuotedSpeechIsItalicisedNotTheNarration() {
        val markdown = convert()
        assertTrue(markdown.contains("\u201C*好的。*\u201D"), markdown)
        assertTrue(markdown.contains("他说："), markdown)
        assertTrue(!markdown.contains("*他说"), markdown)
    }

    @Test
    fun narrationAfterASpeechStaysUpright() {
        assertEquals(
            "他说：\u201C*好。*\u201D然后走了。",
            PlainTextMarkdown.italiciseDialogue("他说：\u201C好。\u201D然后走了。")
        )
    }

    @Test
    fun standaloneSpeechLineIsItalic() {
        assertEquals("\u201C*好的。*\u201D", PlainTextMarkdown.italiciseDialogue("\u201C好的。\u201D"))
    }

    @Test
    fun unpairedQuotesDoNotBreakTheParagraph() {
        assertEquals("他回来了？\u201D", PlainTextMarkdown.italiciseDialogue("他回来了？\u201D"))
        assertEquals("\u201C他回来了。", PlainTextMarkdown.italiciseDialogue("\u201C他回来了。"))
    }

    @Test
    fun sceneBreakAndHeadingAreNeverItalicised() {
        assertEquals("---", PlainTextMarkdown.italiciseDialogue("---"))
        assertEquals("# 第一章 起点", PlainTextMarkdown.italiciseDialogue("# 第一章 起点"))
    }
}