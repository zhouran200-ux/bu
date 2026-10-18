package com.lucent.app.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatWriteQueueTest {
    private class Dispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun drain() {
            var turns = 0
            while (tasks.isNotEmpty()) {
                check(turns++ < 1000)
                tasks.removeFirst().run()
            }
        }
    }

    @Test fun failedEditBlocksDeleteAndRetryRunsBothInOrderWithoutRepeatingCommittedWrites() {
        val dispatcher = Dispatcher()
        var failed = true
        var pending = 0
        var reported: Exception? = null
        val writes = mutableListOf<String>()
        val queue = ChatWriteQueue(CoroutineScope(SupervisorJob() + dispatcher)) { count, error ->
            pending = count
            reported = error
        }
        queue.submit { writes += "committed" }
        dispatcher.drain()
        val edited = queue.submit { if (failed) error("disk full"); writes += "edit" }
        val deleted = queue.submit { writes += "delete" }
        dispatcher.drain()
        assertTrue(edited.getCompleted().isFailure)
        assertTrue(deleted.getCompleted().isFailure)
        assertEquals(listOf("committed"), writes)
        assertEquals(2, pending)
        assertNotNull(reported)
        assertFalse(queue.isIdle)
        failed = false
        val retried = queue.retry()
        dispatcher.drain()
        assertTrue(retried.getCompleted().isSuccess)
        assertEquals(listOf("committed", "edit", "delete"), writes)
        assertEquals(0, pending)
        assertNull(reported)
        assertTrue(queue.isIdle)
    }

    @Test fun failedAnswerSaveCoalescesToLatestContentButNeverAcrossDelete() {
        val dispatcher = Dispatcher()
        var failed = true
        val writes = mutableListOf<String>()
        val queue = ChatWriteQueue(CoroutineScope(SupervisorJob() + dispatcher)) { _, _ -> }
        queue.submit(1) { if (failed) error("disk full"); writes += "placeholder" }
        dispatcher.drain()
        queue.submit(1) { if (failed) error("disk full"); writes += "final" }
        dispatcher.drain()
        queue.submit { writes += "delete" }
        queue.submit(1) { writes += "new answer" }
        dispatcher.drain()
        assertTrue(writes.isEmpty())
        failed = false
        queue.retry()
        dispatcher.drain()
        assertEquals(listOf("final", "delete", "new answer"), writes)
    }

    @Test fun saveSubmittedWhilePreviousSaveIsSuspendedDoesNotChangeItsPayload() {
        val dispatcher = Dispatcher()
        val gate = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val queue = ChatWriteQueue(CoroutineScope(SupervisorJob() + dispatcher)) { _, _ -> }
        queue.submit(1) { gate.await(); writes += "old" }
        dispatcher.drain()
        queue.submit(1) { writes += "new" }
        dispatcher.drain()
        assertTrue(writes.isEmpty())
        gate.complete(Unit)
        dispatcher.drain()
        assertEquals(listOf("old", "new"), writes)
        assertTrue(queue.isIdle)
    }

    @Test fun cancellingObservationDoesNotCancelTheDurableWriteIntent() {
        val dispatcher = Dispatcher()
        val writes = mutableListOf<String>()
        val queue = ChatWriteQueue(CoroutineScope(SupervisorJob() + dispatcher)) { _, _ -> }
        val receipt = queue.submit { writes += "committed" }
        receipt.cancel()
        dispatcher.drain()
        assertEquals(listOf("committed"), writes)
        queue.retry()
        dispatcher.drain()
        assertEquals(listOf("committed"), writes)
        assertTrue(queue.isIdle)
    }

    @Test fun internallyCancelledWriteIsNotReplayedByLaterOperations() {
        val dispatcher = Dispatcher()
        var attempts = 0
        val writes = mutableListOf<String>()
        val queue = ChatWriteQueue(CoroutineScope(SupervisorJob() + dispatcher)) { _, _ -> }
        val cancelled = queue.submit {
            attempts++
            throw kotlinx.coroutines.CancellationException("cancel the operation")
        }
        dispatcher.drain()
        assertTrue(cancelled.isCancelled)
        queue.submit { writes += "next" }
        dispatcher.drain()
        queue.retry()
        dispatcher.drain()
        assertEquals(1, attempts)
        assertEquals(listOf("next"), writes)
        assertTrue(queue.isIdle)
    }

    @Test fun repeatedRetryKeepsOnlyUncommittedTail() {
        val dispatcher = Dispatcher()
        var failSecond = true
        val writes = mutableListOf<String>()
        val queue = ChatWriteQueue(CoroutineScope(SupervisorJob() + dispatcher)) { _, _ -> }
        queue.submit { writes += "first" }
        queue.submit { if (failSecond) error("retry later"); writes += "second" }
        queue.submit { writes += "third" }
        dispatcher.drain()
        queue.retry()
        dispatcher.drain()
        assertEquals(listOf("first"), writes)
        failSecond = false
        queue.retry()
        dispatcher.drain()
        queue.retry()
        dispatcher.drain()
        assertEquals(listOf("first", "second", "third"), writes)
    }
}
