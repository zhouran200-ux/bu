package com.lucent.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    private val style = MarkdownStyle(
        textColor = Color.Black,
        mutedColor = Color.Gray,
        accent = Color.Blue,
        citationsEnabled = true,
        emphasized = false,
        sourceNoteId = 7L,
        sourceRevision = "abcdef",
        clickable = true
    )

    @Test fun matchesFullParsingForEveryPrefix() {
        val text = "# heading\n\nfirst paragraph\n\n- bullet\n- bullet two\n\n```\ncode\n```\n\nlast"
        val renderer = MarkdownRenderer()
        for (end in 0..text.length) {
            assertEquals(parseBlocks(text.take(end)), renderer.render(text.take(end), style, null).blocks)
        }
    }

    @Test fun growingTextKeepsClosedBlocksAndTheirSpansByIdentity() {
        val renderer = MarkdownRenderer()
        val first = renderer.render("first\n\nsecond", style, null)
        val next = renderer.render("first\n\nsecond\n\nthird", style, null)
        assertEquals(parseBlocks("first\n\nsecond\n\nthird"), next.blocks)
        assertSame(first.blocks[0], next.blocks[0])
        assertSame(first.annotated[0], next.annotated[0])
    }

    @Test fun citationNumbersStayStableWhenOnlyTheTailGrows() {
        val renderer = MarkdownRenderer()
        val first = renderer.render("甲〔S1-1〕\n\n乙", style, null)
        assertEquals("甲[1]", first.annotated[0].text)
        val next = renderer.render("甲〔S1-1〕\n\n乙〔S2-2〕", style, null)
        assertEquals("甲[1]", next.annotated[0].text)
        assertEquals("乙[2]", next.annotated[1].text)
        assertEquals(2, next.refs.size)
        assertSame(first.annotated[0], next.annotated[0])
    }

    @Test fun rewritingAnEarlierBlockRenumbersEverythingAfterIt() {
        val renderer = MarkdownRenderer()
        renderer.render("乙〔S2-2〕\n\n丙", style, null)
        val next = renderer.render("甲〔S1-1〕\n\n乙〔S2-2〕\n\n丙", style, null)
        assertEquals(listOf("甲[1]", "乙[2]", "丙"), next.annotated.map { it.text })
        assertEquals(2, next.refs.size)
    }

    @Test fun shorteningTheTextDropsTheStaleTail() {
        val renderer = MarkdownRenderer()
        renderer.render("first\n\nsecond\n\nthird", style, null)
        val next = renderer.render("first", style, null)
        assertEquals(parseBlocks("first"), next.blocks)
        assertEquals(1, next.annotated.size)
        assertEquals(0, next.refs.size)
    }

    @Test fun aStyleChangeReannotatesEveryBlock() {
        val renderer = MarkdownRenderer()
        val first = renderer.render("# title\n\nbody", style, null)
        val next = renderer.render("# title\n\nbody", style.copy(emphasized = true), null)
        assertNotSame(first.annotated[0], next.annotated[0])
        assertNotSame(first.annotated[1], next.annotated[1])
        assertTrue(next.annotated[1].spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
    }

    @Test fun blankAndRuleBlocksRenderEmptyTextWithoutConsumingCitations() {
        val renderer = MarkdownRenderer()
        val next = renderer.render("a\n\n---\n\n〔S1-1〕", style, null)
        assertEquals(1, next.refs.size)
        assertEquals("", next.annotated[1].text)
        assertEquals("[1]", next.annotated.last().text)
    }
}
