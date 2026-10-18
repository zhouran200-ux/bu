package com.lucent.app.ui

import com.lucent.app.i18n.S

import com.lucent.app.data.DocumentHeadings
import com.lucent.app.data.SourceTagging

internal data class SourceReadingItem(
    val key: String,
    val text: String,
    val chapterTitle: String,
    val sliceId: String? = null
)

internal fun sourceReadingItems(body: String, tagged: SourceTagging.Tagged): List<SourceReadingItem> {
    val slicesByChapter = tagged.slices.groupBy { it.chapter }
    return buildList {
        tagged.chapters.forEach { chapter ->
            val title = DocumentHeadings.titleOf(chapter.title) ?: chapter.title
            val lineEnd = body.indexOfAny(charArrayOf('\r', '\n'), chapter.startOffset)
                .takeIf { it >= 0 } ?: body.length
            val firstLine = body.substring(chapter.startOffset, minOf(lineEnd, chapter.endOffset))
            val heading = if (DocumentHeadings.titleOf(firstLine) != null) firstLine else chapter.title
            add(SourceReadingItem("chapter:${chapter.number}", heading, title))
            slicesByChapter[chapter.number].orEmpty().forEach { slice ->
                add(SourceReadingItem(
                    key = "slice:${slice.id}",
                    text = body.substring(slice.startOffset, slice.endOffset),
                    chapterTitle = title,
                    sliceId = slice.id
                ))
            }
        }
    }
}

internal sealed interface SourceFocusLocation {
    data class Found(val index: Int) : SourceFocusLocation
    data class Unavailable(val message: String) : SourceFocusLocation
}

internal fun sourceFocusLocation(
    focus: SourceFocus,
    noteId: Long,
    revision: String,
    items: List<SourceReadingItem>
): SourceFocusLocation {
    if (focus.noteId != noteId) return SourceFocusLocation.Unavailable(S.sourcesCitationMissing)
    if (focus.revision != revision) return SourceFocusLocation.Unavailable(S.readingRevisionChanged)
    val index = items.indexOfFirst { it.sliceId == focus.sliceId }
    return if (index >= 0) SourceFocusLocation.Found(index)
    else SourceFocusLocation.Unavailable(S.readingAnchorMissing)
}

internal fun sourceSectionTitle(items: List<SourceReadingItem>, index: Int, fallback: String): String =
    items.getOrNull(index.coerceIn(0, items.lastIndex.coerceAtLeast(0)))?.chapterTitle ?: fallback
