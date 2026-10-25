package com.lucent.app.network

import com.lucent.app.data.ModelCapabilityRegistry
import com.lucent.app.data.ReasoningControl
import com.lucent.app.data.ReasoningPlanBuilder
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.ReasoningResolver
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetworkBoundaryRegressionTest {
    private class Server(private val replies: List<String>, private val location: String? = null) : Closeable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:" + socket.localPort + "/v1"
        val paths = mutableListOf<String>()
        private val task = executor.submit {
            replies.forEach { body ->
                socket.accept().use { client ->
                    client.soTimeout = 5000
                    val input = client.getInputStream().bufferedReader()
                    paths.add(input.readLine().substringAfter(' ').substringBefore(' '))
                    var length = 0
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                    }
                    repeat(length) { input.read() }
                    val bytes = body.toByteArray()
                    val status = if (location == null) "200 OK" else "302 Found"
                    val redirect = if (location == null) "" else "Location: $location\r\n"
                    client.getOutputStream().apply {
                        write(("HTTP/1.1 $status\r\n" + redirect + "Content-Length: " + bytes.size +
                            "\r\nConnection: close\r\n\r\n").toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
        }
        fun await() { task.get(5, TimeUnit.SECONDS) }
        override fun close() { socket.close(); executor.shutdownNow() }
    }

    @Test(timeout = 15000)
    fun bothClientsRefuseCrossOriginRedirectsWithCustomCredentials() {
        for (client in listOf(LlmHttpClients.client, LlmHttpClients.streamClient)) {
            for (header in listOf("x-api-key", "x-goog-api-key")) {
                ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { target ->
                    target.soTimeout = 150
                    Server(listOf(""), "http://127.0.0.1:" + target.localPort + "/stolen").use { origin ->
                        client.newCall(Request.Builder().url(origin.url).header(header, "private-secret").build())
                            .execute().use { assertEquals(302, it.code) }
                        origin.await()
                        try {
                            target.accept().use { fail("Redirect leaked a request to a different origin") }
                        } catch (_: SocketTimeoutException) { }
                    }
                }
            }
        }
    }

    @Test
    fun urlsPreserveQueriesSuffixesRawEndpointsAndEncodeGoogleModel() {
        for (spec in ApiSpec.entries) {
            val url = EndpointUrls.models("https://example.test/v1/models?tenant=a%2Fb", spec, "a+b&c", false)
            assertEquals("/v1/models", url.encodedPath)
            assertEquals("a/b", url.queryParameter("tenant"))
            if (spec == ApiSpec.GOOGLE) assertEquals("a+b&c", url.queryParameter("key"))
            val raw = "https://example.test/custom/?tenant=a%2Fb"
            assertEquals(raw, EndpointUrls.generation(raw, spec, "key", "model", true, true).toString())
        }
        for (stream in listOf(false, true)) {
            val google = EndpointUrls.generation("https://example.test/v1/models?tenant=x", ApiSpec.GOOGLE,
                "key", "models/a/b ?#%", false, stream)
            assertEquals("/v1/models/a%2Fb%20%3F%23%25:" + (if (stream) "streamGenerateContent" else "generateContent"), google.encodedPath)
            assertEquals("x", google.queryParameter("tenant"))
            assertEquals(if (stream) "sse" else null, google.queryParameter("alt"))
        }
        assertEquals("/v1/chat/completions", EndpointUrls.generation("https://example.test/v1/chat/completions?x=y",
            ApiSpec.OPENAI, "", "o3", false, false).encodedPath)
        assertEquals("/v1/messages", EndpointUrls.generation("https://example.test/v1?x=y",
            ApiSpec.ANTHROPIC, "", "claude", false, true).encodedPath)
    }

    @Test
    fun probeUsesCompatibleCompletionTokenCap() {
        for (model in listOf("o3", "o3-mini", "o4-mini", "openai/o3-2025-04-16")) {
            val payload = JSONObject()
            applyOpenAiProbeCap(payload, model)
            assertFalse(payload.has("max_tokens"))
            assertEquals(1024, payload.getInt("max_completion_tokens"))
        }
        val ordinary = JSONObject()
        applyOpenAiProbeCap(ordinary, "gpt-4o")
        assertEquals(64, ordinary.getInt("max_tokens"))
    }

    @Test(timeout = 15000)
    fun paginatedGoogleAndAnthropicModelsAreDeduplicated() = runBlocking {
        for (spec in listOf(ApiSpec.GOOGLE, ApiSpec.ANTHROPIC)) {
            val pages = if (spec == ApiSpec.GOOGLE) listOf(
                """{"models":[{"name":"models/one"}],"nextPageToken":"a+b&c"}""",
                """{"models":[{"name":"models/one"},{"name":"models/two"}]}""") else listOf(
                """{"data":[{"id":"one"}],"has_more":true,"last_id":"a+b&c"}""",
                """{"data":[{"id":"one"},{"id":"two"}],"has_more":false}""")
            Server(pages).use { server ->
                assertEquals(listOf("one", "two"), LlmClient.fetchModels(server.url + "?tenant=x", spec, "key").getOrThrow())
                server.await()
                val second = ("http://localhost" + server.paths[1]).toHttpUrl()
                assertEquals("/v1/models", second.encodedPath)
                assertEquals("x", second.queryParameter("tenant"))
                assertEquals("a+b&c", second.queryParameter(if (spec == ApiSpec.GOOGLE) "pageToken" else "after_id"))
            }
        }
    }

    @Test(timeout = 15000)
    fun repeatedContinuationFailsWithoutLooping() = runBlocking {
        val page = """{"models":[],"nextPageToken":"repeat"}"""
        Server(listOf(page, page)).use { server ->
            assertTrue(LlmClient.fetchModels(server.url, ApiSpec.GOOGLE, "").isFailure)
            server.await()
            assertEquals(2, server.paths.size)
        }
    }

    @Test(timeout = 15000)
    fun discoveredOverridesStayInExactEndpointProtocolAndNamespace() = runBlocking {
        val original = ModelCapabilityRegistry.findOrRegister("o3", "openai")
        val payload = """{"data":[{"id":"o3","reasoning":{"supported":false,"control":"none"}},
            {"id":"vendor/Exact-Model","reasoning":{"supported":true,"control":"openai_effort","levels":["low","high"]}}]}"""
        Server(listOf(payload)).use { server ->
            LlmClient.fetchModels(server.url, ApiSpec.OPENAI, "").getOrThrow()
            server.await()
            assertEquals(original, ModelCapabilityRegistry.findOrRegister("o3", "openai"))
            assertEquals(ReasoningControl.NONE, ModelCapabilityRegistry.findOrRegister("o3", "openai", server.url).reasoning.control)
            assertEquals(original, ModelCapabilityRegistry.findOrRegister("o3", "openai", server.url + "/other"))
            assertEquals(original, ModelCapabilityRegistry.findOrRegister("o3", "anthropic", server.url))
            assertEquals("high", ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "vendor/Exact-Model", server.url).openAiEffort)
            assertTrue(ReasoningPlanBuilder.build("openai", "vendor/Exact-Model", server.url).supported)
            for (id in listOf("Exact-Model", "other/Exact-Model", "vendor/exact-model", "vendor/ExactModel")) {
                assertEquals(ReasoningControl.NONE, ModelCapabilityRegistry.findOrRegister(id, "openai", server.url).reasoning.control)
            }
        }
    }

    @Test
    fun invalidMetadataIsIgnored() {
        for (json in listOf(
            """{"control":"unknown"}""", """{"control":"effort","levels":["invented"]}""",
            """{"control":"effort","supported":"true"}""", """{"control":"effort","thinking_budget_max":1.5}""")) {
            assertNull(discoveredReasoning("unknown", ApiSpec.OPENAI, JSONObject(json)))
        }
        assertNull(discoveredReasoning("unknown", ApiSpec.GOOGLE, JSONObject("""{"control":"anthropic_budget"}""")))
    }
}
