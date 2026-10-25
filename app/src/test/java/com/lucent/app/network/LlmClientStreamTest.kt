package com.lucent.app.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LlmClientStreamTest {
    @Test
    fun streamingHasNoWallClockReadTimeoutAndNoOverallDeadline() {
        // A backgrounded app can be frozen for minutes; a wall-clock read timeout would abort a
        // healthy stream on resume. StreamIdleWatchdog enforces idleness using foreground time.
        assertEquals(0, LlmHttpClients.streamClient.readTimeoutMillis)
        assertEquals(0, LlmHttpClients.streamClient.callTimeoutMillis)
        assertTrue(LlmHttpClients.streamClient.connectTimeoutMillis > 0)
        assertFalse(LlmHttpClients.streamClient.retryOnConnectionFailure)
    }

    @Test(timeout = 10000)
    fun explicitHandleCancellationUnblocksWaitingForHeaders() = runBlocking {
        assertCancellation(waitForHeaders = true, cancelJob = false)
    }

    @Test(timeout = 10000)
    fun explicitHandleCancellationUnblocksWaitingForNextChunk() = runBlocking {
        assertCancellation(waitForHeaders = false, cancelJob = false)
    }

    @Test(timeout = 10000)
    fun jobCancellationUnblocksWaitingForHeadersWithoutAHandle() = runBlocking {
        assertCancellation(waitForHeaders = true, cancelJob = true)
    }

    @Test(timeout = 10000)
    fun jobCancellationUnblocksWaitingForNextChunkWithoutAHandle() = runBlocking {
        assertCancellation(waitForHeaders = false, cancelJob = true)
    }

    private suspend fun assertCancellation(waitForHeaders: Boolean, cancelJob: Boolean) {
        val ready = CountDownLatch(1)
        val chunkReceived = CompletableDeferred<Unit>()
        LoopbackServer { socket ->
            if (!waitForHeaders) {
                socket.startStream()
                socket.chunk(contentEvent)
            }
            ready.countDown()
            awaitClientClose(socket)
        }.use { server ->
            val handle = if (cancelJob) null else StreamCallHandle()
            val caught = CompletableDeferred<Throwable>()
            kotlinx.coroutines.coroutineScope {
                val pending = async {
                    try {
                        stream(server, handle) { _, _ -> chunkReceived.complete(Unit) }
                        error("Cancelled stream must throw, not return a Result")
                    } catch (e: CancellationException) {
                        caught.complete(e)
                        throw e
                    }
                }
                server.awaitRequest()
                withContext(Dispatchers.IO) { assertTrue(ready.await(3, TimeUnit.SECONDS)) }
                if (!waitForHeaders) withTimeout(3000) { chunkReceived.await() }
                if (cancelJob) pending.cancel() else handle!!.cancel()
                withTimeout(3000) { caught.await() }
                try {
                    withTimeout(3000) { pending.await() }
                    error("Expected cancellation")
                } catch (_: CancellationException) {
                }
                server.awaitFinished()
            }
        }
    }

    @Test(timeout = 10000)
    fun preCancelledHandleNeverOpensAConnection() = runBlocking {
        val handle = StreamCallHandle().apply { cancel() }
        try {
            LlmClient.chatStream(
                baseUrl = "http://127.0.0.1:1", spec = ApiSpec.OPENAI,
                apiKey = "", model = "test", messages = emptyList(), handle = handle
            ) { _, _ -> error("Unexpected chunk") }
            error("Expected cancellation before networking")
        } catch (_: CancellationException) {
        }
    }

    @Test(timeout = 10000)
    fun doneClosesResponseAndValidChunksPreserveUsage() = runBlocking {
        LoopbackServer { socket ->
            socket.startStream()
            socket.chunk("data: {\"choices\":[{\"delta\":{\"content\":\"hello\",\"reasoning_content\":\"thought\"}}]}\n\n")
            socket.chunk("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":9,\"prompt_tokens_details\":{\"cached_tokens\":3}}}\n\ndata: [DONE]\n\n")
            awaitClientClose(socket)
        }.use { server ->
            val chunks = mutableListOf<Pair<String, String?>>()
            val result = stream(server) { text, reasoning -> chunks += text to reasoning }.getOrThrow()
            assertEquals("hello", result.content)
            assertEquals("thought", result.reasoningText)
            assertEquals(9, result.promptTokens)
            assertEquals(3, result.cachedTokens)
            assertEquals(listOf("hello" to "thought"), chunks)
            server.awaitFinished()
        }
    }

    @Test(timeout = 10000)
    fun multilineDataEventIsReassembledBeforeJsonParsing() = runBlocking {
        LoopbackServer { socket ->
            socket.startStream()
            socket.chunk("data: {\"choices\":[{\"delta\":\n")
            socket.chunk("data: {\"content\":\"split\"}}]}\n\n")
            socket.chunk("data: [DONE]\n\n")
            awaitClientClose(socket)
        }.use { server ->
            val result = stream(server).getOrThrow()
            assertEquals("split", result.content)
            server.awaitFinished()
        }
    }

    @Test(timeout = 10000)
    fun malformedEventFailsAndClosesResponseWithoutHidingDeliveredText() = runBlocking {
        LoopbackServer { socket ->
            socket.startStream()
            socket.chunk(contentEvent + "data: not-json" + 10.toChar() + 10.toChar() + "data: [DONE]" + 10.toChar() + 10.toChar())
            awaitClientClose(socket)
        }.use { server ->
            val chunks = mutableListOf<String>()
            val result = stream(server) { text, _ -> chunks += text }
            assertTrue(result.isFailure)
            assertEquals(listOf("hello"), chunks)
            server.awaitFinished()
        }
    }

    @Test(timeout = 10000)
    fun anthropicMessageStopClosesAnOtherwiseOpenResponse() = runBlocking {
        LoopbackServer { socket ->
            socket.startStream()
            socket.chunk("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n")
            awaitClientClose(socket)
        }.use { server ->
            val result = LlmClient.chatStream(
                baseUrl = server.url, spec = ApiSpec.ANTHROPIC, apiKey = "", model = "test",
                messages = emptyList()
            ) { _, _ -> }.getOrThrow()
            assertEquals("hello", result.content)
            server.awaitFinished()
        }
    }

    @Test(timeout = 10000)
    fun callbackCancellationPropagatesAndClosesResponse() = runBlocking {
        val cancellation = CancellationException("consumer cancelled")
        LoopbackServer { socket ->
            socket.startStream()
            socket.chunk(contentEvent)
            awaitClientClose(socket)
        }.use { server ->
            try {
                stream(server) { _, _ -> throw cancellation }
                error("Expected callback cancellation")
            } catch (e: CancellationException) {
                assertEquals(cancellation.message, e.message)
                assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it === cancellation })
            }
            server.awaitFinished()
        }
    }

    @Test(timeout = 10000)
    fun callbackFailureIsNotSwallowedAndClosesResponse() = runBlocking {
        val failure = IllegalStateException("consumer failed")
        LoopbackServer { socket ->
            socket.startStream()
            socket.chunk(contentEvent)
            awaitClientClose(socket)
        }.use { server ->
            val result = stream(server) { _, _ -> throw failure }
            assertTrue(result.isFailure)
            assertEquals(failure.message, result.exceptionOrNull()?.message)
            assertNull(result.exceptionOrNull()?.cause)
            server.awaitFinished()
        }
    }

    @Test(timeout = 10000)
    fun handleCancelledByConsumerDoesNotDeliverBufferedLaterChunks() = runBlocking {
        val handle = StreamCallHandle()
        val chunks = mutableListOf<String>()
        LoopbackServer { socket ->
            socket.startStream()
            socket.chunk(contentEvent + contentEvent + "data: [DONE]\n\n")
            awaitClientClose(socket)
        }.use { server ->
            try {
                stream(server, handle) { text, _ ->
                    chunks += text
                    handle.cancel()
                }
                error("Expected cancellation")
            } catch (_: CancellationException) {
                assertEquals(listOf("hello"), chunks)
            }
            server.awaitFinished()
        }
    }

    @Test(timeout = 10000)
    fun truncatedBodyFailsWithoutReplayingPreviouslyDeliveredText() = runBlocking {
        LoopbackServer { socket ->
            socket.startStream()
            socket.chunk(contentEvent)
        }.use { server ->
            val chunks = mutableListOf<String>()
            val result = stream(server) { text, _ -> chunks += text }
            assertTrue(result.isFailure)
            assertEquals(listOf("hello"), chunks)
            server.awaitFinished()
        }
    }

    @Test(timeout = 10000)
    fun httpFailurePreservesErrorDetails() = runBlocking {
        LoopbackServer { socket ->
            val body = "{\"error\":{\"message\":\"quota exhausted\"}}"
            socket.getOutputStream().apply {
                write("HTTP/1.1 429 Too Many Requests\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body".toByteArray())
                flush()
            }
        }.use { server ->
            assertEquals("HTTP 429: quota exhausted", stream(server).exceptionOrNull()?.message)
            server.awaitFinished()
        }
    }

    private suspend fun stream(
        server: LoopbackServer,
        handle: StreamCallHandle? = null,
        onChunk: suspend (String, String?) -> Unit = { _, _ -> }
    ) = LlmClient.chatStream(
        baseUrl = server.url, spec = ApiSpec.OPENAI, apiKey = "", model = "test",
        messages = listOf(ChatMessagePayload("user", "hello")), handle = handle,
        onChunk = onChunk
    )

    private class LoopbackServer(serve: (Socket) -> Unit) : Closeable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val executor = Executors.newSingleThreadExecutor()
        private val requestReceived = CountDownLatch(1)
        @Volatile private var accepted: Socket? = null
        val url = "http://127.0.0.1:${listener.localPort}/v1"
        private val task = executor.submit {
            listener.accept().use { socket ->
                accepted = socket
                socket.soTimeout = 4000
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
                repeat(contentLength) { check(input.read() >= 0) }
                requestReceived.countDown()
                serve(socket)
            }
        }

        suspend fun awaitRequest() = withContext(Dispatchers.IO) {
            assertTrue("Request never reached server", requestReceived.await(3, TimeUnit.SECONDS))
        }

        suspend fun awaitFinished() = withContext(Dispatchers.IO) {
            task.get(5, TimeUnit.SECONDS)
            Unit
        }

        override fun close() {
            listener.close()
            accepted?.close()
            executor.shutdownNow()
        }
    }

    companion object {
        private const val contentEvent = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n"

        private fun Socket.startStream() {
            getOutputStream().apply {
                write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
        }

        private fun Socket.chunk(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            getOutputStream().apply {
                write("${bytes.size.toString(16)}\r\n".toByteArray())
                write(bytes)
                write("\r\n".toByteArray())
                flush()
            }
        }

        private fun awaitClientClose(socket: Socket) {
            try {
                assertEquals("Client left its response socket open", -1, socket.getInputStream().read())
            } catch (_: SocketException) {
            }
        }
    }
}
