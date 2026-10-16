package com.lucent.app.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.CitationRef
import com.lucent.app.data.SourceCitations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val marker: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lines: List<String>) : MdBlock
    data object Blank : MdBlock
    data object Rule : MdBlock
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")
private val RULE = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
private const val FENCE = "```"

internal fun parseBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    val paragraph = StringBuilder()
    var blankRun = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.toString().trimEnd())
            paragraph.clear()
        }
    }

    fun flushBlankRun() {
        repeat((blankRun - 1).coerceAtLeast(0)) { blocks += MdBlock.Blank }
        blankRun = 0
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (line.trimStart().startsWith(FENCE)) {
            flushParagraph()
            flushBlankRun()
            val code = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith(FENCE)) {
                code += lines[i]
                i++
            }
            i++
            blocks += MdBlock.Code(code)
            continue
        }

        when {
            line.isBlank() -> {
                flushParagraph()
                blankRun++
            }

            RULE.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                blocks += MdBlock.Rule
            }

            HEADING.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                val m = HEADING.find(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            }

            BULLET.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                blocks += MdBlock.Bullet(BULLET.find(line)!!.groupValues[1])
            }

            NUMBERED.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                val m = NUMBERED.find(line)!!
                blocks += MdBlock.Numbered(m.groupValues[1], m.groupValues[2])
            }

            QUOTE.matches(line) -> {
                flushParagraph()
                flushBlankRun()
                blocks += MdBlock.Quote(QUOTE.find(line)!!.groupValues[1])
            }

            else -> {
                if (paragraph.isEmpty()) flushBlankRun()
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}


internal class IncrementalMarkdownParser {
    private var lastFullText: String = ""
    private var cachedClosedPrefixLength: Int = 0
    private var cachedClosedBlocks: List<MdBlock> = emptyList()

    fun parse(text: String): List<MdBlock> {
        if (text.isEmpty()) {
            reset()
            return emptyList()
        }

        if (lastFullText.isEmpty() || !text.startsWith(lastFullText)) {
            reset()
            val split = findSafeClosedSplit(text)
            if (split > 0) {
                cachedClosedPrefixLength = split
                cachedClosedBlocks = parseBlocks(text.substring(0, split))
                lastFullText = text
                val tail = text.substring(split)
                val tailBlocks = if (tail.isNotBlank()) parseBlocks(tail) else emptyList()
                return cachedClosedBlocks + tailBlocks
            } else {
                lastFullText = text
                return parseBlocks(text)
            }
        }

        lastFullText = text
        val newSplit = findSafeClosedSplit(text)
        if (newSplit > cachedClosedPrefixLength) {
            cachedClosedPrefixLength = newSplit
            cachedClosedBlocks = parseBlocks(text.substring(0, newSplit))
        }

        val tail = if (cachedClosedPrefixLength < text.length) {
            text.substring(cachedClosedPrefixLength)
        } else ""

        val tailBlocks = if (tail.isNotBlank()) parseBlocks(tail) else emptyList()
        return if (cachedClosedBlocks.isEmpty()) tailBlocks else cachedClosedBlocks + tailBlocks
    }

    private fun reset() {
        lastFullText = ""
        cachedClosedPrefixLength = 0
        cachedClosedBlocks = emptyList()
    }

    companion object {
        fun findSafeClosedSplit(text: String): Int {
            var inCodeFence = false
            var lastSafeSplit = -1

            val lines = text.lines()
            var currentOffset = 0
            for (i in lines.indices) {
                val line = lines[i]
                val lineLenWithNewline = line.length + if (i < lines.size - 1) 1 else 0

                if (line.trimStart().startsWith("```")) {
                    inCodeFence = !inCodeFence
                    if (!inCodeFence) {
                        lastSafeSplit = currentOffset + lineLenWithNewline
                    }
                } else if (!inCodeFence && line.isBlank() && i > 0 && lines[i - 1].isNotBlank()) {
                    lastSafeSplit = currentOffset + lineLenWithNewline
                }

                currentOffset += lineLenWithNewline
            }

            return lastSafeSplit.coerceAtLeast(0)
        }
    }
}

private val INLINE = Regex(
    """\[([^\]]+)]\(([^)\s]+)\)""" +
        """|\*\*([^*]+)\*\*""" +
        """|__([^_]+)__""" +
        """|~~([^~]+)~~""" +
        """|\*([^*\n]+)\*""" +
        """|_([^_\n]+)_"""
)

internal enum class MdInlineKind { Plain, Code, Link, Bold, Strike, Italic, Citation }

internal data class MdInlineToken(val kind: MdInlineKind, val text: String, val url: String? = null)

private fun inlineCodeRange(text: String, start: Int): IntRange? {
    var opening = text.indexOf('`', start)
    while (opening >= 0) {
        var openingEnd = opening
        while (openingEnd < text.length && text[openingEnd] == '`') openingEnd++
        val width = openingEnd - opening
        var closing = text.indexOf('`', openingEnd)
        while (closing >= 0) {
            var closingEnd = closing
            while (closingEnd < text.length && text[closingEnd] == '`') closingEnd++
            if (closingEnd - closing == width) return opening until closingEnd
            closing = text.indexOf('`', closingEnd)
        }
        opening = text.indexOf('`', openingEnd)
    }
    return null
}

