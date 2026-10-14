package com.lucent.app.data

object PlainTextMarkdown {

    fun normalize(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<String>()

        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val title = DocumentHeadings.plainTitle(line)
            if (title != null) {
                blocks.add("# $title")
                return@forEach
            }
            val body = line.trimEnd().let { if (it.startsWith("\uFEFF")) it.removePrefix("\uFEFF") else it }
            if (body.isNotEmpty()) blocks.add(body)
        }

        return blocks.joinToString("\n\n")
    }
}