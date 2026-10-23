package com.lucent.app.chat

import com.lucent.app.i18n.S

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.network.ChatCompletionResult
import com.lucent.app.network.StreamCallHandle
import com.lucent.app.ui.StreamUiScheduler
import com.lucent.app.chat.UiChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext

class ChatSession(
    private val dispatcher: CoroutineDispatcher,
    private val load: suspend () -> List<UiChatMessage>,
    private val save: suspend (UiChatMessage) -> Unit,
    private val onRunningChanged: (Boolean) -> Unit = {},
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val updateContent: suspend (Long, String) -> Unit = { _, _ -> },
    private val deleteMessagesByIds: suspend (List<Long>) -> Unit = {},
    private val replaceQuestion: suspend (Long, String, List<Long>) -> Unit = { _, _, _ -> }
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val messages = mutableStateListOf<UiChatMessage>()
    val thought = mutableStateOf("")
    var streamingAnswerId by mutableStateOf<Long?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var storageError by mutableStateOf<String?>(null)
        private set
    private var generation: Job? = null
    private var handle: StreamCallHandle? = null
    private var unavailable = false
    private var pendingActions by mutableStateOf(0)
    val hasPendingAction: Boolean get() = pendingActions > 0
    var pendingWriteCount by mutableStateOf(0)
        private set
    private val writes = ChatWriteQueue(scope) { count, failure ->
        pendingWriteCount = count
        storageError = failure?.let { S.chatSaveFailed(it.message ?: "") }
    }

    private var loadingJob: Job? = null
    private var loadVersion = 0L
    var loading by mutableStateOf(false)
        private set
    private var loadedBeforeDelete = false
    private val actions = mutableSetOf<Job>()

    init { retryLoad() }

    fun retryLoad(): Job? {
        if (unavailable || ready || loading) return loadingJob
        val version = ++loadVersion
        loading = true
        storageError = null
        return scope.launch(start = CoroutineStart.LAZY) {
            try {
                val loaded = load()
                if (!unavailable && version == loadVersion) {
                    messages.clear()
                    messages.addAll(loaded)
                    ready = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (!unavailable && version == loadVersion) storageError = S.chatLoadFailed(e.message ?: "")
            } finally {
                if (version == loadVersion) loading = false
            }
        }.also { loadingJob = it; it.start() }
    }

    fun write(block: suspend () -> Unit): Deferred<Result<Unit>> =
        if (unavailable) notWritable() else writes.submit(block = block)

    fun saveMessage(message: UiChatMessage): Deferred<Result<Unit>> =
        if (unavailable) notWritable() else writes.submit(message.id) { save(message) }

    fun retryPendingWrites(): Deferred<Result<Unit>> =
        if (unavailable) notWritable() else writes.retry()

    fun afterWrite(
        result: Deferred<Result<Unit>>,
        onFailure: () -> Unit = {},
        onSuccess: () -> Unit
    ): Job {
        pendingActions++
        val action = scope.launch(start = CoroutineStart.LAZY) {
            if (result.await().isFailure) onFailure()
            if (!writes.awaitCommitted(result)) return@launch
            writes.awaitDrained()
            if (ready && !unavailable) {
                try {
                    onSuccess()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    storageError = S.chatStartFailed(failure.message ?: S.chatRetry)
                }
            }
        }
        actions.add(action)
        action.invokeOnCompletion { actions.remove(action); pendingActions-- }
        action.start()
        return action
    }

    private fun cancelPendingActions() { actions.toList().forEach { it.cancel() } }

    private fun notWritable(): Deferred<Result<Unit>> =
        CompletableDeferred(Result.failure(IllegalStateException(S.chatNotebookNotWritable)))

    fun updateMessageContent(id: Long, content: String): Deferred<Result<Unit>> =
        write { updateContent(id, content) }

    fun deleteMessages(ids: List<Long>): Deferred<Result<Unit>> =
        if (ids.isEmpty()) CompletableDeferred(Result.success(Unit)) else write { deleteMessagesByIds(ids) }

    fun replaceQuestionAndDropFollowing(
        questionId: Long,
        text: String,
        followingIds: List<Long>
    ): Deferred<Result<Unit>> = write { replaceQuestion(questionId, text, followingIds) }

    fun start(
        answerId: Long,
        reasoningTag: String?,
        sourceBinding: suspend () -> Pair<Long?, String?> = { null to null },
        stream: suspend (StreamCallHandle, suspend (String, String?) -> Unit) -> Result<ChatCompletionResult>
    ): Boolean {
        if (!ready || streamingAnswerId != null) return false
        val slot = messages.indexOfFirst { it.id == answerId }
        if (slot < 0) return false
        onRunningChanged(true)
        val previousMessage = messages[slot]
        var initial = previousMessage.copy(content = "", reasoningText = null, reasoningTag = reasoningTag,
            answerSeconds = 0, promptTokens = 0, cachedTokens = 0,
            firstTokenMillis = null, generationMillis = null, cachedTokensKnown = false,
            sourceNoteId = null, sourceRevision = null)
        messages[slot] = initial
        thought.value = ""
        val callHandle = StreamCallHandle()
        handle = callHandle
        streamingAnswerId = answerId
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var startedAt: Long? = null
            var firstTokenMillis: Long? = null
            val scheduler = StreamUiScheduler(
                onThought = { thought.value = it },
                onFlush = { content, reasoning ->
                    update(initial.copy(content = content, reasoningText = reasoning))
                    thought.value = reasoning.orEmpty()
                }
            )
            var result: ChatCompletionResult? = null
            var failure: Throwable? = null
            var initialPersistenceFailed = false
            try {
                val binding = sourceBinding()
                initial = initial.copy(sourceNoteId = binding.first, sourceRevision = binding.second)
                update(initial)
                initialPersistenceFailed = true
                if (saveMessage(initial).await().isFailure) return@launch
                initialPersistenceFailed = false
                startedAt = monotonicNanos()
                val response = stream(callHandle) { text, reasoning ->
                    val arrivedAt = monotonicNanos()
                    withContext(dispatcher) {
                        if (firstTokenMillis == null && text.isNotBlank()) {
                            firstTokenMillis = ((arrivedAt - startedAt!!) / 1_000_000L).coerceAtLeast(0L)
                        }
                        scheduler.append(text, reasoning)
                    }
                }
                result = response.getOrNull()
                failure = response.exceptionOrNull()
            } catch (e: CancellationException) {
                failure = e
            } catch (e: Exception) {
                failure = e
            } finally {
                val elapsedMillis = startedAt?.let { ((monotonicNanos() - it) / 1_000_000L).coerceAtLeast(0L) }
                withContext(NonCancellable) {
                    if (initialPersistenceFailed && !callHandle.isCancelled) {
                        try {
                            update(previousMessage)
                            saveMessage(previousMessage).await()
                        } finally {
                            handle = null
                            generation = null
                            streamingAnswerId = null
                            thought.value = ""
                            onRunningChanged(false)
                        }
                        return@withContext
                    }
                    scheduler.forceFlush()
                    val partial = scheduler.getFinalContent()
                    val content = when {
                        callHandle.isCancelled -> partial.ifBlank { S.chatStopped }
                        result != null -> result.content.ifBlank { partial }.ifBlank { S.chatEmptyReply }
                        partial.isNotBlank() -> S.chatNetworkInterrupted(partial, failure?.message ?: S.chatConnectionClosed)
                        else -> S.chatRequestFailed(failure?.message ?: S.chatNetworkError)
                    }
                    val final = initial.copy(
                        content = content,
                        reasoningText = result?.reasoningText ?: scheduler.getFinalReasoning(),
                        answerSeconds = elapsedMillis?.div(1000L)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0,
                        firstTokenMillis = firstTokenMillis,
                        generationMillis = elapsedMillis,
                        promptTokens = result?.promptTokens ?: 0,
                        cachedTokens = result?.cachedTokens ?: 0,
                        cachedTokensKnown = result?.cachedTokensKnown ?: false
                    )
                    update(final)
                    try {
                        saveMessage(final).await()
                    } finally {
                        handle = null
                        generation = null
                        streamingAnswerId = null
                        thought.value = ""
                        onRunningChanged(false)
                    }
                }
            }
        }
        generation = job.takeUnless { it.isCompleted }
        return true
    }

    suspend fun prepareDelete() {
        check(streamingAnswerId == null) { S.chatStopBeforeDelete }
        val previousReady = ready
        val previousError = storageError
        loadedBeforeDelete = ready
        unavailable = true
        ready = false
        cancelPendingActions()
        loadVersion++
        loadingJob?.cancel()
        loading = false
        storageError = S.notebookDeleting
        try {
            writes.awaitIdle()
        } catch (e: Exception) {
            unavailable = false
            ready = previousReady
            storageError = previousError
            throw e
        }
    }

    fun deletionFailed() {
        unavailable = false
        ready = loadedBeforeDelete
        storageError = S.chatDeleteFailed
    }

    internal fun canRelease(): Boolean = !loading && !unavailable && (ready || storageError != null) &&
        streamingAnswerId == null && pendingActions == 0 && writes.isIdle && generation?.isCompleted != false

    internal fun releaseIfIdle(): Boolean {
        if (!canRelease()) return false
        unavailable = true
        ready = false
        scope.cancel()
        messages.clear()
        thought.value = ""
        return true
    }

    fun stop() {
        cancelPendingActions()
        handle?.cancel()
        generation?.cancel()
    }

    private fun update(message: UiChatMessage) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index >= 0) messages[index] = message
    }
}
