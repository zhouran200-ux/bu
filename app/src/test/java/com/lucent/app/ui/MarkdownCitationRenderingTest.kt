package com.lucent.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.lucent.app.data.SourceCitations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownCitationRenderingTest {
    private val first = "〔S1-1〕"
    private val second = "〔S2-2〕"
    private val full = "〔REF:7:abcdef:S3-3〕"

    private fun render(text: String, emphasized: Boolean = false, enabled: Boolean = true): AnnotatedString =
        inlineAnnotated(
            text,
            Color.Black,
            Color.Blue,
            CitationDecor(null, 7L, "abcdef", enabled, emphasized)
        )

    @Test
    fun citationsAreNumberedByAppearanceOrder() {
        assertEquals("甲 [1] 乙 [2] 丙 [3]", render("甲 $first 乙 $second 丙 $full").text)
    }

    @Test
    fun theSameCitationReappearingGetsTheNextNumber() {
        assertEquals("甲 [1] 乙 [2]", render("甲 $first 乙 $first").text)
    }

    @Test
    fun numberingRestartsForEachReply() {
        assertEquals("[1]", render(first).text)
        assertEquals("[1]", render(second).text)
    }

    @Test
    fun inlineCodeKeepsMarkersAndDoesNotConsumeNumbers() {
        assertEquals("$first [1] [2]", render("`$first` $second $full").text)
    }

    @Test
    fun multipleBackticksProtectInnerBackticksAndCitations() {
        assertEquals("code `$first` **literal** [1]", render("``code `$first` **literal**`` $second").text)
    }

    @Test
    fun formattingAcrossACitationIsNotSplit() {
        for (delimiter in listOf("**", "__", "*", "_", "~~")) {
            assertEquals("before [1] after", render("${delimiter}before $first after$delimiter").text)
        }
    }

    @Test
    fun inlineCodeWithinBoldKeepsItsMarkerLiteral() {
        assertEquals("before $first after [1]", render("**before `$first` after $second**").text)
    }

    @Test
    fun codeContentIsNotClosedByAnInnerFormattingDelimiter() {
        assertTrue(render("**before `**$first` after $second").text.contains("**$first"))
    }

    @Test
    fun fencedCodeAndLinkTargetsDoNotConsumeNumbers() {
        val text = "```" + 10.toChar() + "$first" + 10.toChar() + "```" + 10.toChar() + "[label](https://example.test/$full) $second"
        val blocks = parseBlocks(text)
        assertEquals(listOf(first), (blocks.first() as MdBlock.Code).lines)
        assertEquals("label [1]", render("[label](https://example.test/$full) $second").text)
    }

    @Test
    fun sourceModeLeavesEveryMarkerLiteralAndSkipsNumbering() {
        val text = "$first $full **$second** `$first`"
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
    fun everyCitationOccurrenceGetsItsOwnBadge() {
        val refs = listOf(
            SourceCitations.parse(first, 7L, "abcdef")!!,
            SourceCitations.parse(second, 7L, "abcdef")!!
        )
        val contents = citationBadgeContents(refs, Color.Blue) { _ -> }
        assertEquals(setOf("cite:1", "cite:2"), contents.keys)
    }

    @Test
    fun boldCitationDoesNotOverrideItsWeightWithMedium() {
        val rendered = render("**$first**")
        assertEquals("[1]", rendered.text)
        assertTrue(rendered.spanStyles.filter { it.item.fontWeight != null }.all { it.item.fontWeight == FontWeight.Bold })
    }
}
