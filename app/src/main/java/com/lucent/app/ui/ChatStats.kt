package com.lucent.app.ui

import com.lucent.app.i18n.S

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ChatStats {
    private fun duration(millis: Long): String {
        val tenths = (millis.coerceAtLeast(0L) / 100L)
        return if (millis in 1..99) S.statsUnderTenth else if (tenths % 10L == 0L) S.statsWholeSeconds(tenths / 10L)
        else S.statsFractionalSeconds(tenths / 10L, tenths % 10L)
    }

    fun secondsLabel(seconds: Int): String? = if (seconds > 0) S.statsDuration(seconds) else null

    fun cacheLabel(cachedTokens: Int, promptTokens: Int, known: Boolean = cachedTokens > 0): String? {
        if (promptTokens <= 0) return null
        val hit = if (known) cachedTokens.toString() else "—"
        return S.statsCache(hit, promptTokens)
    }

    fun charLabel(chars: Int): String? = if (chars > 0) S.statsChars(chars) else null

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
        firstTokenMillis?.let { S.statsFirstToken(duration(it)) },
        generationMillis?.let { S.statsDurationText(duration(it)) } ?: secondsLabel(seconds),
        cacheLabel(cachedTokens, promptTokens, cachedTokensKnown || cachedTokens > 0),
        charLabel(chars),
        if (timestamp > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)) else null
    ).joinToString(" · ")
}
