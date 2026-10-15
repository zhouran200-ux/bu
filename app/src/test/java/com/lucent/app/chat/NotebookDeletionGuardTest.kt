package com.lucent.app.chat

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class NotebookDeletionGuardTest {
    private class QueueDispatcher : CoroutineDispatcher() {
        val work = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { work.addLast(block) }
        fun next() { work.removeFirst().run() }
        fun drain() { while (work.isNotEmpty()) next() }
    }

    @Test fun cancellationBetweenPrepareAndReturnStillReleasesOnlyAcquiredGuard() {
        val caller = QueueDispatcher()
        val main = QueueDispatcher()
        var prepared = false
        var deleted = false
        val outcomes = mutableListOf<Boolean>()
        val job = CoroutineScope(Job() + caller).launch {
            guardedNotebookDeletion(main, { prepared = true }, { deleted = true }, { outcomes += it })
        }
        caller.next()
        main.next()
        assertTrue(prepared)
        job.cancel()
        caller.drain()
        main.drain()
        caller.drain()
        assertFalse(deleted)
        assertEquals(listOf(false), outcomes)
        assertTrue(job.isCompleted)
    }

    @Test fun rejectedPreparationDoesNotReleaseAnotherCallsGuard() {
        val queue = QueueDispatcher()
        var rejected = false
        val outcomes = mutableListOf<Boolean>()
        CoroutineScope(Job() + queue).launch {
            try {
                guardedNotebookDeletion(queue, { error("already acquired") }, { fail("must not delete") }, { outcomes += it })
            } catch (_: IllegalStateException) { rejected = true }
        }
        queue.drain()
        assertTrue(rejected)
        assertTrue(outcomes.isEmpty())
    }

    @Test fun successAndDeleteFailureBothFinishExactlyOnce() {
        val queue = QueueDispatcher()
        val outcomes = mutableListOf<Boolean>()
        CoroutineScope(Job() + queue).launch {
            guardedNotebookDeletion(queue, {}, {}, { outcomes += it })
            try {
                guardedNotebookDeletion(queue, {}, { error("transaction failed") }, { outcomes += it })
            } catch (_: IllegalStateException) { }
        }
        queue.drain()
        assertEquals(listOf(true, false), outcomes)
    }
}
