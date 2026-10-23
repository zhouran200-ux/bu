package com.lucent.app.chat

import com.lucent.app.network.ChatCompletionResult
import com.lucent.app.chat.UiChatMessage
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
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun storageFailureKeepsAnswerDirtyAndRetryPersistsWhatIsShown() {
        val dispatcher = QueueDispatcher()
        var failSaving = true
        var started = false
        val saved = mutableListOf<UiChatMessage>()
        val session = ChatSession(dispatcher, { emptyList() }, {
            if (failSaving && it.content.isNotEmpty()) error("disk unavailable")
            saved += it
        })
        dispatcher.drain()
        session.messages.add(UiChatMessage(1, "assistant", ""))
        session.start(1, null) { _, _ ->
            started = true
            Result.success(ChatCompletionResult("kept in memory"))
        }
        dispatcher.drain()
        assertTrue(started)
        assertEquals("kept in memory", session.messages.single().content)
        assertEquals(1, session.pendingWriteCount)
        assertNotNull(session.storageError)
        assertNull(session.streamingAnswerId)
        failSaving = false
        val retried = session.retryPendingWrites()
        dispatcher.drain()
        assertTrue(retried.getCompleted().isSuccess)
        assertEquals(session.messages.single().content, saved.last().content)
        assertEquals(0, session.pendingWriteCount)
        assertNull(session.storageError)
    }

    @Test fun initialSaveFailureNeverOpensNetworkAndRetainsPreviousAnswerForRetry() {
        val dispatcher = QueueDispatcher()
        var diskUnavailable = true
        var requested = false
        val original = UiChatMessage(1, "assistant", "original answer", promptTokens = 42)
        val stored = mutableListOf<UiChatMessage>()
        val session = ChatSession(dispatcher, { listOf(original) }, {
            if (diskUnavailable) error("disk unavailable")
            stored += it
        })
        dispatcher.drain()
        session.start(1, null) { _, _ ->
            requested = true
            Result.success(ChatCompletionResult("must not replace original"))
        }
        dispatcher.drain()
        assertFalse(requested)
        assertEquals(original, session.messages.single())
        assertNull(session.streamingAnswerId)
        assertEquals(1, session.pendingWriteCount)
        assertFalse(session.canRelease())
        diskUnavailable = false
        session.retryPendingWrites()
        dispatcher.drain()
        assertEquals(listOf(original), stored)
        assertEquals(0, session.pendingWriteCount)
        assertNull(session.storageError)
    }

    @Test fun leavingPageDoesNotCancelSaveThenGenerateIntentOrAllowIdleEviction() {
        val dispatcher = QueueDispatcher()
        val gate = CompletableDeferred<Unit>()
        val session = ChatSession(dispatcher, { emptyList() }, { gate.await() })
        dispatcher.drain()
        val page = CoroutineScope(Job() + dispatcher)
        var generated = false
        val saved = session.saveMessage(UiChatMessage(1, "user", "question"))
        session.afterWrite(saved) { generated = true }
        assertTrue(session.hasPendingAction)
        page.cancel()
        dispatcher.drain()
        assertFalse(generated)
        assertFalse(session.canRelease())
        gate.complete(Unit)
        dispatcher.drain()
        assertTrue(generated)
        assertFalse(session.hasPendingAction)
        assertTrue(session.canRelease())
    }

    @Test fun deletingNotebookSuppressesPendingGenerationContinuation() {
        val dispatcher = QueueDispatcher()
        val gate = CompletableDeferred<Unit>()
        val session = ChatSession(dispatcher, { emptyList() }, { gate.await() })
        dispatcher.drain()
        var generated = false
        session.afterWrite(session.saveMessage(UiChatMessage(1, "user", "question"))) { generated = true }
        CoroutineScope(Job() + dispatcher).launch { session.prepareDelete() }
        dispatcher.drain()
        gate.complete(Unit)
        dispatcher.drain()
        assertFalse(generated)
        assertFalse(session.ready)
    }

    @Test fun cancelledInitialPersistenceAndRollbackAlwaysClearRunningState() {
        val dispatcher = QueueDispatcher()
        var running = false
        var requested = false
        val original = UiChatMessage(1, "assistant", "original")
        val session = ChatSession(dispatcher, { listOf(original) }, {
            throw CancellationException("storage cancelled")
        }, { running = it })
        dispatcher.drain()
        session.start(1, null) { _, _ ->
            requested = true
            Result.success(ChatCompletionResult("unexpected"))
        }
        dispatcher.drain()
        assertFalse(requested)
        assertFalse(running)
        assertNull(session.streamingAnswerId)
        assertEquals(original, session.messages.single())
    }

    @Test fun failedQuestionEditAndDeleteRemainRetryableInTransactionOrder() {
        val dispatcher = QueueDispatcher()
        var unavailable = true
        val events = mutableListOf<String>()
        val session = ChatSession(dispatcher, { emptyList() }, {},
            updateContent = { id, text -> events += "update:$id:$text" },
            deleteMessagesByIds = { events += "delete:$it" },
            replaceQuestion = { id, text, following ->
                if (unavailable) error("transaction failed")
                events += "replace:$id:$text:$following"
            })
        dispatcher.drain()
        session.replaceQuestionAndDropFollowing(1, "edited", listOf(2, 3))
        session.updateMessageContent(4, "answer edit")
        session.deleteMessages(listOf(5))
        dispatcher.drain()
        assertTrue(events.isEmpty())
        assertEquals(3, session.pendingWriteCount)
        unavailable = false
        session.retryPendingWrites()
        dispatcher.drain()
        assertEquals(listOf("replace:1:edited:[2, 3]", "update:4:answer edit", "delete:[5]"), events)
        assertEquals(0, session.pendingWriteCount)
    }

    @Test fun onlyReadyCleanSessionsCanReleaseTheirScopeAndMessages() {
        val dispatcher = QueueDispatcher()
        val session = ChatSession(dispatcher, { listOf(UiChatMessage(1, "assistant", "stored")) }, {})
        assertFalse(session.releaseIfIdle())
        dispatcher.drain()
        assertTrue(session.canRelease())
        assertTrue(session.releaseIfIdle())
        assertFalse(session.ready)
        assertTrue(session.messages.isEmpty())
        assertFalse(session.releaseIfIdle())
    }

    @Test fun failedPersistencePreventsIdleEviction() {
        val dispatcher = QueueDispatcher()
        val session = ChatSession(dispatcher, { emptyList() }, { error("disk unavailable") })
        dispatcher.drain()
        val message = UiChatMessage(1, "assistant", "unsaved")
        session.messages.add(message)
        session.saveMessage(message)
        dispatcher.drain()
        assertFalse(session.canRelease())
        assertFalse(session.releaseIfIdle())
        assertEquals(listOf(message), session.messages.toList())
    }

    @Test fun failedSendAndEditedResendContinueExactlyOnceAfterSuccessfulRetry() {
        for (edit in listOf(false, true)) {
            val d = QueueDispatcher()
            var failWrite = true
            var generated = 0
            val session = ChatSession(d, { emptyList() }, { if (failWrite) error("disk") },
                replaceQuestion = { _, _, _ -> if (failWrite) error("disk") })
            d.drain()
            val receipt = if (edit) session.replaceQuestionAndDropFollowing(1, "edited", listOf(2))
                else session.saveMessage(UiChatMessage(1, "user", "question"))
            session.afterWrite(receipt) { generated++ }
            d.drain()
            assertEquals(0, generated)
            assertTrue(session.hasPendingAction)
            session.retryPendingWrites()
            d.drain()
            assertEquals(0, generated)
            failWrite = false
            session.retryPendingWrites()
            session.retryPendingWrites()
            d.drain()
            assertEquals(1, generated)
            assertFalse(session.hasPendingAction)
            session.retryPendingWrites()
            d.drain()
            assertEquals(1, generated)
        }
    }

    @Test fun generationWaitsForDeleteQueuedBehindUserSave() {
        val d = QueueDispatcher()
        val saveGate = CompletableDeferred<Unit>()
        val deleteGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val session = ChatSession(d, { emptyList() }, { saveGate.await(); events += "save" },
            deleteMessagesByIds = { deleteGate.await(); events += "delete" })
        d.drain()
        session.afterWrite(session.saveMessage(UiChatMessage(1, "user", "question"))) {
            assertEquals(0, session.pendingWriteCount)
            events += "generate"
        }
        session.deleteMessages(listOf(2))
        d.drain()
        saveGate.complete(Unit)
        d.drain()
        assertEquals(listOf("save"), events)
        deleteGate.complete(Unit)
        d.drain()
        assertEquals(listOf("save", "delete", "generate"), events)
    }

    @Test fun stoppedOrDeletedFailedIntentNeverResurrectsOnRetry() {
        for (delete in listOf(false, true)) {
            val d = QueueDispatcher()
            var failWrite = true
            var generated = false
            val session = ChatSession(d, { emptyList() }, { if (failWrite) error("disk") })
            d.drain()
            session.afterWrite(session.saveMessage(UiChatMessage(1, "user", "question"))) { generated = true }
            d.drain()
            if (delete) {
                CoroutineScope(Job() + d).launch { session.prepareDelete() }
                d.drain()
                session.deletionFailed()
            } else session.stop()
            failWrite = false
            session.retryPendingWrites()
            d.drain()
            assertFalse(generated)
            assertFalse(session.hasPendingAction)
        }
    }

    @Test fun failedLoadRetriesOnceAndDeduplicatesConcurrentRequests() {
        val d = QueueDispatcher()
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val session = ChatSession(d, {
            attempts++
            if (attempts == 1) error("load failed")
            gate.await()
            listOf(UiChatMessage(1, "user", "loaded"))
        }, {})
        d.drain()
        assertFalse(session.ready)
        assertTrue(session.canRelease())
        val retry = session.retryLoad()
        assertSame(retry, session.retryLoad())
        d.drain()
        assertEquals(2, attempts)
        assertFalse(session.canRelease())
        gate.complete(Unit)
        d.drain()
        assertTrue(session.ready)
        assertNull(session.storageError)
        assertEquals(1, session.messages.size)
        session.retryLoad()
        d.drain()
        assertEquals(2, attempts)
    }

    @Test fun rejectedWriteContinuationFinishesWithoutGenerationOrScopeFailure() {
        val d = QueueDispatcher()
        val session = ChatSession(d, { emptyList() }, {})
        d.drain()
        CoroutineScope(Job() + d).launch { session.prepareDelete() }
        d.drain()
        var generated = false
        val action = session.afterWrite(session.saveMessage(UiChatMessage(1, "user", "rejected"))) { generated = true }
        d.drain()
        assertTrue(action.isCompleted)
        assertFalse(action.isCancelled)
        assertFalse(generated)
        assertFalse(session.hasPendingAction)
    }

    @Test fun staleLoadFinallyCannotClearNewRetryLoadingState() {
        val d = QueueDispatcher()
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        var attempts = 0
        val session = ChatSession(d, {
            attempts++
            if (attempts == 1) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                oldGate.await()
            } else newGate.await()
            listOf(UiChatMessage(attempts.toLong(), "user", "loaded"))
        }, {})
        d.drain()
        CoroutineScope(Job() + d).launch { session.prepareDelete() }
        d.drain()
        session.deletionFailed()
        val retry = session.retryLoad()
        d.drain()
        oldGate.complete(Unit)
        d.drain()
        assertTrue(session.loading)
        assertFalse(session.ready)
        assertSame(retry, session.retryLoad())
        newGate.complete(Unit)
        d.drain()
        assertTrue(session.ready)
        assertFalse(session.loading)
        assertEquals(1, session.messages.size)
    }

    @Test fun failedLoadCanBeEvictedWithoutWaitingForSuccessfulRetry() {
        val d = QueueDispatcher()
        val session = ChatSession(d, { error("load failed") }, {})
        d.drain()
        assertTrue(session.releaseIfIdle())
        assertNull(session.retryLoad()?.takeUnless { it.isCompleted })
        assertFalse(session.ready)
    }

    @Test fun deletingDuringRetryLoadCannotPublishMessagesOrRestartLoading() {
        val d = QueueDispatcher()
        val gate = CompletableDeferred<Unit>()
        val session = ChatSession(d, { gate.await(); listOf(UiChatMessage(1, "user", "late")) }, {})
        d.drain()
        CoroutineScope(Job() + d).launch { session.prepareDelete() }
        d.drain()
        gate.complete(Unit)
        session.retryLoad()
        d.drain()
        assertFalse(session.ready)
        assertTrue(session.messages.isEmpty())
        session.deletionFailed()
        assertFalse(session.ready)
        session.retryLoad()
        d.drain()
        assertTrue(session.ready)
    }

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
        val session = ChatSession(d, { emptyList() }, { if (it.content.isNotEmpty()) error("disk full") })
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

    @Test fun firstBodyLatencyExcludesThinkingAndTimingsSurviveFinalSave() {
        val d = QueueDispatcher()
        var nanos = 0L
        val saved = mutableListOf<UiChatMessage>()
        val session = ChatSession(d, { emptyList() }, { saved.add(it) }, monotonicNanos = { nanos })
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", "old", firstTokenMillis = 999))
        session.start(1, null) { _, chunk ->
            nanos = 300_000_000L
            chunk("", "思考")
            nanos = 500_000_000L
            chunk(" ", null)
            nanos = 1_200_000_000L
            chunk("正文", null)
            nanos = 2_000_000_000L
            chunk("继续", null)
            nanos = 10_000_000_000L
            Result.success(ChatCompletionResult("正文继续", promptTokens = 10000, cachedTokens = 0, cachedTokensKnown = true))
        }
        d.drain()
        val final = saved.last()
        assertEquals(1200L, final.firstTokenMillis)
        assertEquals(10000L, final.generationMillis)
        assertTrue(final.cachedTokensKnown)
        assertEquals(10, final.answerSeconds)
    }

    @Test fun noBodyDoesNotInventLatencyOnEmptyOrFailedReply() {
        val d = QueueDispatcher()
        val session = ChatSession(d, { emptyList() }, {})
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", ""))
        session.start(1, null) { _, chunk ->
            chunk("", "只有思考")
            Result.failure(IllegalStateException("network error"))
        }
        d.drain()
        assertNull(session.messages.single().firstTokenMillis)
        assertNotNull(session.messages.single().generationMillis)
    }

    @Test fun sourceIdentityIsSavedBeforeChunksAndSurvivesCompletion() {
        val d = QueueDispatcher()
        val saved = mutableListOf<UiChatMessage>()
        val session = ChatSession(d, { emptyList() }, { saved.add(it) })
        d.drain()
        session.messages.add(UiChatMessage(1, "assistant", ""))
        session.start(1, null, sourceBinding = { 42L to "v2-bound" }) { _, chunk ->
            assertEquals(42L, saved.last().sourceNoteId)
            chunk("原文依据。〔S：1-1〕", null)
            Result.success(ChatCompletionResult("原文依据。〔S：1-1〕"))
        }
        d.drain()
        assertEquals("v2-bound", session.messages.single().sourceRevision)
        assertEquals(42L, saved.last().sourceNoteId)
    }

    @Test fun continuationFailureIsReportedWithoutCrashingSessionScope() {
        val d = QueueDispatcher()
        val session = ChatSession(d, { emptyList() }, {})
        d.drain()
        session.afterWrite(session.saveMessage(UiChatMessage(1, "user", "question"))) {
            error("launcher no longer registered")
        }
        d.drain()
        assertFalse(session.hasPendingAction)
        assertTrue(session.storageError!!.contains("launcher no longer registered"))
        assertTrue(session.ready)
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
