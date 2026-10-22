package com.lucent.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.lucent.app.data.CitationRef

internal data class MarkdownStyle(
    val textColor: Color,
    val mutedColor: Color,
    val accent: Color,
    val citationsEnabled: Boolean,
    val emphasized: Boolean,
    val sourceNoteId: Long?,
    val sourceRevision: String?,
    val clickable: Boolean
)

internal class MarkdownRenderer {
    private val parser = IncrementalMarkdownParser()
    private var style: MarkdownStyle? = null
    private var blocks: List<MdBlock> = emptyList()
    private var annotated: List<AnnotatedString> = emptyList()
    private var blockRefs: List<List<CitationRef>> = emptyList()

    fun render(
        text: String,
        style: MarkdownStyle,
        click: ((CitationRef) -> Unit)?
    ): MarkdownParseSnapshot {
        if (style != this.style) {
            this.style = style
            blocks = emptyList()
            annotated = emptyList()
            blockRefs = emptyList()
        }
        val next = parser.parse(text)
        val shared = minOf(blocks.size, next.size)
        var firstChanged = 0
        while (firstChanged < shared && sameBlock(blocks[firstChanged], next[firstChanged])) firstChanged++

        val cursor = CitationCursor()
        val nextAnnotated = ArrayList<AnnotatedString>(next.size)
        val nextRefs = ArrayList<List<CitationRef>>(next.size)
        for (index in 0 until firstChanged) {
            nextAnnotated += annotated[index]
            nextRefs += blockRefs[index]
            cursor.refs += blockRefs[index]
        }
        if (firstChanged < next.size) {
            val decor = CitationDecor(
                onClick = click,
                sourceNoteId = style.sourceNoteId,
                sourceRevision = style.sourceRevision,
                enabled = style.citationsEnabled,
                emphasized = style.emphasized
            )
            for (index in firstChanged until next.size) {
                val refsBefore = cursor.refs.size
                nextAnnotated += annotate(next[index], style, decor, cursor)
                nextRefs += cursor.refs.subList(refsBefore, cursor.refs.size).toList()
            }
        }

        blocks = next
        annotated = nextAnnotated
        blockRefs = nextRefs
        return MarkdownParseSnapshot(next, cursor.refs.toList(), nextAnnotated)
    }

    private fun sameBlock(cached: MdBlock, fresh: MdBlock): Boolean =
        cached === fresh || cached == fresh

    private fun annotate(
        block: MdBlock,
        style: MarkdownStyle,
        decor: CitationDecor,
        cursor: CitationCursor
    ): AnnotatedString = when (block) {
        is MdBlock.Heading -> inlineAnnotated(block.text, style.textColor, style.accent, decor, cursor)
        is MdBlock.Paragraph -> inlineAnnotated(block.text, style.textColor, style.accent, decor, cursor)
        is MdBlock.Bullet -> inlineAnnotated(block.text, style.textColor, style.accent, decor, cursor)
        is MdBlock.Numbered -> inlineAnnotated(block.text, style.textColor, style.accent, decor, cursor)
        is MdBlock.Quote -> inlineAnnotated(block.text, style.mutedColor, style.accent, decor, cursor)
        is MdBlock.Code -> AnnotatedString(block.lines.joinToString("\n"))
        else -> AnnotatedString("")
    }
}
