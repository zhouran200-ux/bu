package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUiCadenceTest {
    @Test fun fastThoughtBurstsStayBoundedAndPreserveEveryCharacter() {
        var now = 0L
        val times = mutableListOf<Long>()
        var contentPushes = 0
        var finalThought: String? = null
        val scheduler = StreamUiScheduler(
            clockMs = { now },
            onThought = { times += now },
            onFlush = { _, reasoning -> contentPushes++; finalThought = reasoning }
        )
        val delta = "reasoning paragraph with a boundary\n\n"
        repeat(1000) {
            now = it.toLong()
            scheduler.append("", delta)
        }
        assertTrue(times.size <= 29)
        assertTrue(times.zipWithNext().all { (a, b) -> b - a >= 35L })
        assertEquals(0, contentPushes)
        scheduler.forceFlush()
        assertEquals(delta.repeat(1000), finalThought)
    }

    @Test fun contentBoundariesAndThoughtShareTheSameBudget() {
        var now = 0L
        val times = mutableListOf<Long>()
        var finalContent = ""
        var finalThought: String? = null
        val scheduler = StreamUiScheduler(
            clockMs = { now },
            onThought = { times += now },
            onFlush = { content, reasoning ->
                times += now
                finalContent = content
                finalThought = reasoning
            }
        )
        scheduler.append("", "x".repeat(24))
        repeat(34) {
            now++
            scheduler.append("a\n\n", "b")
        }
        assertEquals(listOf(0L), times)
        now = 35L
        scheduler.append("z", null)
        assertEquals(listOf(0L, 35L), times)
        scheduler.append("tail", "last")
        scheduler.forceFlush()
        assertEquals("a\n\n".repeat(34) + "ztail", finalContent)
        assertEquals("x".repeat(24) + "b".repeat(34) + "last", finalThought)
    }
}
