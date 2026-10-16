package com.lucent.app.network

import okio.Buffer
import okio.BufferedSource
import java.io.IOException

internal data class SseEvent(val data: String, val type: String = "message")

internal class SseEventReader(
    private val source: BufferedSource,
    private val maxEventBytes: Long = 8L * 1024 * 1024
) {
    private var firstLine = true
    private var skipLf = false

    fun next(ensureActive: () -> Unit = {}): SseEvent? {
        val data = StringBuilder()
        var hasData = false
        var eventType = "message"
        var eventBytes = 0L
        while (true) {
            ensureActive()
            val line = readLine(ensureActive) ?: run {
                if (hasData) throw IOException("SSE 事件在空行结束符前断开")
                return null
            }
            ensureActive()
            if (line.isEmpty()) {
                if (hasData) return SseEvent(data.toString(), eventType)
                eventType = "message"
                eventBytes = 0
                continue
            }
            if (line.startsWith(':')) continue
            eventBytes += line.toByteArray(Charsets.UTF_8).size.toLong()
            if (eventBytes > maxEventBytes) throw IOException("SSE 事件超过大小限制")
            val separator = line.indexOf(':')
            val field = if (separator < 0) line else line.substring(0, separator)
            val value = if (separator < 0) "" else line.substring(separator + 1).removePrefix(" ")
            when (field) {
                "data" -> {
                    if (hasData) data.append(10.toChar())
                    data.append(value)
                    hasData = true
                }
                "event" -> eventType = value.ifEmpty { "message" }
            }
        }
    }

    private fun readLine(ensureActive: () -> Unit): String? {
        val line = Buffer()
        while (true) {
            ensureActive()
            if (source.exhausted()) {
                if (line.size == 0L) return null
                throw IOException("SSE 行在换行符前断开")
            }
            val byte = source.readByte().toInt() and 255
            if (skipLf) {
                skipLf = false
                if (byte == 10) continue
            }
            if (byte == 13 || byte == 10) {
                skipLf = byte == 13
                var text = line.readUtf8()
                if (firstLine) {
                    firstLine = false
                    text = text.removePrefix(65279.toChar().toString())
                }
                return text
            }
            line.writeByte(byte)
            if (line.size > maxEventBytes) throw IOException("SSE 行超过大小限制")
        }
    }
}
