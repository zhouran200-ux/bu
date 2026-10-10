package com.lucent.app.ui

import java.util.Locale

internal object ChatStats {

    fun compactCount(value: Int): String = when {
        value <= 0 -> "—"
        value < 1_000 -> value.toString()
        value < 1_000_000 -> trimTail(kilo(value / 1000.0)) + "k"
        else -> trimTail(kilo(value / 1_000_000.0)) + "M"
    }

    private fun kilo(tenths: Double): String = String.format(Locale.US, "%.1f", tenths)

    private fun trimTail(text: String): String = text.trimEnd('0').trimEnd('.')

    fun secondsLabel(seconds: Int): String? {
        if (seconds <= 0) return null
        if (seconds < 60) return "${seconds}秒"
        return "%d分%02d秒".format(Locale.US, seconds / 60, seconds % 60)
    }

    fun cacheLabel(cachedTokens: Int, promptTokens: Int): String? {
        if (promptTokens <= 0) return null
        return "缓存 ${compactCount(cachedTokens)}/${compactCount(promptTokens)}"
    }

    fun charLabel(chars: Int): String? {
        if (chars <= 0) return null
        return "${compactCount(chars)}字"
    }

    fun preview(text: String, limit: Int = 90): String {
        val flat = text.replace("\r", " ").replace("\n", " ").trim()
        if (flat.isEmpty()) return "（空）"
        return if (flat.length <= limit) flat else flat.take(limit).trimEnd() + "…"
    }

    fun summary(seconds: Int, cachedTokens: Int, promptTokens: Int, chars: Int): String =
        listOfNotNull(
            secondsLabel(seconds),
            cacheLabel(cachedTokens, promptTokens),
            charLabel(chars)
        ).joinToString(" · ")
}
