package com.lucent.app.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class StreamFollowSchedulerTest {
    @Test fun continuousUpdatesDoNotCancelPendingFollowAndLatestUpdateIsRetained() = runBlocking {
        val updates = Channel<Int>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        var waits = 0
        var follows = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            followStreamUpdates(updates.receiveAsFlow(), { true }, { waits++; if (waits == 1) release.await() }, { follows++ })
        }
        updates.send(1)
        yield()
        repeat(100) { updates.send(it + 2) }
        yield()
        assertEquals(1, waits)
        release.complete(Unit)
        updates.close()
        job.join()
        assertEquals(2, follows)
    }

    @Test fun manualScrollWhileWaitingPreventsPendingFollow() = runBlocking {
        val updates = Channel<Int>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        var allowed = true
        var follows = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            followStreamUpdates(updates.receiveAsFlow(), { allowed }, { release.await() }, { follows++ })
        }
        updates.send(1)
        yield()
        allowed = false
        release.complete(Unit)
        updates.close()
        job.join()
        assertEquals(0, follows)
    }

    @Test fun leavingOrStoppingCancelsPendingFollowWithoutLateScroll() = runBlocking {
        val updates = Channel<Int>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        var follows = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            followStreamUpdates(updates.receiveAsFlow(), { true }, { release.await() }, { follows++ })
        }
        updates.send(1)
        yield()
        job.cancel()
        release.complete(Unit)
        job.join()
        updates.close()
        assertEquals(0, follows)
    }

    @Test fun zeroProgressAndInactiveViewDoNotRequestLayoutOrFollow() = runBlocking {
        var waits = 0
        followStreamUpdates(kotlinx.coroutines.flow.flowOf(0, 1), { false }, { waits++ }, { fail("Unexpected follow") })
        assertEquals(0, waits)
    }
}