internal fun markdownInlineTokens(text: String, citationsEnabled: Boolean = true): List<MdInlineToken> {
    val tokens = mutableListOf<MdInlineToken>()
    var cursor = 0
    while (cursor < text.length) {
        val code = inlineCodeRange(text, cursor)
        val inline = INLINE.find(text, cursor)?.takeUnless {
            code != null && it.range.first < code.first && it.range.last in code
        }
        val citation = if (citationsEnabled) SourceCitations.marker.find(text, cursor) else null
        val next = minOf(code?.first ?: text.length, inline?.range?.first ?: text.length, citation?.range?.first ?: text.length)
        if (next > cursor) tokens += MdInlineToken(MdInlineKind.Plain, text.substring(cursor, next))
        if (next == text.length) break
        when {
            code != null && next == code.first -> {
                val raw = text.substring(code)
                val width = raw.takeWhile { it == '`' }.length
                tokens += MdInlineToken(MdInlineKind.Code, raw.substring(width, raw.length - width))
                cursor = code.last + 1
            }
            inline != null && next == inline.range.first -> {
                val groups = inline.groupValues
                tokens += when {
                    groups[1].isNotEmpty() -> MdInlineToken(MdInlineKind.Link, groups[1], groups[2])
                    groups[3].isNotEmpty() -> MdInlineToken(MdInlineKind.Bold, groups[3])
                    groups[4].isNotEmpty() -> MdInlineToken(MdInlineKind.Bold, groups[4])
                    groups[5].isNotEmpty() -> MdInlineToken(MdInlineKind.Strike, groups[5])
                    groups[6].isNotEmpty() -> MdInlineToken(MdInlineKind.Italic, groups[6])
                    else -> MdInlineToken(MdInlineKind.Italic, groups[7])
                }
                cursor = inline.range.last + 1
            }
            else -> {
                tokens += MdInlineToken(MdInlineKind.Citation, citation!!.value)
                cursor = citation.range.last + 1
            }
        }
    }
    return tokens
}

internal fun markdownCitationNumbering(blocks: List<MdBlock>, citationsEnabled: Boolean = true): Map<String, Int> {
    if (!citationsEnabled) return emptyMap()
    val markers = mutableListOf<String>()
    fun collect(text: String) {
        for (token in markdownInlineTokens(text)) {
            when (token.kind) {
                MdInlineKind.Citation -> markers += token.text
                MdInlineKind.Bold, MdInlineKind.Strike, MdInlineKind.Italic -> collect(token.text)
                else -> Unit
            }
        }
    }
    for (block in blocks) {
        when (block) {
            is MdBlock.Heading -> collect(block.text)
            is MdBlock.Paragraph -> collect(block.text)
            is MdBlock.Bullet -> collect(block.text)
            is MdBlock.Numbered -> collect(block.text)
            is MdBlock.Quote -> collect(block.text)
            else -> Unit
        }
    }
    return SourceCitations.numbering(markers.joinToString("\n"))
}

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

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    onCitationClick: ((CitationRef) -> Unit)? = null,
    sourceNoteId: Long? = null,
    sourceRevision: String? = null,
    emphasized: Boolean = false,
    citationsEnabled: Boolean = true
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val incrementalParser = remember { IncrementalMarkdownParser() }
    var blocks by remember { mutableStateOf(emptyList<MdBlock>()) }
    var citationNumbering by remember { mutableStateOf(emptyMap<String, Int>()) }
    LaunchedEffect(text) {
        blocks = withContext(Dispatchers.Default) { incrementalParser.parse(text) }
    }
    LaunchedEffect(blocks, citationsEnabled) {
        citationNumbering = withContext(Dispatchers.Default) {
            markdownCitationNumbering(blocks, citationsEnabled)
        }
    }
    val citation = remember(citationNumbering, onCitationClick, sourceNoteId, sourceRevision, emphasized, citationsEnabled) {
        CitationDecor(citationNumbering, onCitationClick, sourceNoteId, sourceRevision, citationsEnabled, emphasized)
    }
    val fontWeight = if (emphasized) FontWeight.Bold else null

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> {
                    if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        inlineAnnotated(block.text, onGradient, accent, citation),
                        color = onGradient,
                        fontSize = when (block.level) {
                            1 -> 22.sp
                            2 -> 19.sp
                            3 -> 17.sp
                            else -> 15.sp
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                is MdBlock.Paragraph -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        inlineAnnotated(block.text, onGradient, accent, citation),
                        color = onGradient,
                        fontWeight = fontWeight
                    )
                }

                is MdBlock.Bullet -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("•", color = onGradientMuted, fontWeight = fontWeight, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            inlineAnnotated(block.text, onGradient, accent, citation),
                            color = onGradient,
                            fontWeight = fontWeight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Numbered -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("${block.marker}.", color = onGradientMuted, fontWeight = fontWeight, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            inlineAnnotated(block.text, onGradient, accent, citation),
                            color = onGradient,
                            fontWeight = fontWeight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Quote -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(onGradientMuted)
                        )
                        Text(
                            inlineAnnotated(block.text, onGradientMuted, accent, citation),
                            color = onGradientMuted,
                            fontWeight = fontWeight,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                is MdBlock.Code -> {
                    if (index > 0) Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(onGradient.copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        Text(
                            block.lines.joinToString("\n"),
                            color = onGradient,
                            fontWeight = fontWeight,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }

                MdBlock.Blank -> Spacer(modifier = Modifier.height(12.dp))

                MdBlock.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(onGradientMuted.copy(alpha = 0.4f))
                )
            }
        }
    }
}
