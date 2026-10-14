package com.lucent.app.data

object PlainTextMarkdown {

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
                flush()
                blocks.add(trimmed)
                previousEndedParagraph = true
                previousLine = null
                return@forEach
            }

            val chapterTitle = DocumentHeadings.plainTitle(line)
            if (chapterTitle != null) {
                flush()
                blocks.add("# $chapterTitle")
                previousEndedParagraph = true
                previousLine = null
                return@forEach
            }

            val body = line.trimEnd().let { if (it.startsWith("\uFEFF")) it.removePrefix("\uFEFF") else it }
            val startsList = DocumentHeadings.isListItem(line)
            val followsList = DocumentHeadings.isListItem(previousLine.orEmpty())
            if (previousEndedParagraph || startsList || followsList) flush()
            if (paragraph.isNotEmpty()) paragraph.append(joinSeparator(paragraph.last(), body.first()))
            paragraph.append(body)
            previousLine = line
            previousEndedParagraph = DocumentHeadings.endsParagraph(line)
        }

        flush()
        return blocks.joinToString("\n\n")
    }

    private fun joinSeparator(previous: Char, next: Char): String =
        if (isCjk(previous) && isCjk(next)) "" else " "

    private fun isCjk(ch: Char): Boolean =
        ch.code in 0x2E80..0x9FFF || ch.code in 0xF900..0xFAFF || ch.code in 0xFF00..0xFFEF
}