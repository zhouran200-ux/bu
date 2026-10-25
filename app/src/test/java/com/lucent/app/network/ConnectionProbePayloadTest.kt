package com.lucent.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProbePayloadTest {
    @Test
    fun everyProtocolSendsTheHelloWorldPrompt() {
        for (spec in ApiSpec.entries) {
            val payload = connectionProbePayload(spec, "model-x")
            val body = payload.toString()
            assertTrue("$spec must send the probe prompt", body.contains(CONNECTION_PROBE_PROMPT))
            assertFalse("$spec still sends the retired prompt", body.contains("\"ping\""))
            if (spec != ApiSpec.GOOGLE) {
                assertEquals("model-x", payload.getString("model"))
            }
        }
    }

    @Test
    fun protocolPayloadsKeepTheirRequiredShape() {
        val openAi = connectionProbePayload(ApiSpec.OPENAI, "gpt-4o")
        assertEquals("hello world", openAi.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals(64, openAi.getInt("max_tokens"))

        val anthropic = connectionProbePayload(ApiSpec.ANTHROPIC, "claude")
        assertEquals("hello world", anthropic.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals(64, anthropic.getInt("max_tokens"))

        val google = connectionProbePayload(ApiSpec.GOOGLE, "gemini")
        val parts = google.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals("hello world", parts.getJSONObject(0).getString("text"))
        assertFalse(google.has("model"))
    }

    @Test
    fun probeModelIsTrimmedBeforeItReachesThePayload() {
        val payload = connectionProbePayload(ApiSpec.OPENAI, "  gpt-4o  ")
        assertEquals("gpt-4o", payload.getString("model"))
    }
}
