package com.lucent.app.chat

internal class StreamIdleWatchdog(
    private val idleLimitMillis: Long,
    private val nowMillis: () -> Long,
    private val isForeground: () -> Boolean,
    private val resumeToken: () -> Long
) {
    private var lastActivityMillis = nowMillis()
    private var lastResumeToken = resumeToken()

    fun markActivity() {
        lastActivityMillis = nowMillis()
    }

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
