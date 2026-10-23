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

    @Test fun readerFooterMapsToLastSourceAnchor() {
        assertEquals(
            ReadingPosition("slice:S40-1", 1, 0),
            ReadingPosition("footer", 80, 30).forSourceLayout(listOf("chapter:40", "slice:S40-1"))
        )
    }
}
