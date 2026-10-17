package com.lucent.app.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.network.ChatCompletionResult
import com.lucent.app.network.StreamCallHandle
import com.lucent.app.ui.StreamUiScheduler
import com.lucent.app.ui.UiChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext

class ChatSession(
    private val dispatcher: CoroutineDispatcher,
    private val load: suspend () -> List<UiChatMessage>,
    private val save: suspend (UiChatMessage) -> Unit,
    private val onRunningChanged: (Boolean) -> Unit = {},
    private val monotonicNanos: () -> Long = System::nanoTime
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
    private var writeTail: Job? = null
    private var unavailable = false
    private val pendingMessages = linkedMapOf<Long, UiChatMessage>()
    var unsavedMessageCount by mutableStateOf(0)
        private set

    init {
        scope.launch {
            try {
                messages.addAll(load())
                ready = !unavailable
            } catch (e: Exception) {
                storageError = "读取对话失败：${e.message}"
            }
        }
    }

    fun write(block: suspend () -> Unit): Deferred<Result<Unit>> {
        if (unavailable) return CompletableDeferred(Result.failure(IllegalStateException("笔记本不可写")))
        val previous = writeTail
        return scope.async(start = CoroutineStart.LAZY) {
            previous?.join()
            try {
                block()
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                storageError = "保存对话失败：" + failure.message
                Result.failure(failure)
            }
        }.also { writeTail = it; it.start() }
    }

    fun saveMessage(message: UiChatMessage): Deferred<Result<Unit>> {
        pendingMessages[message.id] = message
        unsavedMessageCount = pendingMessages.size
        return write {
            save(message)
            if (pendingMessages[message.id] === message) pendingMessages.remove(message.id)
            unsavedMessageCount = pendingMessages.size
            if (pendingMessages.isEmpty()) storageError = null
        }
    }

    fun retryUnsavedMessages(): Deferred<Result<Unit>> = write {
        for (pending in pendingMessages.values.toList()) {
            val current = messages.firstOrNull { it.id == pending.id }
            if (current == null) {
                pendingMessages.remove(pending.id)
            } else {
                save(current)
                if (pendingMessages[pending.id] === pending) pendingMessages.remove(pending.id)
            }
            unsavedMessageCount = pendingMessages.size
        }
        if (pendingMessages.isEmpty()) storageError = null
    }

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
        var initial = messages[slot].copy(content = "", reasoningText = null, reasoningTag = reasoningTag,
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
            try {
                val binding = sourceBinding()
                initial = initial.copy(sourceNoteId = binding.first, sourceRevision = binding.second)
                update(initial)
                saveMessage(initial).await().getOrThrow()
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
                    scheduler.forceFlush()
                    val partial = scheduler.getFinalContent()
                    val content = when {
                        callHandle.isCancelled -> partial.ifBlank { "（已停止生成）" }
                        result != null -> result.content.ifBlank { partial }.ifBlank { "（模型返回了空回复）" }
                        partial.isNotBlank() -> "$partial\n\n[网络中断：${failure?.message ?: "连接已关闭"}，已保留已生成内容]"
                        else -> "【请求失败】${failure?.message ?: "网络或接口异常"}\n\n请检查网络连接、API 密钥与模型端点配置。"
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
        check(streamingAnswerId == null) { "请先停止此笔记本中的回答，再删除笔记本" }
        val previousReady = ready
        val previousError = storageError
        unavailable = true
        ready = false
        storageError = "笔记本正在删除或已删除"
        try {
            writeTail?.join()
        } catch (e: Exception) {
            unavailable = false
            ready = previousReady
            storageError = previousError
            throw e
        }
    }

    fun deletionFailed() {
        unavailable = false
        ready = true
        storageError = "删除失败，请重试"
    }

    fun stop() {
        handle?.cancel()
        generation?.cancel()
    }

    private fun update(message: UiChatMessage) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index >= 0) messages[index] = message
    }
}
