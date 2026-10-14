package com.lucent.app.data

object DocumentHeadings {

    private const val CHAPTER_NUMBER =
        "[0-9０-９零〇一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟]+"

    private const val CHAPTER_MARKER = "[章回节卷部篇]"

    private const val LOOSE_TITLE_MAX_TAIL = 30

    private val markdownHeading = Regex("^#{1,6}\\s+(.+?)(?:\\s+#+)?$")

    private val plainHeading = Regex(
        "^第\\s*$CHAPTER_NUMBER\\s*$CHAPTER_MARKER(?:[\\s:：、.．·—-]+.*)?$"
    )

    private val chapterPrefix = Regex("^第\\s*$CHAPTER_NUMBER\\s*$CHAPTER_MARKER")

    fun markdownTitle(line: String): String? {
        val candidate = normalize(line) ?: return null
        return markdownHeading.matchEntire(candidate)?.groupValues?.get(1)?.trim()
    }

    fun plainTitle(line: String): String? {
        val candidate = normalize(line) ?: return null
        if (plainHeading.matches(candidate)) return candidate
        val marker = chapterPrefix.find(candidate) ?: return null
        val tail = candidate.substring(marker.range.last + 1).trim()
        if (tail.isEmpty()) return candidate
        if (tail.length > LOOSE_TITLE_MAX_TAIL) return null
        if (tail.any { it == '。' || it == '！' || it == '？' }) return null
        return candidate
    }

    fun titleOf(line: String): String? = markdownTitle(line) ?: plainTitle(line)

    fun endsParagraph(line: String): Boolean {
        val candidate = line.trimEnd()
        if (candidate.isEmpty()) return true
        return candidate.last() in TERMINATORS
    }

    fun isListItem(line: String): Boolean = listItem.containsMatchIn(line.trimStart())

    private val listItem = Regex("^\\d+\\s*[.)、]")

    private fun normalize(line: String): String? {
        val candidate = line.trim().removePrefix("\uFEFF").trim()
        return candidate.ifEmpty { null }
    }

    private val TERMINATORS = setOf(
        '。', '！', '？', '…', '.', '!', '?', '"', '\u201D', '』', '」', '】', '）', ')', '：', ':'
    )
}