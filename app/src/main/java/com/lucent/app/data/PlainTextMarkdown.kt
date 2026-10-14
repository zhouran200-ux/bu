package com.lucent.app.data

object PlainTextMarkdown {

    const val SCENE_BREAK = "---"

    private val QUOTE_SEGMENT = Regex("([\u201C\u201D\"])([^\u201C\u201D\"]+)([\u201D\"])")

    fun normalize(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<String>()
        val paragraph = StringBuilder()
        var previousEndedParagraph = true
        var previousLine: String? = null

        fun flush() {
            val body = paragraph.toString().trim()
            paragraph.setLength(0)
            if (body.isNotEmpty()) blocks.add(body)
        }

        fun appendBlock(block: String) {
            flush()
            blocks.add(block)
            previousEndedParagraph = true
            previousLine = null
        }

        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flush()
                previousEndedParagraph = true
                previousLine = null
                return@forEach
            }

            val markdownTitle = DocumentHeadings.markdownTitle(line)
            if (markdownTitle != null) {
                appendBlock(trimmed)
                return@forEach
            }

            val chapterTitle = DocumentHeadings.plainTitle(line)
            if (chapterTitle != null) {
                appendBlock("# $chapterTitle")
                return@forEach
            }

            if (DocumentHeadings.isSceneSeparator(line)) {
                appendBlock(SCENE_BREAK)
                return@forEach
            }

            val body = trimmed.removePrefix("\uFEFF")
            if (body.isEmpty()) return@forEach

            val startsList = DocumentHeadings.isListItem(line)
            val followsList = DocumentHeadings.isListItem(previousLine.orEmpty())
            if (previousEndedParagraph || startsList || followsList) flush()
            if (paragraph.isNotEmpty()) paragraph.append(joinSeparator(paragraph.last(), body.first()))
            paragraph.append(body)
            previousLine = line
            previousEndedParagraph = DocumentHeadings.endsParagraph(line)
        }

        flush()
        return blocks.joinToString("\n\n") { italiciseDialogue(it) }
    }

    internal fun italiciseDialogue(text: String): String {
        if (text == SCENE_BREAK || text.startsWith("# ")) return text
        return QUOTE_SEGMENT.replace(text) { match ->
            val open = match.groupValues[1]
            val inner = match.groupValues[2]
            val close = match.groupValues[3]
            val leading = inner.takeWhile { it.isWhitespace() }
            val trailing = inner.takeLastWhile { it.isWhitespace() }
            val core = inner.trim()
            if (core.isEmpty()) return@replace match.value
            "$open$leading*$core*$trailing$close"
        }
    }

    private fun joinSeparator(previous: Char, next: Char): String =
        if (isCjk(previous) && isCjk(next)) "" else " "

    private fun isCjk(ch: Char): Boolean =
        ch.code in 0x2E80..0x9FFF || ch.code in 0xF900..0xFAFF || ch.code in 0xFF00..0xFFEF
}