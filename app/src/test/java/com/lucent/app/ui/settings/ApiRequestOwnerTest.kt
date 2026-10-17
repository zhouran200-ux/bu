package com.lucent.app.ui.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ApiRequestOwnerTest {
    @Test fun switchedTargetRejectsLateResultAndCancelClearsBusy() = runBlocking {
        val owner = ApiRequestOwner()
        var current = "A"
        var busy = false
        val accepted = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        owner.start(this, { current == "A" }, { busy = it }, {
            withContext(NonCancellable) { release.await(); "old" }
        }, { }, { accepted += it })
        yield()
        assertTrue(busy)
        current = "B"
        owner.cancel()
        assertFalse(busy)
        release.complete(Unit)
        yield()
        assertTrue(accepted.isEmpty())
    }

    @Test fun replacementResultWinsAndOldFinallyCannotClearNewBusy() = runBlocking {
        val owner = ApiRequestOwner()
        var busy = false
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        val accepted = mutableListOf<String>()
        owner.start(this, { true }, { busy = it }, { first.await() }, { }, { accepted += it })
        yield()
        owner.start(this, { true }, { busy = it }, { second.await() }, { }, { accepted += it })
        yield()
        assertTrue(busy)
        first.complete("old")
        second.complete("new")
        yield()
        yield()
        assertEquals(listOf("new"), accepted)
        assertFalse(busy)
    }
}
