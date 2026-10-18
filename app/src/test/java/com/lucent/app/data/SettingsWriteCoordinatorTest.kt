package com.lucent.app.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class SettingsWriteCoordinatorTest {
    @Test fun failedWriteRestoresPersistedValueAndNotifiesUser() = runBlocking {
        var cache = "startup"
        var notices = 0
        val writer = SettingsWriteCoordinator(this)
        val setting = SettingsWriteCoordinator.Setting({ cache }, { cache = it })
        val job = writer.submit(listOf(setting.change("new") { "disk" }), { throw IOException("disk full") }, { notices++ })
        assertEquals("new", cache)
        job.join()
        assertEquals("disk", cache)
        assertEquals(1, notices)
    }

    @Test fun oldFailureCannotReplaceNewInputAndFollowingFailureRestoresLastCommit() = runBlocking {
        var cache = "zero"
        var disk = "zero"
        val gate = CompletableDeferred<Unit>()
        val writer = SettingsWriteCoordinator(this)
        val setting = SettingsWriteCoordinator.Setting({ cache }, { cache = it })
        val events = mutableListOf<String>()
        writer.submit(listOf(setting.change("one") { disk }), { gate.await(); events += "one"; throw IOException() }, { assertEquals("three", cache) })
        writer.submit(listOf(setting.change("two") { disk }), { events += "two"; disk = "two" }, {})
        val last = writer.submit(listOf(setting.change("three") { disk }), { assertEquals("three", cache); events += "three"; throw IOException() }, {})
        yield()
        assertTrue(events.isEmpty())
        gate.complete(Unit)
        last.join()
        assertEquals(listOf("one", "two", "three"), events)
        assertEquals("two", disk)
        assertEquals("two", cache)
    }

    @Test fun acceptedWriteSurvivesPageCancellation() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val page = CoroutineScope(coroutineContext + SupervisorJob())
        val gate = CompletableDeferred<Unit>()
        var cache = 0
        var disk = 0
        val writer = SettingsWriteCoordinator(owner)
        val setting = SettingsWriteCoordinator.Setting({ cache }, { cache = it })
        val accepted = CompletableDeferred<kotlinx.coroutines.Job>()
        page.launch {
            accepted.complete(writer.submit(listOf(setting.change(1) { disk }), { gate.await(); disk = 1 }, {}))
        }
        val job = accepted.await()
        page.cancel()
        gate.complete(Unit)
        job.join()
        assertEquals(1, disk)
        assertEquals(1, cache)
        owner.cancel()
    }

    @Test fun cancellationIsNotReportedAsSaveFailure() = runBlocking {
        var cache = 0
        var failures = 0
        val writer = SettingsWriteCoordinator(this)
        val setting = SettingsWriteCoordinator.Setting({ cache }, { cache = it })
        val job = writer.submit(listOf(setting.change(1) { 0 }), { throw CancellationException("cancel") }, { failures++ })
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, failures)
        assertEquals(0, cache)
    }

    @Test fun cancellationBeforeDispatchRestoresOptimisticCache() = runBlocking {
        var cache = "saved"
        var writes = 0
        var notices = 0
        val writer = SettingsWriteCoordinator(this)
        val setting = SettingsWriteCoordinator.Setting({ cache }, { cache = it })
        val job = writer.submit(listOf(setting.change("draft") { "saved" }), { writes++ }, { notices++ })
        assertEquals("draft", cache)
        job.cancel()
        job.join()
        assertEquals("saved", cache)
        assertEquals(0, writes)
        assertEquals(0, notices)
    }

    @Test fun cancellingAnOlderUndispatchedWriteNeverRollsBackNewerInput() = runBlocking {
        var cache = "saved"
        var disk = "saved"
        val writer = SettingsWriteCoordinator(this)
        val setting = SettingsWriteCoordinator.Setting({ cache }, { cache = it })
        val old = writer.submit(listOf(setting.change("old") { disk }), { disk = "old" }, {})
        val latest = writer.submit(listOf(setting.change("latest") { disk }), { disk = "latest" }, {})
        old.cancel()
        old.join()
        assertEquals("latest", cache)
        latest.join()
        assertEquals("latest", disk)
        assertEquals("latest", cache)
    }

    @Test fun anAlreadyCancelledOwnerDoesNotLeaveAnOptimisticDraftBehind() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        owner.cancel()
        var cache = "saved"
        var writes = 0
        val writer = SettingsWriteCoordinator(owner)
        val setting = SettingsWriteCoordinator.Setting({ cache }, { cache = it })
        writer.submit(listOf(setting.change("draft") { "saved" }), { writes++ }, {}).join()
        assertEquals("saved", cache)
        assertEquals(0, writes)
    }

    @Test fun failedResetDoesNotRollbackNewerScaleButRestoresOtherFields() = runBlocking {
        var scale = 2
        var spacing = 3
        val writer = SettingsWriteCoordinator(this)
        val scaleSetting = SettingsWriteCoordinator.Setting({ scale }, { scale = it })
        val spacingSetting = SettingsWriteCoordinator.Setting({ spacing }, { spacing = it })
        writer.submit(listOf(scaleSetting.change(1) { 2 }, spacingSetting.change(1) { 3 }), { throw IOException() }, { assertEquals(4, scale); assertEquals(3, spacing) })
        writer.submit(listOf(scaleSetting.change(4) { 2 }), {}, {}).join()
        assertEquals(4, scale)
        assertEquals(3, spacing)
    }
}
