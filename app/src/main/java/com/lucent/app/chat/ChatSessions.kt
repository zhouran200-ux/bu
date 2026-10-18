package com.lucent.app.chat

import com.lucent.app.i18n.S

import android.content.Context
import androidx.room.withTransaction
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.ChatMessage
import com.lucent.app.data.ChatScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object ChatSessions {
    private val sessions = java.util.concurrent.ConcurrentHashMap<Long, Pair<ChatSession, Long>>()
    private val accessClock = java.util.concurrent.atomic.AtomicLong()
    private val pageOwners = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    private val deletedNotebooks = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Long, Boolean>())
    private const val MAX_IDLE_SESSIONS = 4
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = running.asStateFlow()

    private fun scheduleIdleTrim() {
        maintenanceScope.launch {
            delay(250)
            trimIdle()
        }
    }

    fun retain(notebookId: Long) {
        pageOwners.compute(notebookId) { _, count -> (count ?: 0) + 1 }
    }

    fun release(notebookId: Long) {
        val next = pageOwners.compute(notebookId) { _, count ->
            if (count == null || count <= 1) null else count - 1
        }
        trimIdle()
        if (next == null) scheduleIdleTrim()
    }

    private fun touch(id: Long) {
        sessions.computeIfPresent(id) { _, entry -> entry.first to accessClock.incrementAndGet() }
    }

    private fun trimIdle(excluded: Long? = null) {
        val idle = sessions.entries.asSequence()
            .filter { (id, entry) -> id != excluded && !pageOwners.containsKey(id) && entry.first.canRelease() }
            .associate { it.key to it.value.second }
        for (id in evictionOrder(idle, MAX_IDLE_SESSIONS)) {
            val entry = sessions[id] ?: continue
            if (entry.first.releaseIfIdle()) sessions.remove(id, entry)
        }
    }

    fun forNotebook(context: Context, notebookId: Long): ChatSession {
        trimIdle(notebookId)
        return sessions.computeIfAbsent(notebookId) {
            createSession(context.applicationContext, notebookId) to accessClock.incrementAndGet()
        }.also { touch(notebookId) }.first
    }

    private fun createSession(app: Context, notebookId: Long): ChatSession {
        val dao = AppDatabase.getInstance(app).chatDao()
        val conversationId = ChatScope.forNotebook(notebookId)
        return ChatSession(
            dispatcher = Dispatchers.Main.immediate,
            load = {
                check(notebookId !in deletedNotebooks) { S.notebookDeleting }
                val messages = dao.getForConversationOnce(conversationId)
                withContext(Dispatchers.Default) {
                    messages.map { message ->
                        UiChatMessage(message.id, message.role, message.content, message.reasoningText,
                            message.answerSeconds, promptTokens = message.tokens,
                            cachedTokens = message.cachedTokens, timestamp = message.timestamp,
                            firstTokenMillis = message.firstTokenMillis, generationMillis = message.generationMillis,
                            cachedTokensKnown = message.cachedTokensKnown,
                            sourceNoteId = message.sourceNoteId, sourceRevision = message.sourceRevision)
                    }.filter { it.role != "assistant" || it.content.isNotBlank() || !it.reasoningText.isNullOrBlank() }
                }
            },
            save = { message ->
                dao.upsert(mergeChatMessage(dao.getById(message.id), message, conversationId))
            },
            updateContent = { id, content -> dao.updateContent(id, content) },
            deleteMessagesByIds = { ids -> dao.deleteByIds(ids) },
            replaceQuestion = { questionId, text, following ->
                AppDatabase.getInstance(app).withTransaction {
                    dao.updateContent(questionId, text)
                    if (following.isNotEmpty()) dao.deleteByIds(following)
                }
            },
            onRunningChanged = { active ->
                if (active) {
                    check(notebookId !in deletedNotebooks) { S.notebookDeleting }
                    running.value += 1
                    try {
                        ChatGenerationService.ensureRunning(app)
                    } catch (e: Exception) {
                        running.value -= 1
                        throw e
                    }
                } else {
                    running.value -= 1
                    scheduleIdleTrim()
                }
            }
        )
    }

    fun isRunning(notebookId: Long): Boolean = sessions[notebookId]?.first?.streamingAnswerId != null

    suspend fun prepareDelete(notebookId: Long) {
        check(!isRunning(notebookId)) { S.chatStopBeforeDelete }
        check(deletedNotebooks.add(notebookId)) { S.notebookDeleting }
        try {
            sessions[notebookId]?.first?.prepareDelete()
        } catch (e: Exception) {
            deletedNotebooks.remove(notebookId)
            throw e
        }
    }

    fun finishDelete(notebookId: Long, success: Boolean) {
        if (success) {
            sessions.remove(notebookId)
        } else {
            deletedNotebooks.remove(notebookId)
            sessions[notebookId]?.first?.deletionFailed()
        }
    }

    fun stopAll() {
        sessions.values.forEach { it.first.stop() }
    }
}

internal fun mergeChatMessage(
    existing: ChatMessage?,
    message: UiChatMessage,
    conversationId: Long
): ChatMessage {
    val base = existing ?: ChatMessage(
        id = message.id,
        role = message.role,
        content = message.content,
        timestamp = message.timestamp,
        conversationId = conversationId
    )
    return base.copy(
        role = message.role,
        content = message.content,
        timestamp = message.timestamp,
        conversationId = conversationId,
        reasoningText = message.reasoningText,
        answerSeconds = message.answerSeconds,
        tokens = message.promptTokens,
        cachedTokens = message.cachedTokens,
        firstTokenMillis = message.firstTokenMillis,
        generationMillis = message.generationMillis,
        cachedTokensKnown = message.cachedTokensKnown,
        sourceNoteId = message.sourceNoteId,
        sourceRevision = message.sourceRevision
    )
}
