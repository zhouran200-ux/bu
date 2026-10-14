package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleNovelConversionTest {

    private val sample = """
        第一百零三章 拥抱
        她不喜欢有人拍她得屁股。最少我这样做，她是反感的。
        我没关机。”把手机掏出来看了一下。可能昨天晚上没充
        电就睡了，手机自己关掉了。
        ……
        后面列出了他判断出轨的迹像：
        1.她会用很多你没见过的有趣表情包。
        2.下班了不爱回家，对家里人冷淡。
        第一百零八章小发现
        那黑色的管柱嵌入那白色的身体里。
    """.trimIndent()

    private fun convert() = PlainTextMarkdown.normalize(sample)

    @Test
    fun chapterTitlesWithAndWithoutSpacingBecomeHeadings() {
        val markdown = convert()
        assertTrue(markdown.contains("# 第一百零三章 拥抱"), markdown)
        assertTrue(markdown.contains("# 第一百零八章小发现"), markdown)
    }

    @Test
    fun hardWrappedParagraphIsJoined() {
        assertTrue(
            convert().contains("我没关机。”把手机掏出来看了一下。可能昨天晚上没充电就睡了，手机自己关掉了。"),
            convert()
        )
    }

    @Test
    fun sceneSeparatorBecomesARuleOnItsOwnLine() {
        assertTrue(convert().contains("\n\n---\n\n"), convert())
    }

    @Test
    fun listItemsStaySeparateFromIntroAndEachOther() {
        val blocks = convert().split("\n\n")
        assertTrue(blocks.contains("后面列出了他判断出轨的迹像："), blocks.toString())
        assertTrue(blocks.contains("1.她会用很多你没见过的有趣表情包。"), blocks.toString())
        assertTrue(blocks.contains("2.下班了不爱回家，对家里人冷淡。"), blocks.toString())
    }

    @Test
    fun headingCountMatchesTheSampleChapterCount() {
        assertEquals(2, convert().lines().count { it.startsWith("# ") })
    }
}