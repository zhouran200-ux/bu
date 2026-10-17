package com.lucent.app.ui.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ApiProfileCommandQueueTest {
    @Test fun completionIsExactlyOnceOnBothSuccessAndFailure() = runBlocking {
        val queue = ApiProfileCommandQueue(this)
        var successes = 0
        var failures = 0
        val first = queue.submit({ Unit }, { if (it.isSuccess) successes++ else failures++ })
        first.join()
        val second = queue.submit({ error("disk") }, { if (it.isSuccess) successes++ else failures++ })
        second.join()
        assertEquals(1, successes)
        assertEquals(1, failures)
    }

    @Test fun commandsAreOrderedAndFailureDoesNotLoseFollowingCommand() = runBlocking {
        val queue = ApiProfileCommandQueue(this)
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        queue.submit({ gate.await(); events += "first" })
        queue.submit({ events += "second"; error("disk") }, { assertTrue(it.isFailure) })
        val last = queue.submit({ events += "third" })
        yield()
        assertTrue(events.isEmpty())
        gate.complete(Unit)
        last.join()
        assertEquals(listOf("first", "second", "third"), events)
    }
}
