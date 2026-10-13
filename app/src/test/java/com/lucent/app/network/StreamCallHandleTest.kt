package com.lucent.app.network

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StreamCallHandleTest {

    @Test
    fun cancelBeforeAttachCancelsTheLaterCall() {
        val handle = StreamCallHandle()
        val call = FakeCall()
        assertFalse(handle.isCancelled)

        handle.cancel()
        handle.attach(call)

        assertTrue(handle.isCancelled)
        assertTrue(call.isCanceled())
        assertEquals(1, call.cancelCount)
    }

    @Test
    fun cancelAfterAttachCancelsTheAttachedCall() {
        val handle = StreamCallHandle()
        val call = FakeCall()
        handle.attach(call)
        assertFalse(call.isCanceled())

        handle.cancel()

        assertTrue(handle.isCancelled)
        assertTrue(call.isCanceled())
        assertEquals(1, call.cancelCount)
    }

    @Test
    fun repeatedCancellationDoesNotCancelTheSameCallAgain() {
        val handle = StreamCallHandle()
        val call = FakeCall()
        handle.attach(call)

        repeat(3) { handle.cancel() }
        handle.detach(call)
        handle.cancel()

        assertTrue(handle.isCancelled)
        assertEquals(1, call.cancelCount)
    }

    @Test
    fun repeatedDetachReleasesTheCompletedCallWithoutCancellingIt() {
        val handle = StreamCallHandle()
        val call = FakeCall()
        handle.attach(call)

        handle.detach(call)
        handle.detach(call)
        assertFalse(handle.isCancelled)
        handle.cancel()

        assertTrue(handle.isCancelled)
        assertFalse(call.isCanceled())
        assertEquals(0, call.cancelCount)
    }

    @Test
    fun detachOnlyRemovesTheMatchingCall() {
        val handle = StreamCallHandle()
        val completedCall = FakeCall()
        val activeCall = FakeCall()
        handle.attach(completedCall)
        handle.attach(activeCall)

        handle.detach(completedCall)
        handle.cancel()

        assertFalse(completedCall.isCanceled())
        assertTrue(activeCall.isCanceled())
        assertEquals(1, activeCall.cancelCount)
    }

    @Test
    fun detachDoesNotResetCancellationForLaterAttachments() {
        val handle = StreamCallHandle()
        val firstCall = FakeCall()
        val laterCall = FakeCall()
        handle.cancel()
        handle.attach(firstCall)

        handle.detach(firstCall)
        handle.attach(laterCall)
        handle.cancel()

        assertTrue(handle.isCancelled)
        assertEquals(1, firstCall.cancelCount)
        assertEquals(1, laterCall.cancelCount)
    }

    @Test
    fun cancellingAPreviousGenerationDoesNotCancelTheNextHandle() {
        val previousHandle = StreamCallHandle()
        val previousCall = FakeCall()
        previousHandle.attach(previousCall)
        previousHandle.cancel()

        val nextHandle = StreamCallHandle()
        val nextCall = FakeCall()
        nextHandle.attach(nextCall)
        previousHandle.cancel()
        previousHandle.detach(previousCall)

        assertTrue(previousHandle.isCancelled)
        assertEquals(1, previousCall.cancelCount)
        assertFalse(nextHandle.isCancelled)
        assertFalse(nextCall.isCanceled())

        nextHandle.cancel()

        assertEquals(1, nextCall.cancelCount)
        assertEquals(1, previousCall.cancelCount)
    }

    @Test
    fun concurrentAttachAndCancelAlwaysCancelTheCall() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val handle = StreamCallHandle()
                val call = FakeCall()
                val start = CountDownLatch(1)
                val attach = executor.submit {
                    start.await()
                    handle.attach(call)
                }
                val cancel = executor.submit {
                    start.await()
                    handle.cancel()
                }

                start.countDown()
                attach.get(5, TimeUnit.SECONDS)
                cancel.get(5, TimeUnit.SECONDS)

                assertTrue(handle.isCancelled)
                assertTrue(call.isCanceled())
                assertEquals(1, call.cancelCount)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private class FakeCall : Call {
        private val cancellationCount = AtomicInteger()
        val cancelCount: Int get() = cancellationCount.get()

        override fun request(): Request = Request.Builder()
            .url("https://example.invalid/")
            .build()

        override fun execute(): Response = error("FakeCall must not be executed")

        override fun enqueue(responseCallback: Callback) {
            error("FakeCall must not be enqueued")
        }

        override fun cancel() {
            cancellationCount.incrementAndGet()
        }

        override fun isExecuted(): Boolean = false

        override fun isCanceled(): Boolean = cancelCount > 0

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = FakeCall()
    }
}
