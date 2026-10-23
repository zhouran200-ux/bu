package com.lucent.app.network

import com.lucent.app.data.ModelCapabilityRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LlmClientFetchModelsTest {

    private class StubServer(private val status: String, private val payload: String) : Closeable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        @Volatile private var requestLine = ""
        val url = "http://127.0.0.1:${listener.localPort}/v1"
        private val task = executor.submit {
            listener.accept().use { socket ->
                socket.soTimeout = 5000
                val input = BufferedInputStream(socket.getInputStream())
                requestLine = readLine(input)
                var contentLength = 0
                while (true) {
                    val line = readLine(input)
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toInt()
                    }
                }
                repeat(contentLength) { if (input.read() < 0) return@repeat }
                val body = payload.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                            ).toByteArray()
                    )
                    write(body)
                    flush()
                }
            }
        }

        private fun readLine(input: BufferedInputStream): String = buildString {
            while (true) {
                val byte = input.read()
                if (byte < 0) break
                if (byte == 10) break
                if (byte != 13) append(byte.toChar())
            }
        }

        fun capturedRequestLine(): String {
            task.get(10, TimeUnit.SECONDS)
            return requestLine
        }

        override fun close() {
            listener.close()
            executor.shutdownNow()
        }
    }

    private fun fetch(
        spec: ApiSpec,
        payload: String,
        status: String = "200 OK",
        rawEndpoint: Boolean = false,
        capture: (String) -> Unit = {}
    ): Result<List<String>> = runBlocking {
        StubServer(status, payload).use { server ->
            val result = LlmClient.fetchModels(
                baseUrl = server.url,
                spec = spec,
                apiKey = "test-key",
                rawEndpoint = rawEndpoint
            )
            capture(server.capturedRequestLine())
            result
        }
    }

    @Test(timeout = 15000)
    fun openAiAndAnthropicStyleDataArrayIsParsedInOrder() {
        val ids = fetch(ApiSpec.OPENAI, """{"data":[{"id":"gpt-4o"},{"id":"o3"},{"id":"o3-mini"}]}""").getOrThrow()
        assertEquals(listOf("gpt-4o", "o3", "o3-mini"), ids)
    }

    @Test(timeout = 15000)
    fun googleModelsArrayLosesItsModelsPrefix() {
        val ids = fetch(
            ApiSpec.GOOGLE,
            """{"models":[{"name":"models/gemini-2.5-flash"},{"name":"models/gemini-1.5-flash"}]}"""
        ).getOrThrow()
        assertEquals(listOf("gemini-2.5-flash", "gemini-1.5-flash"), ids)
    }

    @Test(timeout = 15000)
    fun blankDuplicateAndPaddedIdsAreNormalised() {
        val ids = fetch(
            ApiSpec.OPENAI,
            """{"data":[{"id":"  alpha  "},{"id":"alpha"},{"id":""},{"id":"beta"}]}"""
        ).getOrThrow()
        assertEquals(listOf("alpha", "beta"), ids)
    }

    @Test(timeout = 15000)
    fun untrustedMetadataDoesNotChangeGlobalBuiltins() {
        val previous = ModelCapabilityRegistry.findOrRegister("o3", "openai")
        fetch(ApiSpec.OPENAI, """{"data":[{"id":"o3","reasoning":{"control":"none","supported":false}}]}""").getOrThrow()
        assertEquals(previous, ModelCapabilityRegistry.findOrRegister("o3", "openai"))
        assertNull(ModelCapabilityRegistry.find("future-budget-model"))
    }

    @Test(timeout = 15000)
    fun httpErrorsSurfaceTheStatusAndBody() {
        val failure = fetch(ApiSpec.OPENAI, """{"error":"unauthorized"}""", status = "401 Unauthorized").exceptionOrNull()
        assertTrue(failure != null)
        assertTrue(failure!!.message!!.contains("401"))
        assertTrue(failure.message!!.contains("unauthorized"))
    }

    @Test(timeout = 15000)
    fun malformedJsonFailsInsteadOfReturningAnEmptyList() {
        val failure = fetch(ApiSpec.OPENAI, "not-json-at-all").exceptionOrNull()
        assertTrue(failure is Exception)
    }

    @Test(timeout = 15000)
    fun emptyArraysProduceAnEmptyListWithoutFailure() {
        assertEquals(emptyList<String>(), fetch(ApiSpec.OPENAI, """{"data":[]}""").getOrThrow())
        assertEquals(emptyList<String>(), fetch(ApiSpec.OPENAI, """{}""").getOrThrow())
    }

    @Test(timeout = 15000)
    fun endpointPathsFollowTheProtocolAndRawOverride() {
        var openAiPath = ""
        fetch(ApiSpec.OPENAI, """{"data":[]}""", capture = { openAiPath = it })
        assertTrue(openAiPath.startsWith("GET /v1/models"), openAiPath)

        var rawPath = ""
        fetch(ApiSpec.OPENAI, """{"data":[]}""", rawEndpoint = true, capture = { rawPath = it })
        assertTrue(rawPath.startsWith("GET /v1 "), rawPath)

        var googlePath = ""
        fetch(ApiSpec.GOOGLE, """{"models":[]}""", capture = { googlePath = it })
        assertTrue(googlePath.startsWith("GET /v1/models"), googlePath)
        assertTrue(googlePath.contains("key=test-key"), googlePath)
        assertNull(googlePath.substringAfter("GET ").substringBefore(" ").takeIf { it.endsWith("models/") })
    }
}
