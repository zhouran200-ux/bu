package com.lucent.app.chat

import com.lucent.app.network.ChatCompletionResult
import com.lucent.app.ui.UiChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class ChatSessionTest {
    private class QueueDispatcher : CoroutineDispatcher() {
        private val work = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { work.addLast(block) }
        fun drain() {
            var turns = 0
            while (work.isNotEmpty()) {
                check(turns++ < 1000) { "Unexpected unbounded task queue" }
                work.removeFirst().run()
            }
        }
    }

    @Test fun leavingPageDoesNotCancelAndReturningSeesSameLiveState() {
        val d = QueueDispatcher()
        val saved = mutableListOf<UiChatMessage>()
        val active = mutableListOf<Boolean>()
        val session = ChatSession(d, { emptyList() }, { saved.add(it) }, { active.add(it) })
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", ""))
        val finish = CompletableDeferred<Unit>()
        val page = CoroutineScope(Job() + d)
        page.launch {
            assertTrue(session.start(1, "深度") { _, chunk ->
                chunk("first\n\n", "thought")
                finish.await()
                chunk("second", null)
                Result.success(ChatCompletionResult(content = "first\n\nsecond", reasoningText = "thought"))
            })
        }
        d.drain()
        page.cancel()
        d.drain()
        assertEquals(1L, session.streamingAnswerId)
        assertEquals("first\n\n", session.messages.single().content)
        assertEquals("thought", session.thought.value)
        finish.complete(Unit)
        d.drain()
        assertNull(session.streamingAnswerId)
        assertEquals("first\n\nsecond", saved.last().content)
        assertEquals(listOf(true, false), active)
    }

    @Test fun stopBeforeDispatchFinalizesAndNextGenerationUsesFreshHandle() {
        val d = QueueDispatcher()
        val saved = mutableListOf<UiChatMessage>()
        val session = ChatSession(d, { emptyList() }, { saved.add(it) })
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", "", timestamp = 42))
        session.start(1, null) { _, _ ->
            error("stop before dispatch must not open the network")
        }
        session.stop()
        d.drain()
        assertNull(session.streamingAnswerId)
        assertEquals("（已停止生成）", saved.last().content)
        assertEquals(42L, saved.last().timestamp)
        session.start(1, null) { handle, _ ->
            assertFalse(handle.isCancelled)
            Result.success(ChatCompletionResult("next", null))
        }
        d.drain()
        assertEquals("next", saved.last().content)
        assertNull(session.streamingAnswerId)
    }

    @Test fun stopRetainsThoughtOnlyAndRejectsDuplicateGeneration() {
        val d = QueueDispatcher()
        val saved = mutableListOf<UiChatMessage>()
        val finish = CompletableDeferred<Unit>()
        val session = ChatSession(d, { emptyList() }, { saved.add(it) })
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", ""))
        session.start(1, null) { handle, chunk ->
            chunk("", "reasoning only")
            finish.await()
            assertTrue(handle.isCancelled)
            throw CancellationException("stop")
        }
        d.drain()
        assertFalse(session.start(1, null) { _, _ -> error("duplicate") })
        session.stop()
        finish.complete(Unit)
        d.drain()
        assertEquals("reasoning only", saved.last().reasoningText)
        assertEquals("（已停止生成）", saved.last().content)
        assertNull(session.streamingAnswerId)
    }

    @Test fun separateNotebooksRemainIndependent() {
        val d = QueueDispatcher()
        val gate = CompletableDeferred<Unit>()
        val first = ChatSession(d, { emptyList() }, {})
        val second = ChatSession(d, { emptyList() }, {})
        d.drain()
        listOf(first, second).forEach { it.messages.add(UiChatMessage(1, "assistant", "")) }
        first.start(1, null) { handle, _ -> gate.await(); assertTrue(handle.isCancelled); throw CancellationException() }
        second.start(1, null) { handle, _ -> gate.await(); assertFalse(handle.isCancelled); Result.success(ChatCompletionResult("second", null)) }
        d.drain()
        first.stop()
        gate.complete(Unit)
        d.drain()
        assertEquals("（已停止生成）", first.messages.single().content)
        assertEquals("second", second.messages.single().content)
    }

    @Test fun queuedWritesCannotOvertakeEachOtherOrFinalSave() {
        val d = QueueDispatcher()
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val session = ChatSession(d, { emptyList() }, { writes.add(it.content) })
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", ""))
        session.write { gate.await(); writes.add("delete previous answer") }
        session.start(1, null) { _, _ -> Result.success(ChatCompletionResult("final", null)) }
        d.drain()
        assertTrue(writes.isEmpty())
        gate.complete(Unit)
        d.drain()
        assertEquals(listOf("delete previous answer", "", "final"), writes)
    }

    @Test fun storageFailureStillClearsBusyStateAndKeepsInMemoryAnswer() {
        val d = QueueDispatcher()
        val session = ChatSession(d, { emptyList() }, { error("disk full") })
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", ""))
        session.start(1, null) { _, _ -> Result.success(ChatCompletionResult("retained", null)) }
        d.drain()
        assertNull(session.streamingAnswerId)
        assertEquals("retained", session.messages.single().content)
        assertTrue(session.storageError!!.contains("disk full"))
    }

    @Test fun deletionDrainsExistingWritesAndRejectsNewWritesAndStarts() {
        val d = QueueDispatcher()
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val session = ChatSession(d, { emptyList() }, { writes.add(it.content) })
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", "old"))
        session.write { gate.await(); writes.add("existing") }
        val deletion = CoroutineScope(Job() + d).launch { session.prepareDelete() }
        d.drain()
        assertFalse(session.ready)
        assertFalse(deletion.isCompleted)
        session.saveMessage(UiChatMessage(2, "assistant", "must not resurrect"))
        assertFalse(session.start(1, null) { _, _ -> error("must not start") })
        gate.complete(Unit)
        d.drain()
        assertTrue(deletion.isCompleted)
        assertEquals(listOf("existing"), writes)
        session.deletionFailed()
        session.saveMessage(UiChatMessage(3, "assistant", "restored"))
        d.drain()
        assertTrue(session.ready)
        assertEquals(listOf("existing", "restored"), writes)
    }

    @Test fun activeGenerationCannotBeDeletedOrImplicitlyStopped() {
        val d = QueueDispatcher()
        val gate = CompletableDeferred<Unit>()
        val session = ChatSession(d, { emptyList() }, {})
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", ""))
        session.start(1, null) { handle, _ ->
            gate.await()
            assertFalse(handle.isCancelled)
            Result.success(ChatCompletionResult("complete"))
        }
        d.drain()
        CoroutineScope(Job() + d).launch {
            try { session.prepareDelete(); fail("active deletion must be rejected") }
            catch (_: IllegalStateException) { }
        }
        d.drain()
        assertTrue(session.ready)
        assertEquals(1L, session.streamingAnswerId)
        gate.complete(Unit)
        d.drain()
        assertEquals("complete", session.messages.single().content)
    }

    @Test fun cancelledDeletionPreparationRestoresWritableSession() {
        val d = QueueDispatcher()
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val session = ChatSession(d, { emptyList() }, { writes.add(it.content) })
        d.drain()
        session.write { gate.await() }
        val deletion = CoroutineScope(Job() + d).launch { session.prepareDelete() }
        d.drain()
        assertFalse(session.ready)
        deletion.cancel()
        d.drain()
        assertTrue(session.ready)
        assertNull(session.storageError)
        gate.complete(Unit)
        session.saveMessage(UiChatMessage(3, "assistant", "retained"))
        d.drain()
        assertEquals(listOf("retained"), writes)
    }

    @Test fun serviceStartFailureLeavesSessionIdle() {
        val d = QueueDispatcher()
        val session = ChatSession(d, { emptyList() }, {}, { if (it) error("FGS denied") })
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", "original"))
        try {
            session.start(1, null) { _, _ -> error("must not run") }
            fail("must report service rejection")
        } catch (_: IllegalStateException) { }
        assertNull(session.streamingAnswerId)
        assertEquals("original", session.messages.single().content)
    }
}
