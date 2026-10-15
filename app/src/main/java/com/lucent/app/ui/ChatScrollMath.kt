package com.lucent.app.ui

data class ChatScrollTarget(
    val index: Int,
    val offset: Int
)

object ChatScrollMath {

    fun shouldFollowAfterScroll(
        wasFollowing: Boolean,
        atBottom: Boolean,
        userScrolling: Boolean
    ): Boolean = when {
        !userScrolling -> wasFollowing
        atBottom -> true
        else -> false
    }
}

private val documentCodeFence = Regex("^(`{3,}|~{3,})(.*)$")

internal fun documentSectionTitle(line: String): String? = com.lucent.app.data.DocumentHeadings.titleOf(line)

internal data class DocumentSection(val text: String, val start: Int, val end: Int)

internal data class BodyLine(val text: String, val start: Int, val end: Int)

fun splitDocumentSections(body: String): List<String> =
    splitDocumentSectionsWithOffsets(body).map { it.text }

internal fun splitDocumentSectionsWithOffsets(body: String): List<DocumentSection> {
    val sections = mutableListOf<DocumentSection>()
    val buffer = StringBuilder()
    var fence: String? = null
    var sectionStart = 0
    var sectionEnd = 0

    fun flush() {
        val text = buffer.toString().trim()
        if (text.isNotEmpty()) sections.add(DocumentSection(text, sectionStart, sectionEnd))
        buffer.setLength(0)
        sectionStart = 0
        sectionEnd = 0
    }

    bodyLines(body).forEach { line ->
        val codeFence = documentCodeFence.matchEntire(line.text.trimStart())
        if (codeFence != null) {
            val marker = codeFence.groupValues[1]
            val currentFence = fence
            if (currentFence == null) {
                fence = marker
            } else if (marker.first() == currentFence.first() &&
                marker.length >= currentFence.length && codeFence.groupValues[2].isBlank()
            ) {
                fence = null
            }
        } else if (fence == null && documentSectionTitle(line.text) != null && buffer.isNotEmpty()) {
            flush()
        }
        if (line.text.isNotBlank()) {
            if (buffer.isBlank()) sectionStart = line.start
            sectionEnd = line.end
        }
        buffer.append(line.text).append("\n")
    }
    flush()
    return sections
}

internal fun bodyLines(body: String): List<BodyLine> {
    val lines = mutableListOf<BodyLine>()
    val length = body.length
    var index = 0
    while (true) {
        var end = index
        while (end < length && body[end] != '\n' && body[end] != '\r') end++
        lines.add(BodyLine(body.substring(index, end), index, end))
        if (end >= length) break
        index = if (body[end] == '\r' && end + 1 < length && body[end + 1] == '\n') end + 2 else end + 1
    }
    return lines
}

fun activeSectionNumber(sectionTops: List<Int?>, scrollOffset: Int): Int {
    var active = 0
    for (i in sectionTops.indices) {
        val top = sectionTops[i] ?: continue
        if (top <= scrollOffset + 1) active = i
    }
    return active + 1
}

data class RenderBlock(
    val text: String,
    val sectionIndex: Int,
    val sectionTitle: String? = null,
    val startOffset: Int = 0,
    val endOffset: Int = 0
)

fun splitRenderBlocks(body: String, maxChars: Int = 1200): List<RenderBlock> {
    val sections = splitDocumentSectionsWithOffsets(body)
    val blocks = mutableListOf<RenderBlock>()
    sections.forEachIndexed { sectionIndex, section ->
        val title = documentSectionTitle(section.text.lineSequence().first())
        if (section.text.length <= maxChars) {
            blocks.add(RenderBlock(section.text, sectionIndex, title, section.start, section.end))
        } else {
            var cursor = 0
            chunkLongText(section.text, maxChars).forEach { piece ->
                val found = section.text.indexOf(piece, cursor)
                val localStart = if (found >= 0) found else cursor
                val localEnd = localStart + piece.length
                cursor = localEnd
                blocks.add(
                    RenderBlock(
                        piece,
                        sectionIndex,
                        title,
                        section.start + localStart,
                        section.start + localEnd
                    )
                )
            }
        }
    }
    return blocks
}

internal fun currentDocumentSectionTitle(
    blocks: List<RenderBlock>,
    firstVisibleItemIndex: Int,
    documentTitle: String
): String {
    val block = blocks.getOrNull(firstVisibleItemIndex.coerceIn(0, blocks.lastIndex.coerceAtLeast(0)))
    return block?.sectionTitle ?: documentTitle.ifBlank { "正文" }
}

private fun chunkLongText(text: String, maxChars: Int): List<String> {
    val paragraphLimit = maxChars * 5
    val pieces = mutableListOf<String>()
    val buffer = StringBuilder()

    fun flush() {
        val t = buffer.toString().trim()
        if (t.isNotEmpty()) pieces.add(t)
        buffer.setLength(0)
    }

    text.split("\n\n").forEach { paragraph ->
        val trimmed = paragraph.trim()
        if (trimmed.isEmpty()) return@forEach
        if (trimmed.length <= maxChars) {
            if (buffer.isNotEmpty() && buffer.length + trimmed.length + 2 > maxChars) flush()
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(trimmed)
            return@forEach
        }
        flush()
        if (trimmed.length <= paragraphLimit) {
            pieces.add(trimmed)
        } else {
            pieces.addAll(chunkByLine(trimmed, maxChars))
        }
    }
    flush()
    return pieces.ifEmpty { listOf(text.trim()) }
}

private fun chunkByLine(text: String, maxChars: Int): List<String> {
    val pieces = mutableListOf<String>()
    val buffer = StringBuilder()

    fun flush() {
        val t = buffer.toString().trim()
        if (t.isNotEmpty()) pieces.add(t)
        buffer.setLength(0)
    }

    text.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        if (trimmed.length > maxChars) {
            flush()
            pieces.addAll(chunkBySentence(trimmed, maxChars))
            return@forEach
        }
        if (buffer.isNotEmpty() && buffer.length + trimmed.length + 1 > maxChars) flush()
        if (buffer.isNotEmpty()) buffer.append("\n")
        buffer.append(trimmed)
    }
    flush()
    return pieces.ifEmpty { listOf(text.trim()) }
}

private fun chunkBySentence(text: String, maxChars: Int): List<String> {
    val pieces = mutableListOf<String>()
    val buffer = StringBuilder()
    for (ch in text) {
        buffer.append(ch)
        val boundary = ch == '。' || ch == '！' || ch == '？' || ch == '\n' ||
            ch == '.' || ch == '!' || ch == '?'
        if (boundary && buffer.length >= maxChars) {
            pieces.add(buffer.toString().trim())
            buffer.setLength(0)
        } else if (buffer.length >= maxChars * 2) {
            pieces.add(buffer.toString().trim())
            buffer.setLength(0)
        }
    }
    val tail = buffer.toString().trim()
    if (tail.isNotEmpty()) pieces.add(tail)
    return pieces.filter { it.isNotEmpty() }
}
