package com.lucent.app.data

object DocumentHeadings {

    private const val DIGITS = "[0-9０-９]"
    private const val CN_NUM = "[零〇一二三四五六七八九十百千万亿两壹贰叁肆伍陆柒捌玖拾佰仟]"
    private const val NUM = "(?:$DIGITS+|$CN_NUM+)"
    private const val UNIT = "[章回节卷部篇集话幕]"
    private const val TAIL_START = "[\\s:：、,，.．·•—\\-_)）]"

    private const val LOOSE_TITLE_MAX_TAIL = 30

    private val markdownHeading = Regex("^#{1,6}\\s+(.+?)(?:\\s+#+)?$")

    private val wrapped = Regex("^[【\\[（(](.*)[】\\]）)]\\s*$")

    private val numberedWithPrefix = Regex("^[【\\[（(]?\\s*第\\s*$NUM\\s*$UNIT")

    private val numberedBare = Regex("^[【\\[（(]?\\s*$NUM\\s*$UNIT(?=$|$TAIL_START)")

    private val namedSections = Regex(
        "^[【\\[（(]?\\s*(?:序章|序幕|序言|楔子|引子|前言|后记|尾声|终章|大结局|完结章|番外篇|番外|外传|附录)(?=$|$TAIL_START)"
    )

    private val englishChapter = Regex(
        "^(?:chapter|part|section|volume|book|episode)\\s*\\d+",
        RegexOption.IGNORE_CASE
    )

    private val listItem = Regex("^\\d+\\s*[.)、]")

    private val sceneSeparator = Regex("^[.．·•*＊\\-—_=~…‥・]{2,}$")

    private val TERMINATORS = setOf(
        '。', '！', '？', '…', '.', '!', '?', '"', '\u201D', '』', '」', '】', '）', ')'
    )

    private val SENTENCE_ENDERS = setOf('。', '！', '？', '!', '?')

    private val LEADING_SEPARATORS = ":：、,，.．·•—-_~】\\])）"

    fun markdownTitle(line: String): String? {
        val candidate = normalize(line) ?: return null
        return markdownHeading.matchEntire(candidate)?.groupValues?.get(1)?.trim()
    }

    fun plainTitle(line: String): String? {
        val candidate = normalize(line)?.let { unwrap(it) } ?: return null
        val marker = chapterMarker(candidate) ?: return null
        val tail = trimSeparators(candidate.substring(marker.last + 1))
        if (tail.isEmpty()) return candidate
        if (tail.length > LOOSE_TITLE_MAX_TAIL) return null
        if (tail.any { it in SENTENCE_ENDERS }) return null
        return candidate
    }

    fun titleOf(line: String): String? = markdownTitle(line) ?: plainTitle(line)

    fun isSceneSeparator(line: String): Boolean {
        val candidate = normalize(line) ?: return false
        return sceneSeparator.matches(candidate)
    }

    fun endsParagraph(line: String): Boolean {
        val candidate = line.trimEnd()
        if (candidate.isEmpty()) return true
        return candidate.last() in TERMINATORS
    }

    fun isListItem(line: String): Boolean = listItem.containsMatchIn(line.trimStart())

    private fun chapterMarker(candidate: String): IntRange? =
        numberedWithPrefix.find(candidate)?.range
            ?: numberedBare.find(candidate)?.range
            ?: namedSections.find(candidate)?.range
            ?: englishChapter.find(candidate)?.range

    private fun unwrap(candidate: String): String =
        wrapped.matchEntire(candidate)?.groupValues?.get(1)?.trim() ?: candidate

    private fun trimSeparators(text: String): String =
        text.trimStart { it.isWhitespace() || it in LEADING_SEPARATORS }

    private fun normalize(line: String): String? {
        val candidate = line.trim().removePrefix("\uFEFF").trim()
        return candidate.ifEmpty { null }
    }
}