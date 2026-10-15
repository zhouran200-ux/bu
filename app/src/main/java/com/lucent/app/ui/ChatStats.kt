package com.lucent.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ChatStats {
    private fun duration(millis: Long): String {
        val tenths = (millis.coerceAtLeast(0L) / 100L)
        return if (millis in 1..99) "<0.1秒" else if (tenths % 10L == 0L) "${tenths / 10L}秒"
        else "${tenths / 10L}.${tenths % 10L}秒"
    }

    fun secondsLabel(seconds: Int): String? = if (seconds > 0) "总用时 ${seconds}秒" else null

    fun cacheLabel(cachedTokens: Int, promptTokens: Int, known: Boolean = cachedTokens > 0): String? {
        if (promptTokens <= 0) return null
        val hit = if (known) cachedTokens.toString() else "—"
        return "缓存 $hit/${promptTokens}T"
    }

    fun charLabel(chars: Int): String? = if (chars > 0) "${chars}字" else null

    fun bodyCharCount(text: String): Int {
        val withoutRefs = com.lucent.app.data.SourceCitations.marker.replace(text, "")
        return withoutRefs.codePoints().filter { !Character.isWhitespace(it) && !Character.isSpaceChar(it) }.count().toInt()
    }

    fun summary(
        seconds: Int,
        cachedTokens: Int,
        promptTokens: Int,
        chars: Int,
        firstTokenMillis: Long? = null,
        generationMillis: Long? = null,
        cachedTokensKnown: Boolean = cachedTokens > 0,
        timestamp: Long = 0L
    ): String = listOfNotNull(
        firstTokenMillis?.let { "首字延迟${duration(it)}" },
        generationMillis?.let { "总用时 ${duration(it)}" } ?: secondsLabel(seconds),
        cacheLabel(cachedTokens, promptTokens, cachedTokensKnown || cachedTokens > 0),
        charLabel(chars),
        if (timestamp > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)) else null
    ).joinToString(" · ")
}
