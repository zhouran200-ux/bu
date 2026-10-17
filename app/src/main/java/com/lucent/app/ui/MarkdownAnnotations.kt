package com.lucent.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
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

private fun AnnotatedString.Builder.appendCitation(
    number: Int,
    accent: Color,
    ref: CitationRef,
    onClick: ((CitationRef) -> Unit)?,
    emphasized: Boolean
) {
    val style = SpanStyle(
        color = accent,
        fontSize = 11.sp,
        fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
        baselineShift = BaselineShift.Superscript
    )
    if (onClick == null) {
        withStyle(style) { append("[$number]") }
    } else {
        withLink(
            LinkAnnotation.Clickable(tag = ref.raw, styles = TextLinkStyles(style = style)) { onClick(ref) }
        ) { append("[$number]") }
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
                if (number > 0 && ref != null) appendCitation(number, accent, ref, citation.onClick, emphasized)
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
