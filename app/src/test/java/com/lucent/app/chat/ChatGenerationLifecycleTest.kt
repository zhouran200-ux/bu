package com.lucent.app.chat

import com.lucent.app.network.ChatCompletionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class ChatGenerationLifecycleTest {
    @Test fun stopKeepsGenerationActiveUntilFinalSaveCompletes() {
        assertStoppedSaveSettlement(failSave = false)
    }

    @Test fun failedFinalSaveSettlesGenerationWithoutWaitingForRetry() {
        assertStoppedSaveSettlement(failSave = true)
    }

    private fun assertStoppedSaveSettlement(failSave: Boolean) {
        val dispatcher = QueueDispatcher()
        val finalSave = CompletableDeferred<Unit>()
        val runningChanges = mutableListOf<Boolean>()
        var finalSaveEntered = false
        val session = ChatSession(
            dispatcher = dispatcher,
            load = { listOf(UiChatMessage(1, "assistant", "")) },
            save = { message ->
                if (message.content.isNotEmpty()) {
                    finalSaveEntered = true
                    finalSave.await()
                    if (failSave) error("disk unavailable")
                }
            },
            onRunningChanged = { runningChanges += it }
        )
        dispatcher.drain()
        val response = CompletableDeferred<Result<ChatCompletionResult>>()
        assertTrue(session.start(1, null) { _, _ -> response.await() })
        dispatcher.drain()

        session.stop()
        dispatcher.drain()
        assertTrue(finalSaveEntered)
        assertEquals(listOf(true), runningChanges)
        assertNotNull(session.streamingAnswerId)
        assertFalse(session.canRelease())

        session.stop()
        dispatcher.drain()
        assertEquals(listOf(true), runningChanges)

        finalSave.complete(Unit)
        dispatcher.drain()
        assertEquals(listOf(true, false), runningChanges)
        assertNull(session.streamingAnswerId)
        assertEquals(if (failSave) 1 else 0, session.pendingWriteCount)
        assertEquals(!failSave, session.canRelease())
        if (!failSave) assertTrue(session.releaseIfIdle())
    }

    @Test fun stopAllRemainsActiveUntilLastSessionsSaveSettles() {
        val dispatcher = QueueDispatcher()
        val saves = List(2) { CompletableDeferred<Unit>() }
        var activeCount = 0
        val sessions = saves.mapIndexed { index, saveGate ->
            ChatSession(
                dispatcher = dispatcher,
                load = { listOf(UiChatMessage(index.toLong(), "assistant", "")) },
                save = { if (it.content.isNotEmpty()) saveGate.await() },
                onRunningChanged = { activeCount += if (it) 1 else -1 }
            )
        }
        dispatcher.drain()
        sessions.forEachIndexed { index, session ->
            val response = CompletableDeferred<Result<ChatCompletionResult>>()
            assertTrue(session.start(index.toLong(), null) { _, _ -> response.await() })
        }
        dispatcher.drain()
        assertEquals(2, activeCount)

        sessions.forEach { it.stop() }
        dispatcher.drain()
        assertEquals(2, activeCount)
        saves[0].complete(Unit)
        dispatcher.drain()
        assertEquals(1, activeCount)
        saves[1].complete(Unit)
        dispatcher.drain()
        assertEquals(0, activeCount)
        sessions.forEach { assertTrue(it.releaseIfIdle()) }
    }

    private class QueueDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
        fun drain() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }
}
