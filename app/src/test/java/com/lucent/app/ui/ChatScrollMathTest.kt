package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatScrollMathTest {

    @Test
    fun emptyListIsNotScrollable() {
        val m = ChatScrollMath.metrics(
            totalItems = 0, averageItemSize = 0, spacing = 0,
            viewport = 1000, leadIndex = 0, leadOffset = 0
        )
        assertFalse(m.scrollable)
        assertEquals(0f, m.fraction)
    }

    @Test
    fun shortContentThatFitsIsNotScrollable() {
        val m = ChatScrollMath.metrics(
            totalItems = 2, averageItemSize = 100, spacing = 10,
            viewport = 1000, leadIndex = 0, leadOffset = 0
        )
        assertFalse(m.scrollable)
    }

    @Test
    fun atTopFractionIsZeroAtBottomApproachesOne() {
        val top = ChatScrollMath.metrics(
            totalItems = 50, averageItemSize = 200, spacing = 0,
            viewport = 1000, leadIndex = 0, leadOffset = 0
        )
        assertTrue(top.scrollable)
        assertEquals(0f, top.fraction)

        val bottom = ChatScrollMath.metrics(
            totalItems = 50, averageItemSize = 200, spacing = 0,
            viewport = 1000, leadIndex = 45, leadOffset = 0
        )
        assertEquals(1f, bottom.fraction)
    }

    @Test
    fun thumbShrinksWithLongerContentButHasFloor() {
        val longDoc = ChatScrollMath.metrics(
            totalItems = 1000, averageItemSize = 200, spacing = 0,
            viewport = 1000, leadIndex = 0, leadOffset = 0
        )
        assertEquals(0.08f, longDoc.thumbFraction)
    }

    @Test
    fun targetAtZeroIsFirstItem() {
        val t = ChatScrollMath.targetForFraction(
            totalItems = 50, averageItemSize = 200, spacing = 0,
            viewport = 1000, fraction = 0f
        )
        assertEquals(0, t.index)
        assertEquals(0, t.offset)
    }

    @Test
    fun targetAtOneLandsNearBottomWithinBounds() {
        val total = 50
        val t = ChatScrollMath.targetForFraction(
            totalItems = total, averageItemSize = 200, spacing = 0,
            viewport = 1000, fraction = 1f
        )
        assertTrue(t.index in 0 until total)
        assertTrue(t.offset >= 0)
    }

    @Test
    fun targetFractionIsClampedForOutOfRangeInput() {
        val low = ChatScrollMath.targetForFraction(50, 200, 0, 1000, -5f)
        assertEquals(0, low.index)
        val high = ChatScrollMath.targetForFraction(50, 200, 0, 1000, 9f)
        assertTrue(high.index in 0 until 50)
    }

    @Test
    fun programmaticScrollDoesNotChangeFollowState() {
        assertTrue(
            ChatScrollMath.shouldFollowAfterScroll(
                wasFollowing = true, atBottom = true, userScrolling = false
            )
        )
        assertFalse(
            ChatScrollMath.shouldFollowAfterScroll(
                wasFollowing = false, atBottom = true, userScrolling = false
            )
        )
    }

    @Test
    fun userScrollUpStopsFollowingUserScrollToBottomResumes() {
        assertFalse(
            ChatScrollMath.shouldFollowAfterScroll(
                wasFollowing = true, atBottom = false, userScrolling = true
            )
        )
        assertTrue(
            ChatScrollMath.shouldFollowAfterScroll(
                wasFollowing = false, atBottom = true, userScrolling = true
            )
        )
    }

    @Test
    fun pausedReadingStaysPausedAfterReleaseAndIncomingLayoutChanges() {
        assertFalse(ChatScrollMath.shouldFollowAfterScroll(false, false, true))
        assertFalse(ChatScrollMath.shouldFollowAfterScroll(false, false, false))
        assertFalse(ChatScrollMath.shouldFollowAfterScroll(false, true, false))
        assertTrue(ChatScrollMath.shouldFollowAfterScroll(false, true, true))
    }
}

class DocumentSectionTest {

    @Test
    fun plainTextWithoutHeadingsIsOneSection() {
        val sections = splitDocumentSections("第一行\n第二行\n第三行")
        assertEquals(1, sections.size)
    }

    @Test
    fun headingsSplitIntoSections() {
        val body = "# 一\n正文一\n# 二\n正文二\n# 三\n正文三"
        val sections = splitDocumentSections(body)
        assertEquals(3, sections.size)
    }

    @Test
    fun leadingTextBeforeFirstHeadingIsItsOwnSection() {
        val body = "开头说明\n# 第一章\n内容"
        val sections = splitDocumentSections(body)
        assertEquals(2, sections.size)
    }

    @Test
    fun blankBodyProducesNoSections() {
        assertTrue(splitDocumentSections("   \n\n  ").isEmpty())
    }

    @Test
    fun activeSectionReflectsScrollPosition() {
        val tops = listOf(0, 500, 1200)
        assertEquals(1, activeSectionNumber(tops, 0))
        assertEquals(1, activeSectionNumber(tops, 400))
        assertEquals(2, activeSectionNumber(tops, 600))
        assertEquals(3, activeSectionNumber(tops, 1300))
    }

    @Test
    fun unmeasuredSectionsAreSkipped() {
        val tops = listOf<Int?>(0, null, 1000)
        assertEquals(1, activeSectionNumber(tops, 500))
        assertEquals(3, activeSectionNumber(tops, 1000))
    }

