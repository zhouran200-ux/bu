package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceLayoutPositionTest {
    @Test fun handoffUsesSourceIdentityNotPixelsOrOrdinal() {
        val position = ReadingPosition("slice:S11-1", 21, 1800)
        assertEquals(
            ReadingPosition("slice:S11-1", 1, 0),
            position.forSourceLayout(listOf("chapter:11", "slice:S11-1"))
        )

        assertEquals(1800, position.resolve(listOf("chapter:11", "slice:S11-1")).offset)
    }

    @Test fun chapterBoundaryHandoffIsIndependentOfTaggedWhitespaceAndMarkdown() {
        val body = "# 第一章\n\n" + "正文 **强调**。\n\n".repeat(80) + "# 第二章\n\n第二章正文。"
        val tagged = com.lucent.app.data.SourceTagging.tag("书", body)
        val reader = sourceReadingItems(body, tagged).map { it.key }
        val model = sourcePreviewRows(body, tagged).map { it.key }
        val secondChapter = reader.indexOf("chapter:2")
        val position = ReadingPosition("chapter:2", secondChapter, 140)
        val modelPosition = position.forSourceLayout(model)
        assertEquals(ReadingPosition("chapter:2", model.indexOf("chapter:2"), 0), modelPosition)
        assertEquals(position.copy(offset = 0), modelPosition.forSourceLayout(reader))
    }

    @Test fun readerFooterMapsToLastSourceAnchor() {
        assertEquals(
            ReadingPosition("slice:S40-1", 1, 0),
            ReadingPosition("footer", 80, 30).forSourceLayout(listOf("chapter:40", "slice:S40-1"))
        )
    }
}
