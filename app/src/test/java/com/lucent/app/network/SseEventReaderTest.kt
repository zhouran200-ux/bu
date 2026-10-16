package com.lucent.app.network

import okio.Buffer
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SseEventReaderTest {
    private val lf = 10.toChar().toString()
    private fun wire(vararg lines: String) = lines.joinToString(lf)

    @Test fun joinsDataLinesOnlyAtEventBoundary() {
        val reader = SseEventReader(Buffer().writeUtf8(wire("data: {", "data:  value", "data: }", "", "")))
        assertEquals(wire("{", " value", "}"), reader.next()?.data)
        assertNull(reader.next())
    }

    @Test fun supportsAllLineEndingsBomCommentsAndFieldSpacing() {
        for (ending in listOf(lf, 13.toChar().toString() + lf, 13.toChar().toString())) {
            val text = listOf(65279.toChar() + ": heartbeat", "id: 42", "retry: 1000", "event: delta", "data:  first", "data:second", "", "data", "", "").joinToString(ending)
            val reader = SseEventReader(Buffer().writeUtf8(text))
            assertEquals(SseEvent(wire(" first", "second"), "delta"), reader.next())
            assertEquals(SseEvent("", "message"), reader.next())
            assertNull(reader.next())
        }
    }

    @Test fun commentsAndMetadataAloneDoNotDispatch() {
        val reader = SseEventReader(Buffer().writeUtf8(wire(": ping", "", "event: ping", "", "data: ok", "", "")))
        assertEquals(SseEvent("ok", "message"), reader.next())
        assertNull(reader.next())
    }

    @Test fun incompleteEventNeverBecomesSuccessfulEof() {
        for (text in listOf("data: value", wire("data: value", ""), "data: [DONE]")) {
            try {
                SseEventReader(Buffer().writeUtf8(text)).next()
                fail("Expected incomplete event rejection")
            } catch (_: IOException) { }
        }
    }

    @Test fun arbitraryTransportByteSplitsDoNotChangeUtf8OrCrLf() {
        val bytes = wire("data: 中文", "data: next", "", "").replace(lf, 13.toChar().toString() + lf).toByteArray(Charsets.UTF_8)
        val upstream = object : okio.Source {
            var position = 0
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (byteCount == 0L) return 0L
                if (position == bytes.size) return -1L
                sink.writeByte(bytes[position++].toInt())
                return 1L
            }
            override fun timeout() = okio.Timeout.NONE
            override fun close() { }
        }
        val reader = SseEventReader(upstream.buffer())
        assertEquals(wire("中文", "next"), reader.next()?.data)
        assertNull(reader.next())
    }

    @Test fun sizeLimitFailsExplicitly() {
        try {
            SseEventReader(Buffer().writeUtf8(wire("data: 123456789", "", "")), maxEventBytes = 8).next()
            fail("Expected size limit")
        } catch (_: IOException) { }
    }
}
