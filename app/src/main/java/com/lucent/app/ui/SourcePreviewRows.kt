package com.lucent.app.ui

import com.lucent.app.data.SourceTagging

private const val CHAPTER_KEY_PREFIX = "chapter:"

internal data class SourcePreviewRow(val key: String, val text: String) {
    val isChapter: Boolean get() = key.startsWith(CHAPTER_KEY_PREFIX)
}

private val sourcePreviewMarker = Regex("""^〔(?:CH(\d+)|S：(\d+)-(\d+))〕$""", RegexOption.MULTILINE)

internal fun sourcePreviewRows(tagged: SourceTagging.Tagged): List<SourcePreviewRow> {
    val text = tagged.text
    if (text.isBlank()) return emptyList()
    val markers = sourcePreviewMarker.findAll(text).toList()
    return markers.mapIndexed { position, marker ->
        val end = markers.getOrNull(position + 1)?.range?.first ?: text.length
        val block = text.substring(marker.range.first, end).trimEnd('\n', '\r')
        val chapter = marker.groupValues[1]
        val key = if (chapter.isNotEmpty()) CHAPTER_KEY_PREFIX + chapter
        else "slice:S${marker.groupValues[2]}-${marker.groupValues[3]}"
        SourcePreviewRow(key, block)
    }
}
