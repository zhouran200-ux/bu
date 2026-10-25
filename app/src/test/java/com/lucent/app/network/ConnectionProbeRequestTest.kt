package com.lucent.app.network

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConnectionProbeRequestTest {
    private class Server(private val response: String) : Closeable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:${listener.localPort}/v1"
        private val task = executor.submit<Pair<String, String>> {
            listener.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().buffered()
                fun line(): String = buildString {
                    while (true) {
                        val byte = input.read()
                        check(byte >= 0)
                        if (byte == 10) break
                        if (byte != 13) append(byte.toChar())
                    }
                }
                val requestLine = line()
                var length = 0
                while (true) {
                    val header = line()
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                }
                val body = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val count = input.read(body, offset, length - offset)
                    check(count > 0)
                    offset += count
                }
                val bytes = if (requestLine.startsWith("HEAD ")) byteArrayOf() else response.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
                requestLine to String(body, Charsets.UTF_8)
            }
        }
        fun request(): Pair<String, String> = task.get(5, TimeUnit.SECONDS)
        override fun close() { listener.close(); executor.shutdownNow() }
    }

    @Test(timeout = 20000) fun generationUsesTheSelectedModelAndHelloWorldAcrossProtocols() = runBlocking {
        val replies = mapOf(
            ApiSpec.OPENAI to """{"choices":[{"message":{"content":"Hello"}}]}""",
            ApiSpec.ANTHROPIC to """{"content":[{"type":"text","text":"Hello"}]}""",
            ApiSpec.GOOGLE to """{"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}"""
        )
        for ((spec, reply) in replies) Server(reply).use { server ->
            val result = LlmClient.testConnection(server.url, spec, "", " model-x ").getOrThrow()
            val (requestLine, body) = server.request()
            assertTrue(result.success)
            assertEquals(ConnectionProbeKind.Generation, result.probeKind)
            assertEquals("model-x", result.targetModel)
            val path = when (spec) {
                ApiSpec.OPENAI -> "/v1/chat/completions"
                ApiSpec.ANTHROPIC -> "/v1/messages"
                ApiSpec.GOOGLE -> "/v1/models/model-x:generateContent"
            }
            assertEquals("POST $path HTTP/1.1", requestLine)
            val json = JSONObject(body)
            if (spec == ApiSpec.GOOGLE) {
                assertEquals("hello world", json.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
            } else {
                assertEquals("model-x", json.getString("model"))
                assertEquals("hello world", json.getJSONArray("messages").getJSONObject(0).getString("content"))
            }
        }
    }

    @Test(timeout = 20000) fun configurationProbesKeepGetAndHeadSeparateFromGeneration() = runBlocking {
        for (raw in listOf(false, true)) Server("{}").use { server ->
            val result = LlmClient.testConnection(server.url, ApiSpec.OPENAI, "", "  ", raw).getOrThrow()
            assertTrue(result.success)
            assertEquals(if (raw) ConnectionProbeKind.Endpoint else ConnectionProbeKind.ModelCatalog, result.probeKind)
            assertEquals(if (raw) "HEAD /v1 HTTP/1.1" else "GET /v1/models HTTP/1.1", server.request().first)
        }
    }

    @Test(timeout = 10000) fun successfulHttpWithoutAnAnswerDoesNotVerifyGeneration() = runBlocking {
        Server("{}").use { server ->
            val result = LlmClient.testConnection(server.url, ApiSpec.OPENAI, "", "model-x", true).getOrThrow()
            assertFalse(result.success)
            assertEquals(200, result.httpCode)
            assertEquals(ConnectionProbeKind.Generation, result.probeKind)
            assertEquals("POST /v1 HTTP/1.1", server.request().first)
            assertNotNull(result.errorMessage)
        }
    }
}
