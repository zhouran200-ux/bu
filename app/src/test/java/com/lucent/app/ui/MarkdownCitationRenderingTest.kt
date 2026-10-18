package com.lucent.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownCitationRenderingTest {
    private val first = "〔S1-1〕"
    private val second = "〔S2-2〕"
    private val full = "〔REF:7:abcdef:S3-3〕"

    private fun render(text: String, emphasized: Boolean = false, enabled: Boolean = true) = inlineAnnotated(
        text,
        Color.Black,
        Color.Blue,
        CitationDecor(markdownCitationNumbering(parseBlocks(text), enabled), null, 7L, "abcdef", enabled, emphasized)
    )

    @Test
    fun inlineCodeKeepsMarkersAndDoesNotConsumeNumbers() {
        val text = "`$first` $second $full"
        assertEquals(mapOf(second to 1, full to 2), markdownCitationNumbering(parseBlocks(text)))
        assertEquals("$first [1] [2]", render(text).text)
    }

    @Test
    fun multipleBackticksProtectInnerBackticksAndCitations() {
        val text = "``code `$first` **literal**`` $second"
        assertEquals(mapOf(second to 1), markdownCitationNumbering(parseBlocks(text)))
        assertEquals("code `$first` **literal** [1]", render(text).text)
    }

    @Test
    fun formattingAcrossACitationIsNotSplit() {
        for (delimiter in listOf("**", "__", "*", "_", "~~")) {
            val text = "${delimiter}before $first after$delimiter"
            assertEquals("before [1] after", render(text).text)
            assertEquals(mapOf(first to 1), markdownCitationNumbering(parseBlocks(text)))
        }
    }

    @Test
    fun inlineCodeWithinBoldKeepsItsMarkerLiteral() {
        val text = "**before `$first` after $second**"
        assertEquals("before $first after [1]", render(text).text)
        assertEquals(mapOf(second to 1), markdownCitationNumbering(parseBlocks(text)))
    }

    @Test
    fun codeContentIsNotClosedByAnInnerFormattingDelimiter() {
        val text = "**before `**$first` after $second"
        assertEquals(mapOf(second to 1), markdownCitationNumbering(parseBlocks(text)))
        assertTrue(render(text).text.contains("**$first"))
    }

    @Test
    fun fencedCodeAndLinkTargetsDoNotConsumeNumbers() {
        val text = "```\n$first\n```\n[label](https://example.test/$full)\n$second"
        assertEquals(mapOf(second to 1), markdownCitationNumbering(parseBlocks(text)))
        assertEquals(listOf(first), (parseBlocks(text).first() as MdBlock.Code).lines)
    }

    @Test
    fun sourceModeLeavesEveryMarkerLiteralAndSkipsNumbering() {
        val text = "$first $full **$second** `$first`"
        assertTrue(markdownCitationNumbering(parseBlocks(text), false).isEmpty())
        assertEquals("$first $full $second $first", render(text, enabled = false).text)
        assertFalse(markdownInlineTokens(text, false).any { it.kind == MdInlineKind.Citation })
    }

    @Test
    fun emphasisKeepsItalicAndBoldsCitationAndCodeSpans() {
        val rendered = render("*italic* `$first` $second", emphasized = true)
        assertEquals("italic $first [1]", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontStyle == FontStyle.Italic && it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.filter { it.item.fontWeight != null }.all { it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.start == 0 && it.end == rendered.length && it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun repeatedCitationsCollapseInsideOneBlock() {
        val text = "甲 $first 乙 $first 丙 $second"
        assertEquals("甲 [1] 乙  丙 [2]", render(text).text)
    }

    @Test
    fun everyNumberedMarkerGetsADistinctInlineBadge() {
        val text = "$first $second"
        val numbering = markdownCitationNumbering(parseBlocks(text))
        val contents = citationBadgeContents(numbering, Color.Blue, { _ -> }, 7L, "abcdef")
        assertEquals(setOf("cite:$first", "cite:$second"), contents.keys)
    }

    @Test
    fun boldCitationDoesNotOverrideItsWeightWithMedium() {
        val rendered = render("**$first**")
        assertEquals("[1]", rendered.text)
        assertTrue(rendered.spanStyles.filter { it.item.fontWeight != null }.all { it.item.fontWeight == FontWeight.Bold })
    }
}
