package com.lucent.app.ui

import com.lucent.app.data.SourceCitations

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
        val previousSplit = cachedClosedPrefixLength
        val openTail = text.substring(previousSplit)
        val newlyClosedLength = findSafeClosedSplit(openTail)
        if (newlyClosedLength > 0) {
            cachedClosedBlocks = cachedClosedBlocks + parseBlocks(openTail.substring(0, newlyClosedLength))
            cachedClosedPrefixLength = previousSplit + newlyClosedLength
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

