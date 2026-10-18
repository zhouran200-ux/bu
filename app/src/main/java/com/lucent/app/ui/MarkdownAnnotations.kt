package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.lucent.app.data.CitationRef
import com.lucent.app.data.SourceCitations

internal class CitationDecor(
    val numbering: Map<String, Int>,
    val onClick: ((CitationRef) -> Unit)?,
    val sourceNoteId: Long?,
    val sourceRevision: String?,
    val enabled: Boolean,
    val emphasized: Boolean
)

internal fun inlineAnnotated(
    line: String,
    textColor: Color,
    accent: Color,
    citation: CitationDecor
): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = if (citation.emphasized) FontWeight.Bold else null)) {
        appendInline(line, textColor, accent, citation, citation.emphasized)
    }
}

private fun citationBadgeId(ref: CitationRef) = "cite:" + ref.raw

private fun AnnotatedString.Builder.appendCitation(
    number: Int,
    ref: CitationRef
) {
    appendInlineContent(citationBadgeId(ref), "[$number]")
}

internal fun citationBadgeContents(
    numbering: Map<String, Int>,
    accent: Color,
    onClick: ((CitationRef) -> Unit)?,
    sourceNoteId: Long?,
    sourceRevision: String?
): Map<String, InlineTextContent> {
    if (onClick == null) return emptyMap()
    val badgeColor = lerp(accent, Color.Black, 0.15f)
    val onBadge = if (badgeColor.luminance() > 0.5f) Color.Black else Color.White
    return numbering.entries.associate { (raw, number) ->
        val ref = SourceCitations.parse(raw, sourceNoteId, sourceRevision) ?: return@associate raw to null
        val digits = number.toString().length
        val content = InlineTextContent(
            Placeholder(
                width = (0.95f + 0.42f * digits).em,
                height = 0.95.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom
            )
        ) {
            CitationBadge(number, badgeColor, onBadge, onClick)
        }
        citationBadgeId(ref) to content
    }.filterValues { it != null }.mapValues { it.value!! }
}

@Composable
private fun CitationBadge(
    number: Int,
    accent: Color,
    onAccent: Color,
    onClick: (CitationRef) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lerp(accent, Color.Black, 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            color = onAccent,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun AnnotatedString.Builder.appendInline(
    segment: String,
    textColor: Color,
    accent: Color,
    citation: CitationDecor,
    emphasized: Boolean
) {
    for (token in markdownInlineTokens(segment, citation.enabled)) {
        when (token.kind) {
            MdInlineKind.Plain -> append(token.text)
            MdInlineKind.Code -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = textColor.copy(alpha = 0.10f),
                    fontWeight = if (emphasized) FontWeight.Bold else null
                )
            ) { append(token.text) }
            MdInlineKind.Link -> withLink(
                LinkAnnotation.Url(
                    url = token.url!!,
                    styles = TextLinkStyles(style = SpanStyle(
                        color = accent,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = if (emphasized) FontWeight.Bold else null
                    ))
                )
            ) { append(token.text) }
            MdInlineKind.Citation -> {
                val number = citation.numbering[token.text] ?: 0
                val ref = SourceCitations.parse(token.text, citation.sourceNoteId, citation.sourceRevision)
                if (number > 0 && ref != null) appendCitation(number, ref)
                else append(token.text)
            }
            else -> {
                val bold = emphasized || token.kind == MdInlineKind.Bold
                val style = SpanStyle(
                    fontWeight = if (bold) FontWeight.Bold else null,
                    fontStyle = if (token.kind == MdInlineKind.Italic) FontStyle.Italic else null,
                    textDecoration = if (token.kind == MdInlineKind.Strike) TextDecoration.LineThrough else null
                )
                withStyle(style) { appendInline(token.text, textColor, accent, citation, bold) }
            }
        }
    }
}
