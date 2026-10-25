package com.lucent.app.ui

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NotebookModifiedAtTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun labelPutsTheClockBeforeTheDate() {
        assertEquals(
            "16:22 · 10月25日",
            notebookModifiedAtLabel(
                Instant.parse("2026-10-25T08:22:00Z").toEpochMilli(),
                zone,
                Locale.SIMPLIFIED_CHINESE
            )
        )
    }

    @Test
    fun labelPadsTheClockButNotTheDate() {
        assertEquals(
            "09:05 · 3月5日",
            notebookModifiedAtLabel(
                Instant.parse("2026-03-05T01:05:00Z").toEpochMilli(),
                zone,
                Locale.SIMPLIFIED_CHINESE
            )
        )
    }
}
