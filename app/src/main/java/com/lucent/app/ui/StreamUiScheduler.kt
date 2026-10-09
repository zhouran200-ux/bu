package com.lucent.app.ui


class StreamUiScheduler(
    private val timeWindowMs: Long = 35L,
    private val charBurstThreshold: Int = 48,
    private val onFlush: (content: String, reasoning: String?) -> Unit
) {
    private val pendingContent = StringBuilder()
    private val pendingReasoning = StringBuilder()

    private val fullContent = StringBuilder()
    private val fullReasoning = StringBuilder()

    private var lastFlushTime = System.currentTimeMillis()

    fun append(deltaText: String, deltaThought: String?) {
        var hasStructuralBoundary = false

        if (deltaText.isNotEmpty()) {
            pendingContent.append(deltaText)
            fullContent.append(deltaText)
            if (deltaText.contains("\n\n") || deltaText.contains("```")) {
                hasStructuralBoundary = true
            }
        }

        if (!deltaThought.isNullOrEmpty()) {
            pendingReasoning.append(deltaThought)
            fullReasoning.append(deltaThought)
            if (deltaThought.contains("\n\n")) {
                hasStructuralBoundary = true
            }
        }

        val now = System.currentTimeMillis()
        val timeElapsed = now - lastFlushTime
        val pendingLen = pendingContent.length + pendingReasoning.length

        val shouldFlush = hasStructuralBoundary ||
                (pendingLen >= charBurstThreshold) ||
                (timeElapsed >= timeWindowMs && pendingLen > 0)

        if (shouldFlush) {
            flush(now)
        }
    }

    fun forceFlush() {
        flush(System.currentTimeMillis())
    }

    private fun flush(timestamp: Long) {
        lastFlushTime = timestamp
        pendingContent.clear()
        pendingReasoning.clear()
        onFlush(fullContent.toString(), fullReasoning.toString().ifBlank { null })
    }

    fun getFinalContent(): String = fullContent.toString()
    fun getFinalReasoning(): String? = fullReasoning.toString().ifBlank { null }
}
