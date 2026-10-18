package com.lucent.app.chat

import com.lucent.app.i18n.S

import android.content.Context
import androidx.room.withTransaction
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.ChatMessage
import com.lucent.app.data.ChatScope
import com.lucent.app.chat.UiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ChatSessions {
    private val sessions = linkedMapOf<Long, ChatSession>()
    private val pageOwners = mutableMapOf<Long, Int>()
    private const val MAX_IDLE_SESSIONS = 4
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun scheduleIdleTrim() {
        maintenanceScope.launch {
            delay(250)
            trimIdle()
        }
    }

    fun retain(notebookId: Long) {
        pageOwners[notebookId] = (pageOwners[notebookId] ?: 0) + 1
    }

    fun release(notebookId: Long) {
        val count = (pageOwners[notebookId] ?: 1) - 1
        if (count <= 0) pageOwners.remove(notebookId) else pageOwners[notebookId] = count
        trimIdle()
        scheduleIdleTrim()
    }

    private fun trimIdle(excluded: Long? = null) {
        val idle = sessions.filter { (id, session) -> id != excluded && id !in pageOwners && session.canRelease() }
        for ((id, session) in idle.entries.take((idle.size - MAX_IDLE_SESSIONS).coerceAtLeast(0))) {
            if (session.releaseIfIdle()) sessions.remove(id)
        }
    }
    private val deletedNotebooks = mutableSetOf<Long>()
    private val running = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = running.asStateFlow()

    fun forNotebook(context: Context, notebookId: Long): ChatSession {
        trimIdle(notebookId)
        return sessions.getOrPut(notebookId) {
        val app = context.applicationContext
        val dao = AppDatabase.getInstance(app).chatDao()
        val conversationId = ChatScope.forNotebook(notebookId)
        ChatSession(
            dispatcher = Dispatchers.Main.immediate,
            load = {
                check(notebookId !in deletedNotebooks) { S.notebookDeleting }
                dao.getForConversationOnce(conversationId).map { message ->
                    UiChatMessage(message.id, message.role, message.content, message.reasoningText,
                        message.answerSeconds, promptTokens = message.tokens,
                        cachedTokens = message.cachedTokens, timestamp = message.timestamp,
                        firstTokenMillis = message.firstTokenMillis, generationMillis = message.generationMillis,
                        cachedTokensKnown = message.cachedTokensKnown,
                        sourceNoteId = message.sourceNoteId, sourceRevision = message.sourceRevision)
                }.filter { it.role != "assistant" || it.content.isNotBlank() || !it.reasoningText.isNullOrBlank() }
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

    }

    fun isRunning(notebookId: Long): Boolean = sessions[notebookId]?.streamingAnswerId != null

    suspend fun prepareDelete(notebookId: Long) {
        check(!isRunning(notebookId)) { S.chatStopBeforeDelete }
        check(deletedNotebooks.add(notebookId)) { S.notebookDeleting }
        try {
            sessions[notebookId]?.prepareDelete()
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
            sessions[notebookId]?.deletionFailed()
        }
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
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
