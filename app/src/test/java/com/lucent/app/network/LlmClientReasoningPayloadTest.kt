package com.lucent.app.network

import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.ReasoningResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LlmClientReasoningPayloadTest {

    private class CaptureServer : Closeable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        @Volatile private var captured = ""
        val url = "http://127.0.0.1:${listener.localPort}/v1"
        private val task = executor.submit {
            listener.accept().use { socket ->
                socket.soTimeout = 5000
                val input = BufferedInputStream(socket.getInputStream())
                var contentLength = 0
                while (true) {
                    val line = buildString {
                        while (true) {
                            val byte = input.read()
                            check(byte >= 0) { "Unexpected EOF in request headers" }
                            if (byte == 10) break
                            if (byte != 13) append(byte.toChar())
                        }
                    }
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toInt()
                    }
                }
                val bytes = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(bytes, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                captured = String(bytes, 0, read, Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                    write("data: [DONE]\n\n".toByteArray())
                    flush()
                }
            }
        }

        suspend fun body(): String {
            task.get(10, TimeUnit.SECONDS)
            return captured
        }

        override fun close() {
            listener.close()
            executor.shutdownNow()
        }
    }

    private suspend fun send(preset: ReasoningPreset, model: String): String {
        CaptureServer().use { server ->
            LlmClient.chatStream(
                baseUrl = server.url,
                spec = ApiSpec.OPENAI,
                apiKey = "test-key",
                model = model,
                messages = listOf(ChatMessagePayload("user", "ping")),
                reasoningConfig = ReasoningResolver.resolve(preset, "openai", model)
            ) { _, _ -> }.getOrThrow()
            return server.body()
        }
    }

    @Test(timeout = 15000)
    fun claudeViaOpenAiEndpointStillHonoursTheChosenEffort() = runBlocking {
        val body = send(ReasoningPreset.DEEP, "claude-3-7-sonnet")
        assertEquals("high", org.json.JSONObject(body).optString("reasoning_effort"))
    }

    @Test(timeout = 15000)
    fun claudeBudgetModelSendsATokenBudgetNotAnEffort() = runBlocking {
        val json = org.json.JSONObject(send(ReasoningPreset.DEEP, "claude-3-5-sonnet"))
        assertEquals(16384, json.optInt("max_completion_tokens"))
        assertNull(json.opt("reasoning_effort"))
        assertNull(json.opt("reasoning"))
    }

    @Test(timeout = 15000)
    fun gptSixMaximumUsesTheProModeReasoningObject() = runBlocking {
        val json = org.json.JSONObject(send(ReasoningPreset.MAXIMUM, "gpt-6.1-sol"))
        val reasoning = json.optJSONObject("reasoning")
        assertEquals("pro", reasoning?.optString("mode"))
        assertEquals("xhigh", reasoning?.optString("effort"))
        assertNull(json.opt("reasoning_effort"))
    }

    @Test(timeout = 15000)
    fun autoModeSendsNoReasoningParametersAtAll() = runBlocking {
        val json = org.json.JSONObject(send(ReasoningPreset.AUTO, "claude-3-7-sonnet"))
        assertNull(json.opt("reasoning"))
        assertNull(json.opt("reasoning_effort"))
        assertNull(json.opt("max_completion_tokens"))
        assertTrue(json.optBoolean("stream"))
        assertEquals("claude-3-7-sonnet", json.optString("model"))
        assertFalse(json.optJSONArray("messages")!!.length() == 0)
    }

    @Test(timeout = 15000)
    fun deepSeekAndQwenKeepTheirOwnEffortValues() = runBlocking {
        assertEquals("max", org.json.JSONObject(send(ReasoningPreset.MAXIMUM, "deepseek-r1")).optString("reasoning_effort"))
        assertEquals("xhigh", org.json.JSONObject(send(ReasoningPreset.DEEP, "qwen-max")).optString("reasoning_effort"))
    }
}