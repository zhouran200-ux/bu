package com.lucent.app.ui

import kotlin.math.roundToInt

data class ChatScrollMetrics(
    val fraction: Float,
    val thumbFraction: Float,
    val scrollable: Boolean
)

data class ChatScrollTarget(
    val index: Int,
    val offset: Int
)

object ChatScrollMath {

    fun metrics(
        totalItems: Int,
        averageItemSize: Int,
        spacing: Int,
        viewport: Int,
        leadIndex: Int,
        leadOffset: Int
    ): ChatScrollMetrics {
        if (totalItems <= 0 || averageItemSize <= 0) {
            return ChatScrollMetrics(fraction = 0f, thumbFraction = 1f, scrollable = false)
        }
        val safeViewport = viewport.coerceAtLeast(1)
        val contentLength = (averageItemSize + spacing).toLong() * totalItems - spacing
        val maxScroll = (contentLength - safeViewport).coerceAtLeast(1L)
        val scrolledPast = leadIndex.toLong() * (averageItemSize + spacing) - leadOffset
        val fraction = (scrolledPast.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
        val thumbFraction = (safeViewport.toFloat() / contentLength.toFloat()).coerceIn(0.08f, 1f)
        return ChatScrollMetrics(
            fraction = fraction,
            thumbFraction = thumbFraction,
            scrollable = contentLength > safeViewport
        )
    }

    fun targetForFraction(
        totalItems: Int,
        averageItemSize: Int,
        spacing: Int,
        viewport: Int,
        fraction: Float
    ): ChatScrollTarget {
        if (totalItems <= 0) return ChatScrollTarget(0, 0)
        val safeAverage = averageItemSize.coerceAtLeast(1)
        val safeViewport = viewport.coerceAtLeast(1)
        val contentLength = (safeAverage + spacing).toLong() * totalItems - spacing
        val maxScroll = (contentLength - safeViewport).coerceAtLeast(0L)
        val targetPx = (fraction.coerceIn(0f, 1f) * maxScroll).roundToInt()
        val step = (safeAverage + spacing).coerceAtLeast(1)
        val index = (targetPx / step).coerceIn(0, (totalItems - 1).coerceAtLeast(0))
        val offset = (targetPx - index * step).coerceAtLeast(0)
        return ChatScrollTarget(index = index, offset = offset)
    }

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

private val markdownSectionHeading = Regex("^#{1,6}\\s+(.+?)(?:\\s+#+)?$")
private val plainSectionHeading = Regex(
    "^第\\s*[0-9０-９零〇一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟]+\\s*[章回节卷部篇](?:[\\s:：、.．·—-]+.*)?$"
)
private val documentCodeFence = Regex("^(`{3,}|~{3,})(.*)$")

internal fun documentSectionTitle(line: String): String? {
    val title = line.trim().removePrefix("\uFEFF").trim()
    markdownSectionHeading.matchEntire(title)?.let { return it.groupValues[1].trim() }
    return title.takeIf { plainSectionHeading.matches(it) }
}

fun splitDocumentSections(body: String): List<String> {
    val sections = mutableListOf<String>()
    val buffer = StringBuilder()
    var fence: String? = null
    fun flush() {
        val text = buffer.toString().trim()
        if (text.isNotEmpty()) sections.add(text)
        buffer.setLength(0)
    }
    body.lineSequence().forEach { line ->
        val codeFence = documentCodeFence.matchEntire(line.trimStart())
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
        } else if (fence == null && documentSectionTitle(line) != null && buffer.isNotEmpty()) {
            flush()
        }
        buffer.append(line).append("\n")
    }
    flush()
    return sections
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
    val sectionTitle: String? = null
)

fun splitRenderBlocks(body: String, maxChars: Int = 1200): List<RenderBlock> {
    val sections = splitDocumentSections(body)
    val blocks = mutableListOf<RenderBlock>()
    sections.forEachIndexed { sectionIndex, section ->
        val title = documentSectionTitle(section.lineSequence().first())
        if (section.length <= maxChars) {
            blocks.add(RenderBlock(section, sectionIndex, title))
        } else {
            chunkLongText(section, maxChars).forEach { piece ->
                blocks.add(RenderBlock(piece, sectionIndex, title))
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
    val paragraphs = text.split("\n\n")
    val pieces = mutableListOf<String>()
    val buffer = StringBuilder()
    fun flush() {
        val t = buffer.toString().trim()
        if (t.isNotEmpty()) pieces.add(t)
        buffer.setLength(0)
    }
    for (para in paragraphs) {
        if (para.length > maxChars) {
            flush()
            pieces.addAll(chunkBySentence(para, maxChars))
            continue
        }
        if (buffer.isNotEmpty() && buffer.length + para.length > maxChars) flush()
        if (buffer.isNotEmpty()) buffer.append("\n\n")
        buffer.append(para)
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
