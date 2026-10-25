package com.lucent.app.chat

/**
 * Detects a streaming connection that stopped delivering data.
 *
 * Only foreground time is accumulated: while the app is backgrounded (and possibly frozen by the
 * system) the countdown keeps resetting, and every return to the foreground starts a fresh window.
 * A socket-level read timeout cannot express that rule because it measures wall-clock time.
 */
internal class StreamIdleWatchdog(
    private val idleLimitMillis: Long,
    private val nowMillis: () -> Long,
    private val isForeground: () -> Boolean,
    private val resumeToken: () -> Long
) {
    private var lastActivityMillis = nowMillis()
    private var lastResumeToken = resumeToken()

    /** Records that the stream made progress. */
    fun markActivity() {
        lastActivityMillis = nowMillis()
    }

    /** @return true once the stream stayed silent for [idleLimitMillis] of foreground time. */
    fun tick(): Boolean {
        val now = nowMillis()
        val token = resumeToken()
        if (token != lastResumeToken) {
            lastResumeToken = token
            lastActivityMillis = now
            return false
        }
        if (!isForeground()) {
            lastActivityMillis = now
            return false
        }
        return now - lastActivityMillis >= idleLimitMillis
    }
}
