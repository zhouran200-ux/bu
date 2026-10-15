package com.lucent.app.chat

import android.content.Context
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.ChatMessage
import com.lucent.app.data.ChatScope
import com.lucent.app.ui.UiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ChatSessions {
    private val sessions = mutableMapOf<Long, ChatSession>()
    private val deletedNotebooks = mutableSetOf<Long>()
    private val running = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = running.asStateFlow()

    fun forNotebook(context: Context, notebookId: Long): ChatSession = sessions.getOrPut(notebookId) {
        val app = context.applicationContext
        val dao = AppDatabase.getInstance(app).chatDao()
        val conversationId = ChatScope.forNotebook(notebookId)
        ChatSession(
            dispatcher = Dispatchers.Main.immediate,
            load = {
                check(notebookId !in deletedNotebooks) { "笔记本正在删除或已删除" }
                dao.getForConversationOnce(conversationId).map { message ->
                    UiChatMessage(message.id, message.role, message.content, message.reasoningText,
                        message.answerSeconds, promptTokens = message.tokens,
                        cachedTokens = message.cachedTokens, timestamp = message.timestamp,
                        firstTokenMillis = message.firstTokenMillis, generationMillis = message.generationMillis,
                        cachedTokensKnown = message.cachedTokensKnown)
                }
            },
            save = { message ->
                dao.upsert(ChatMessage(
                    id = message.id, role = message.role, content = message.content,
                    timestamp = message.timestamp, conversationId = conversationId,
                    reasoningText = message.reasoningText, answerSeconds = message.answerSeconds,
                    tokens = message.promptTokens, cachedTokens = message.cachedTokens,
                    firstTokenMillis = message.firstTokenMillis, generationMillis = message.generationMillis,
                    cachedTokensKnown = message.cachedTokensKnown
                ))
            },
            onRunningChanged = { active ->
                if (active) {
                    check(notebookId !in deletedNotebooks) { "笔记本正在删除或已删除" }
                    running.value += 1
                    try {
                        ChatGenerationService.ensureRunning(app)
                    } catch (e: Exception) {
                        running.value -= 1
                        throw e
                    }
                } else {
                    running.value -= 1
                }
            }
        )
    }

    fun isRunning(notebookId: Long): Boolean = sessions[notebookId]?.streamingAnswerId != null

    suspend fun prepareDelete(notebookId: Long) {
        check(!isRunning(notebookId)) { "请先停止此笔记本中的回答，再删除笔记本" }
        check(deletedNotebooks.add(notebookId)) { "笔记本正在删除或已删除" }
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
