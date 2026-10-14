package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadingPositionTest {
    @Test fun roundTripKeepsExactOffsetInsideLongMessage() {
        val saved = ReadingPosition("message:1834", 8, 1247)
        assertEquals(saved, ReadingPosition.decode(saved.encode()))
        assertEquals(saved, saved.resolve(List(8) { "message:$it" } + "message:1834"))
    }

    @Test fun stableMessageAnchorSurvivesEarlierMessageDeletion() {
        val saved = ReadingPosition("message:42", 15, 387)
        assertEquals(ReadingPosition("message:42", 2, 387), saved.resolve(listOf("header", "message:41", "message:42", "footer")))
    }

    @Test fun sourceBlockRetainsLineLevelPixelOffset() {
        val saved = ReadingPosition("block:4", 4, 319)
        assertEquals(saved, ReadingPosition.decode(saved.encode())!!.resolve((0..8).map { "block:$it" } + "footer"))
    }

    @Test fun missingAnchorUsesNearestRemainingItemWithoutStaleOffset() {
        assertEquals(ReadingPosition("footer", 2, 0), ReadingPosition("message:removed", 99, 2048).resolve(listOf("header", "message:1", "footer")))
    }

    @Test fun emptyDocumentRestoresSafely() {
        assertEquals(ReadingPosition("", 0, 0), ReadingPosition("block:80", 80, 400).resolve(emptyList()))
    }

    @Test fun malformedPersistenceIsIgnored() {
        listOf(null, "", "1", "1|2", "-1|4|message:1", "1|-3|block:2", "bad|2|x", "1|2|", "2147483648|3|x")
            .forEach { assertNull(ReadingPosition.decode(it)) }
    }

    @Test fun keyMayContainDelimiter() {
        val saved = ReadingPosition("block|with|separators", 3, 39)
        assertEquals(saved, ReadingPosition.decode(saved.encode()))
    }

    @Test fun undoLastVisibleMessageRestoresViewportInsteadOfItsTop() {
        val beforeDelete = ReadingPosition("message:2", 2, 387)
        val shortened = listOf("header", "message:1", "message:2", "footer")
        val restored = shortened.toMutableList().apply { add(3, "message:3") }
        assertEquals(ReadingPosition("message:2", 2, 387), beforeDelete.resolve(restored))
    }

    @Test fun undoDeletedAnchorRetainsOffsetWithinLongAnswer() {
        val beforeDelete = ReadingPosition("message:3", 3, 1729)
        val restored = listOf("header", "message:1", "message:2", "message:3", "footer")
        assertEquals(beforeDelete, beforeDelete.resolve(restored))
    }

    @Test fun undoOnlyMessageRestoresOriginalHeaderPosition() {
        val beforeDelete = ReadingPosition("header", 0, 0)
        assertEquals(beforeDelete, beforeDelete.resolve(listOf("header", "message:1", "footer")))
    }

    @Test fun backgroundAppendDoesNotMoveReadingAnchor() {
        val saved = ReadingPosition("message:4", 1, 517)
        assertEquals(saved, saved.resolve(listOf("header", "message:4", "message:5", "message:6", "footer")))
    }
}
