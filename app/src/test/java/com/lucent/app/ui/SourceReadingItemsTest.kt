package com.lucent.app.ui

import com.lucent.app.data.SourceTagging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SourceReadingItemsTest {
    @Test
    fun headingsAreIndependentAndSlicesKeepOriginalOffsets() {
        val body = "# 第一章\r\n\r\n第一段。\r\n\r\n第二段。\r\n# 第二章\r\n正文。"
        val tagged = SourceTagging.tag("书", body)
        val items = sourceReadingItems(body, tagged)

        assertEquals(listOf("chapter:1", "slice:S1-1", "chapter:2", "slice:S2-1"), items.map { it.key })
        assertEquals("# 第一章", items.first().text)
        assertEquals("第一章", items.first().chapterTitle)
        tagged.slices.forEach { slice ->
            assertEquals(body.substring(slice.startOffset, slice.endOffset), items.single { it.sliceId == slice.id }.text)
        }
        assertTrue(items[1].text.contains("\r\n\r\n"))
        assertFalse(items.any { it.text.contains("〔CH") || it.text.contains("〔S：") })
    }

    @Test
    fun prefaceAndHeadingOnlyChaptersRetainTheirOwnTitles() {
        val body = "序言。\n\n# 第一章\n\n# 第二章\n正文。"
        val items = sourceReadingItems(body, SourceTagging.tag("书名", body))

        assertEquals(listOf("chapter:1", "slice:S1-1", "chapter:2", "chapter:3", "slice:S3-1"), items.map { it.key })
        assertEquals("书名", items.first().text)
        assertEquals("序言。", items[1].text)
        assertEquals("# 第一章", items[2].text)
        assertEquals("第二章", sourceSectionTitle(items, 4, "书名"))
        assertEquals("第二章", sourceSectionTitle(items, items.size, "书名"))
    }

    @Test
    fun longSliceRemainsOneReadingItemEvenWhenItContainsMultipleParagraphs() {
        val body = "# 第一章\n\n" + "甲".repeat(240) + "。\n\n" + "乙".repeat(240) + "。\n\n" + "丙".repeat(240) + "。"
        val tagged = SourceTagging.tag("书", body)
        val items = sourceReadingItems(body, tagged)

        assertEquals(tagged.slices.size, items.count { it.sliceId != null })
        assertEquals(tagged.slices.map { "slice:" + it.id }, items.filter { it.sliceId != null }.map { it.key })
        assertEquals(items.size, items.map { it.key }.distinct().size)
    }

    @Test
    fun focusRequiresMatchingNoteAndNonNullRevisionWithoutFallback() {
        val body = "# 第一章\n正文。"
        val items = sourceReadingItems(body, SourceTagging.tag("书", body))
        val focus = SourceFocus(7L, "S1-1", 1L, "revision")

        assertEquals(SourceFocusLocation.Found(1), sourceFocusLocation(focus, 7L, "revision", items))
        assertIs<SourceFocusLocation.Unavailable>(sourceFocusLocation(focus.copy(noteId = 8L), 7L, "revision", items))
        assertIs<SourceFocusLocation.Unavailable>(sourceFocusLocation(focus.copy(revision = null), 7L, "revision", items))
        assertIs<SourceFocusLocation.Unavailable>(sourceFocusLocation(focus.copy(revision = "old"), 7L, "revision", items))
        assertIs<SourceFocusLocation.Unavailable>(sourceFocusLocation(focus.copy(sliceId = "S1-99"), 7L, "revision", items))
    }

    @Test
    fun repeatedCitationRequestsResolveToTheSameSliceItem() {
        val body = "# 第一章\n正文。"
        val items = sourceReadingItems(body, SourceTagging.tag("书", body))
        val first = SourceFocus(7L, "S1-1", 1L, "revision")
        val second = first.copy(requestId = 2L)

        assertEquals(SourceFocusLocation.Found(1), sourceFocusLocation(first, 7L, "revision", items))
        assertEquals(SourceFocusLocation.Found(1), sourceFocusLocation(second, 7L, "revision", items))
    }

    @Test
    fun blankSourcesHaveNoSyntheticReadingItems() {
        val body = " \r\n "
        val items = sourceReadingItems(body, SourceTagging.tag("空文档", body))

        assertTrue(items.isEmpty())
        assertEquals("空文档", sourceSectionTitle(items, 0, "空文档"))
    }
}