    @Test
    fun smallSectionsStayOneBlockEach() {
        val blocks = splitRenderBlocks("# 一\n短内容\n# 二\n也很短", maxChars = 1200)
        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0].sectionIndex)
        assertEquals(1, blocks[1].sectionIndex)
    }

    @Test
    fun oversizedSectionIsSplitButKeepsSectionIndex() {
        val huge = "开头\n\n" + "句子。".repeat(2000)
        val blocks = splitRenderBlocks(huge, maxChars = 500)
        assertTrue(blocks.size > 1)
        assertTrue(blocks.all { it.sectionIndex == 0 })
        assertTrue(blocks.all { it.text.isNotBlank() })
    }

    @Test
    fun splittingPreservesAllVisibleCharacters() {
        val body = "# 标题\n" + "字".repeat(5000)
        val blocks = splitRenderBlocks(body, maxChars = 400)
        val merged = blocks.joinToString("") { it.text }.filter { it == '字' }
        assertEquals(5000, merged.length)
    }

    @Test
    fun blankBodyProducesNoBlocks() {
        assertTrue(splitRenderBlocks("   \n\n  ").isEmpty())
    }

    @Test
    fun longSectionsAreChunkedAtLineBoundariesNotInsideALine() {
        val lines = (1..40).map { "第${it}行内容" }
        val body = lines.joinToString("\n")
        val blocks = splitRenderBlocks(body, maxChars = 12)
        assertTrue(blocks.size > 1)
        blocks.forEach { block ->
            block.text.lines().filter { it.isNotBlank() }.forEach { line ->
                assertTrue(lines.contains(line), "分块把一行切成了半截: $line")
            }
        }
        assertEquals(lines, blocks.flatMap { it.text.lines() }.filter { it.isNotBlank() })
    }

    @Test
    fun aSingleLongParagraphIsKeptWholeInsteadOfBeingSplitMidParagraph() {
        val paragraph = "字".repeat(1500)
        val body = "开头段落\n\n$paragraph"
        val blocks = splitRenderBlocks(body, maxChars = 400)
        assertTrue(blocks.any { it.text == paragraph }, "长段落不应被拆开")
    }

    @Test
    fun plainTextChaptersAreRecognizedWithoutBlankLines() {
        val body = "第57章 剥皮\n正文一\n第五十八章 新线索\n正文二\n第５９章：归来\n正文三"
        val blocks = splitRenderBlocks(body)

        assertEquals(3, blocks.size)
        assertEquals(
            listOf("第57章 剥皮", "第五十八章 新线索", "第５９章：归来"),
            blocks.map { it.sectionTitle }
        )
        assertEquals(listOf(0, 1, 2), blocks.map { it.sectionIndex })
    }

    @Test
    fun chapterMentionsInParagraphsDoNotCreateSections() {
        val body = "第57章 剥皮\n他翻到了第58章，继续读下去。\n第58章的内容仍未结束。\n#不是标题"
        assertEquals(1, splitDocumentSections(body).size)
    }

    @Test
    fun headingLabelsRemoveMarkdownMarkersAndFileBom() {
        assertEquals("第一章 开始", documentSectionTitle("  ## 第一章 开始 ###  "))
        assertEquals("第57章 剥皮", documentSectionTitle("\uFEFF第57章 剥皮"))
    }

    @Test
    fun headingsInsideCodeFencesDoNotCreateSections() {
        val body = "# 文档\n```text\n# 代码标题\n第57章 剥皮\n```\n## 下一节\n正文"
        val blocks = splitRenderBlocks(body)
        assertEquals(listOf("文档", "下一节"), blocks.map { it.sectionTitle })
    }

    @Test
    fun longChaptersKeepTheirTitlesAcrossEveryRenderBlock() {
        val body = "第57章 剥皮\n" + "长段正文。".repeat(200) + "\n第58章 新线索\n另一章正文"
        val blocks = splitRenderBlocks(body, maxChars = 120)
        val nextChapter = blocks.indexOfFirst { it.sectionIndex == 1 }

        assertTrue(nextChapter > 1)
        blocks.take(nextChapter).indices.forEach { index ->
            assertEquals("第57章 剥皮", currentDocumentSectionTitle(blocks, index, "书名"))
        }
        assertEquals("第58章 新线索", currentDocumentSectionTitle(blocks, nextChapter, "书名"))
    }

    @Test
    fun aSingleChapterStillProvidesItsActualTitle() {
        val blocks = splitRenderBlocks("第57章 剥皮\n正文")
        assertEquals("第57章 剥皮", currentDocumentSectionTitle(blocks, 0, "书名"))
    }

    @Test
    fun headinglessAndEmptyDocumentsUseReadableFallbacks() {
        val blocks = splitRenderBlocks("没有章节标题的正文")
        assertEquals("导入的资料", currentDocumentSectionTitle(blocks, 0, "导入的资料"))
        assertEquals("正文", currentDocumentSectionTitle(blocks, 0, ""))
        assertEquals("资料", currentDocumentSectionTitle(emptyList(), 0, "资料"))
    }

    @Test
    fun footerAndOutOfRangePositionsKeepTheNearestChapter() {
        val blocks = splitRenderBlocks("# 开头\n一\n# 结尾\n二")
        assertEquals("开头", currentDocumentSectionTitle(blocks, -1, "资料"))
        assertEquals("结尾", currentDocumentSectionTitle(blocks, blocks.size, "资料"))
        assertEquals("结尾", currentDocumentSectionTitle(blocks, Int.MAX_VALUE, "资料"))
    }

    @Test
    fun switchingDocumentsDoesNotReuseThePreviousChapterTitle() {
        val first = splitRenderBlocks("第57章 剥皮\n正文")
        val second = splitRenderBlocks("第1章 新书\n正文")
        assertEquals("第57章 剥皮", currentDocumentSectionTitle(first, 0, "旧书"))
        assertEquals("第1章 新书", currentDocumentSectionTitle(second, 0, "新书"))
    }
}
