package com.lucent.app.network

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class BufferedResponseLimitsTest {
    @Test fun exactLimitAndEmptyBodiesAreAccepted() {
        "abcd".toResponseBody().use { assertEquals("abcd", BufferedResponseLimits.read(it, 4)) }
        "".toResponseBody().use { assertEquals("", BufferedResponseLimits.read(it, 0)) }
    }

    @Test fun declaredAndUnknownLengthsCannotExceedTheLimit() {
        val unknown = object : ResponseBody() {
            override fun contentType() = "text/plain".toMediaType()
            override fun contentLength() = -1L
            override fun source() = Buffer().writeUtf8("abcde")
        }
        for (body in listOf("abcde".toResponseBody(), unknown)) {
            body.use {
                try {
                    BufferedResponseLimits.read(it, 4)
                    fail("oversized response must be rejected")
                } catch (_: IOException) { }
            }
        }
    }

    @Test fun declaredCharsetIsRespected() {
        val bytes = byteArrayOf(0xE9.toByte())
        bytes.toResponseBody("text/plain; charset=ISO-8859-1".toMediaType()).use {
            assertEquals("é", BufferedResponseLimits.read(it, 1))
        }
    }
}
