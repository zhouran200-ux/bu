package com.lucent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.lucent.app.data.CitationRef
import com.lucent.app.data.SourceCitations

internal class CitationDecor(
    val onClick: ((CitationRef) -> Unit)?,
    val sourceNoteId: Long?,
    val sourceRevision: String?,
    val enabled: Boolean,
    val emphasized: Boolean
)

internal class CitationCursor {
    val refs = mutableListOf<CitationRef>()
}

internal fun inlineAnnotated(
    line: String,
    textColor: Color,
    accent: Color,
    citation: CitationDecor,
    cursor: CitationCursor = CitationCursor()
): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = if (citation.emphasized) FontWeight.Bold else null)) {
        appendInline(line, textColor, accent, citation, citation.emphasized, cursor)
    }
}

private fun citationBadgeId(number: Int) = "cite:" + number

internal fun citationBadgeContents(
    refs: List<CitationRef>,
    accent: Color,
    onClick: ((CitationRef) -> Unit)?
): Map<String, InlineTextContent> {
    val click = onClick ?: return emptyMap()
    return refs.mapIndexed { index, ref ->
        val number = index + 1
        val digits = number.toString().length
        val width = if (digits == 1) 0.92f else 0.92f + 0.30f * (digits - 1)
        val shape = if (digits == 1) CircleShape else RoundedCornerShape(percent = 50)
        val content = InlineTextContent(
            Placeholder(
                width = width.em,
                height = 0.92.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom
            )
        ) {
            CitationBadge(number, accent, shape) { click(ref) }
        }
        citationBadgeId(number) to content
    }.toMap()
}

@Composable
private fun CitationBadge(
    number: Int,
    accent: Color,
    shape: Shape,
    onClick: () -> Unit
) {
    val dark = isDarkGlass()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(accent.copy(alpha = if (dark) 0.22f else 0.14f))
            .border(0.8.dp, accent.copy(alpha = if (dark) 0.40f else 0.30f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            color = accent,
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private fun AnnotatedString.Builder.appendInline(
    segment: String,
    textColor: Color,
    accent: Color,
    citation: CitationDecor,
    emphasized: Boolean,
    cursor: CitationCursor
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
                val ref = SourceCitations.parse(token.text, citation.sourceNoteId, citation.sourceRevision)
                if (ref != null) {
                    cursor.refs += ref
                    appendInlineContent(citationBadgeId(cursor.refs.size), "[${cursor.refs.size}]")
                } else {
                    append(token.text)
                }
            }
            else -> {
                val bold = emphasized || token.kind == MdInlineKind.Bold
                val style = SpanStyle(
                    fontWeight = if (bold) FontWeight.Bold else null,
                    fontStyle = if (token.kind == MdInlineKind.Italic) FontStyle.Italic else null,
                    textDecoration = if (token.kind == MdInlineKind.Strike) TextDecoration.LineThrough else null
                )
                withStyle(style) { appendInline(token.text, textColor, accent, citation, bold, cursor) }
            }
        }
    }
}
