package com.lucent.app.ui

import com.lucent.app.data.DocumentHeadings
import com.lucent.app.data.SourceTagging

private const val CHAPTER_KEY_PREFIX = "chapter:"

internal data class SourcePreviewRow(val key: String, val text: String) {
    val isChapter: Boolean get() = key.startsWith(CHAPTER_KEY_PREFIX)
}

internal fun sourcePreviewRows(body: String, tagged: SourceTagging.Tagged): List<SourcePreviewRow> {
    val slicesByChapter = tagged.slices.groupBy { it.chapter }
    return buildList {
        tagged.chapters.forEach { chapter ->
            val lineEnd = body.indexOfAny(charArrayOf('\r', '\n'), chapter.startOffset)
                .takeIf { it >= 0 } ?: body.length
            val firstLine = body.substring(chapter.startOffset, minOf(lineEnd, chapter.endOffset))
            val heading = if (DocumentHeadings.titleOf(firstLine) != null) firstLine else "# ${chapter.title}"
            add(SourcePreviewRow("chapter:${chapter.number}", "〔CH${chapter.number}〕\n$heading"))
            slicesByChapter[chapter.number].orEmpty().forEach { slice ->
                add(SourcePreviewRow(
                    "slice:${slice.id}",
                    "〔S：${slice.chapter}-${slice.number}〕\n" + body.substring(slice.startOffset, slice.endOffset)
                ))
            }
        }
    }
}
