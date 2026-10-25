package com.lucent.app.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamIdleWatchdogTest {
    private var now = 0L
    private var foreground = true
    private var token = 0L

    private fun watchdog(limit: Long = 90_000L) = StreamIdleWatchdog(
        idleLimitMillis = limit,
        nowMillis = { now },
        isForeground = { foreground },
        resumeToken = { token }
    )

    @Test fun foregroundSilenceEventuallyReportsADeadStream() {
        val watchdog = watchdog()
        now = 89_999L
        assertFalse(watchdog.tick())
        now = 90_000L
        assertTrue(watchdog.tick())
    }

    @Test fun backgroundTimeIsNeverCharged() {
        val watchdog = watchdog()
        foreground = false
        now = 10 * 60_000L
        assertFalse("Ten minutes in the background must not count", watchdog.tick())

        foreground = true
        now += 89_999L
        assertFalse(watchdog.tick())
        now += 1L
        assertTrue(watchdog.tick())
    }

    @Test fun returningToTheForegroundRestartsTheCountdown() {
        val watchdog = watchdog()
        now = 60_000L
        token += 1
        assertFalse(watchdog.tick())
        now += 89_999L
        assertFalse(watchdog.tick())
        now += 1L
        assertTrue(watchdog.tick())
    }

    @Test fun everyChunkKeepsTheStreamAlive() {
        val watchdog = watchdog()
        repeat(5) {
            now += 80_000L
            watchdog.markActivity()
            assertFalse(watchdog.tick())
        }
    }

    @Test fun configurableLimitIsHonoured() {
        val watchdog = watchdog(limit = 1_000L)
        now = 999L
        assertFalse(watchdog.tick())
        now = 1_000L
        assertTrue(watchdog.tick())
    }
}
