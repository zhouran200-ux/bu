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
}
