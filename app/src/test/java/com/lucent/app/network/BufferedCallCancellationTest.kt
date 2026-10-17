package com.lucent.app.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BufferedCallCancellationTest {
    @Test(timeout = 12000) fun cancellationClosesModelDiscoveryAndConnectionProbeSockets() = runBlocking {
        for (probe in listOf(false, true)) {
            val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
            val executor = Executors.newSingleThreadExecutor()
            val waiting = CompletableDeferred<Unit>()
            val accepted = CountDownLatch(1)
            val server = executor.submit {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    while (true) { if (input.readLine().isNullOrEmpty()) break }
                    accepted.countDown()
                    waiting.complete(Unit)
                    assertEquals(-1, input.read())
                }
            }
            try {
                val url = "http://127.0.0.1:" + listener.localPort + "/v1"
                val pending = async {
                    if (probe) LlmClient.testConnection(url, ApiSpec.OPENAI, "")
                    else LlmClient.fetchModels(url, ApiSpec.OPENAI, "")
                }
                withTimeout(3000) { waiting.await() }
                withTimeout(3000) { pending.cancelAndJoin() }
                assertTrue(pending.isCancelled)
                withContext(Dispatchers.IO) { server.get(3, TimeUnit.SECONDS) }
            } finally {
                listener.close()
                executor.shutdownNow()
            }
        }
    }
}
