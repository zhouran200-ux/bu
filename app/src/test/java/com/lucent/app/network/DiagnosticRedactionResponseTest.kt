package com.lucent.app.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiagnosticRedactionResponseTest {
    @Test(timeout = 10000)
    fun httpFallbacksRedactBeforeTheirDisplayLimits() = runBlocking {
        val key = "credential-secret"
        for (jsonFallback in listOf(false, true)) {
            val probeBody = if (jsonFallback) org.json.JSONObject().put("other", "x".repeat(105) + key).toString() else "x".repeat(115) + key
            StubServer(probeBody).use { server ->
                val result = LlmClient.testConnection(server.url, ApiSpec.OPENAI, key).getOrThrow()
                assertFalse(result.success)
                assertEquals(401, result.httpCode)
                assertFalse(result.errorMessage!!.contains(key.take(5)))
                assertFalse(result.formatDisplayMessage().contains(key.take(5)))
                assertFalse(result.prettyResponseBody().contains(key))
                server.awaitFinished()
            }
            val streamBody = if (jsonFallback) org.json.JSONObject().put("other", "x".repeat(185) + key).toString() else "x".repeat(195) + key
            StubServer(streamBody).use { server ->
                val result = LlmClient.chatStream(server.url, ApiSpec.OPENAI, key, "test", emptyList()) { _, _ -> }
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()!!.message!!.startsWith("HTTP 401:"))
                assertFalse(result.exceptionOrNull()!!.message!!.contains(key.take(5)))
                server.awaitFinished()
            }
        }
    }

    @Test fun exceptionReportRedactsAKeyThatCrossesTheStackTraceLimit() = runBlocking {
        val key = "credential-".repeat(200)
        val result = LlmClient.testConnection("http://127.0.0.1:" + key + "/v1", ApiSpec.OPENAI, key).getOrThrow()
        assertFalse(result.success)
        assertEquals(0, result.httpCode)
        assertTrue(result.rawResponseBody.length <= 1500)
        assertFalse(result.rawResponseBody.contains("credential-"))
        assertTrue(result.rawResponseBody.contains("[REDACTED]"))
    }

    @Test(timeout = 10000)
    fun escapedJsonResponseCannotRevealTheKeyWhenPrettyPrintedOrCopied() = runBlocking {
        val key = "a/b/c"
        val escaped = "a" + 92.toChar() + "/b" + 92.toChar() + "/c"
        val body = """{"message":"echo $escaped","other":"visible"}"""
        StubServer(body).use { server ->
            val result = LlmClient.testConnection(server.url, ApiSpec.OPENAI, key).getOrThrow()
            assertEquals(401, result.httpCode)
            for (field in listOf(result.rawResponseBody, result.prettyResponseBody(), result.formatDisplayMessage(), result.toFullReport())) {
                assertFalse(field.contains(key))
                assertTrue(field.contains("[REDACTED]"))
                assertTrue(field.contains("visible") || field == result.formatDisplayMessage())
            }
            server.awaitFinished()
        }
    }

    private class StubServer(payload: String) : Closeable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
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
                    if (line.startsWith("Content-Length:", ignoreCase = true)) contentLength = line.substringAfter(':').trim().toInt()
                }
                repeat(contentLength) { check(input.read() >= 0) }
                val bytes = payload.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes)
                    flush()
                }
            }
        }

        fun awaitFinished() { task.get(5, TimeUnit.SECONDS) }

        override fun close() {
            listener.close()
            executor.shutdownNow()
        }
    }
}
