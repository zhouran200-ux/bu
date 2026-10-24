package com.lucent.app.ui


class StreamUiScheduler(
    private val timeWindowMs: Long = 35L,
    private val charBurstThreshold: Int = 48,
    private val thoughtWindowMs: Long = 16L,
    private val thoughtBurstThreshold: Int = 24,
    private val onThought: (reasoning: String) -> Unit = {},
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val minimumPublishIntervalMs: Long = 35L,
    private val onFlush: (content: String, reasoning: String?) -> Unit
) {
    private val pendingContent = StringBuilder()
    private val pendingReasoning = StringBuilder()

    private val fullContent = StringBuilder()
    private val fullReasoning = StringBuilder()

    private var lastFlushTime = clockMs()
    private var lastThoughtFlushTime = lastFlushTime
    private var lastPublishTime: Long? = null
    private var lastPushedReasoning = ""

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
        }

        val now = clockMs()
        if (lastPublishTime?.let { now - it < minimumPublishIntervalMs } == true) return

        if (hasStructuralBoundary) {
            flush(now)
            return
        }

        if (pendingContent.length >= charBurstThreshold ||
            (now - lastFlushTime >= timeWindowMs && pendingContent.isNotEmpty())
        ) {
            flush(now)
            return
        }

        if (pendingReasoning.isNotEmpty() &&
            (pendingReasoning.length >= thoughtBurstThreshold || now - lastThoughtFlushTime >= thoughtWindowMs)
        ) {
            flushThought(now)
        }
    }

    fun hasPending(): Boolean = pendingContent.isNotEmpty() || pendingReasoning.isNotEmpty()

    fun flushPending() {
        if (hasPending()) {
            val now = clockMs()
            if (pendingContent.isNotEmpty()) {
                flush(now)
            } else if (pendingReasoning.isNotEmpty()) {
                flushThought(now)
            }
        }
    }

    fun forceFlush() {
        flush(clockMs())
    }

    private fun flush(timestamp: Long) {
        lastPublishTime = timestamp
        lastFlushTime = timestamp
        lastThoughtFlushTime = timestamp
        pendingContent.clear()
        pendingReasoning.clear()
        val reasoning = fullReasoning.toString()
        lastPushedReasoning = reasoning
        onFlush(fullContent.toString(), reasoning.ifBlank { null })
    }

    private fun flushThought(timestamp: Long) {
        lastPublishTime = timestamp
        lastThoughtFlushTime = timestamp
        pendingReasoning.clear()
        val reasoning = fullReasoning.toString()
        if (reasoning == lastPushedReasoning) return
        lastPushedReasoning = reasoning
        onThought(reasoning)
    }

    fun getFinalContent(): String = fullContent.toString()
    fun getFinalReasoning(): String? = fullReasoning.toString().ifBlank { null }
}
