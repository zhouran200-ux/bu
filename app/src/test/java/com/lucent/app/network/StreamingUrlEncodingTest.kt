package com.lucent.app.network

import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingUrlEncodingTest {

    @Test
    fun googleStreamingKeyIsUrlEncoded() {
        val request: Request = StreamingRequestBuilder.build(
            "https://example.test/v1beta",
            ApiSpec.GOOGLE,
            "a+b/c",
            "gemini-pro",
            listOf(ChatMessagePayload("user", "hello")),
            "",
            null,
            false
        ).build()
        val url = request.url.toString()
        assertTrue(url, url.contains("key=a%2Bb%2Fc"))
    }
}
