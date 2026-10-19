package com.lucent.app.ui.chat

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.chat.ChatSession
import com.lucent.app.chat.UiChatMessage
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.Note
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.i18n.S
import com.lucent.app.ui.ChatScrollMath
import com.lucent.app.ui.Haptics
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.PendingChatDelete
import com.lucent.app.ui.buildChatHistory
import com.lucent.app.ui.resolveChatEndpoint
import com.lucent.app.ui.resolveChatReasoning
import com.lucent.app.ui.startChatStream

internal class ChatActionsCoordinator(
    private val context: Context,
    internal val session: ChatSession,
    internal val listState: LazyListState,
    private val sourceNotes: List<Note>,
    internal val prefs: SharedPreferences,
    private val notebookId: Long,
    private val notificationPermission: ManagedActivityResultLauncher<String, Boolean>,
    private val focusManager: androidx.compose.ui.focus.FocusManager,
    private val keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    internal val messages = session.messages
    internal val streamingAnswerId: Long? get() = session.streamingAnswerId
    internal var inputText by mutableStateOf("")
    internal var followStream by mutableStateOf(false)
    internal var regenerateReserve by mutableStateOf<Pair<Long, Int>?>(null)
    internal var pendingDelete by mutableStateOf<PendingChatDelete?>(null)
    internal var profilesJson: String by mutableStateOf("")
    internal val savedProfiles: List<com.lucent.app.data.ApiProfile>
        get() = ApiProfiles.parseStored(profilesJson) ?: ApiProfiles.DEFAULT_PROFILES
    internal var selectedProfileId by mutableStateOf(prefs.getString("profile_id_$notebookId", "") ?: "")
    internal var selectedProfileName by mutableStateOf(prefs.getString("profile_name_$notebookId", "") ?: "")
    internal var selectedModel by mutableStateOf(prefs.getString("model_$notebookId", "") ?: "")
    internal var reasoningPreset by mutableStateOf(
        ReasoningPreset.fromKey(
            prefs.getString("reasoning_preset_$notebookId", null)
                ?: prefs.getString("reasoning_$notebookId", ReasoningPreset.DEFAULT.key)
        )
    )
    internal var systemPrompt by mutableStateOf(prefs.getString("prompt_$notebookId", "") ?: "")
    internal var historyRounds by androidx.compose.runtime.mutableIntStateOf(prefs.getInt("history_rounds_$notebookId", 10))

    fun streamReserveBlocksFollow(): Boolean {
        val entry = regenerateReserve ?: return false
        if (streamingAnswerId != entry.first) return false
        val info = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == "message:${entry.first}" } ?: return false
        return info.size <= entry.second
    }

    fun settlePendingDelete() {
        val pending = pendingDelete ?: return
        pendingDelete = null
        session.deleteMessages(listOf(pending.message.id))
    }

    fun askModel(answerId: Long) {
        if (!session.ready || session.pendingWriteCount > 0) return
        val selection = ApiProfiles.resolveSelection(savedProfiles, selectedProfileId, selectedProfileName, selectedModel)
        val matchedProfile = selection?.first
        val targetModel = selection?.second.orEmpty()
        val aiMsgId = answerId
        val answerStamp = messages.firstOrNull { it.id == aiMsgId }?.timestamp ?: System.currentTimeMillis()
        fun upsertAnswerSlot(msg: UiChatMessage) {
            val slot = messages.indexOfFirst { it.id == msg.id }
            if (slot >= 0) messages[slot] = msg else messages.add(msg)
        }
        if (targetModel.isBlank()) {
            LucentToast.show(context, S.chatNoModelsForProfile)
            val blankIdx = messages.indexOfFirst { it.id == aiMsgId }
            if (blankIdx >= 0) {
                messages[blankIdx] = messages[blankIdx].copy(content = S.chatNoModelsAnswer)
                session.saveMessage(messages[blankIdx])
            }
            return
        }

        val endpoint = resolveChatEndpoint(matchedProfile)
        val resolvedReasoning = resolveChatReasoning(reasoningPreset, matchedProfile, targetModel)
        val effortLabel = resolvedReasoning.displayTag

        if (sourceNotes.size > 1) {
            LucentToast.show(context, S.chatSourceBindingBroken)
            return
        }
        val sourceNote = sourceNotes.singleOrNull()
        val customPrompt = systemPrompt

        val historyPayload = buildChatHistory(messages, aiMsgId, historyRounds)

        if (endpoint.apiKey.isBlank()) {
            val pendingAi = UiChatMessage(
                id = aiMsgId,
                role = "assistant",
                content = S.chatMissingApiKey
            )
            upsertAnswerSlot(pendingAi.copy(timestamp = answerStamp))
            session.saveMessage(pendingAi.copy(timestamp = answerStamp))
        } else {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                !prefs.getBoolean("generation_notification_asked", false) &&
                androidx.core.content.ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                prefs.edit().putBoolean("generation_notification_asked", true).apply()
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            try {
                startChatStream(
                    session = session,
                    answerId = aiMsgId,
                    effortLabel = effortLabel,
                    sourceNote = sourceNote,
                    customPrompt = customPrompt,
                    endpoint = endpoint,
                    model = targetModel,
                    historyPayload = historyPayload,
                    resolvedReasoning = resolvedReasoning
                )
            } catch (e: Exception) {
                val failed = UiChatMessage(aiMsgId, "assistant",
                    S.chatStartFailed(e.message ?: S.chatRetry), timestamp = answerStamp)
                upsertAnswerSlot(failed)
                session.saveMessage(failed)
                LucentToast.show(context, S.chatStartFailedToast)
            }
        }
    }

    fun startAnswer(promptText: String) {
        val userMsg = UiChatMessage(
            id = System.currentTimeMillis(),
            role = "user",
            content = promptText
        )
        messages.add(userMsg)
        val saved = session.saveMessage(userMsg)
        val answerId = userMsg.id + 1
        messages.add(UiChatMessage(id = answerId, role = "assistant", content = "", timestamp = answerId))
        followStream = true
        session.afterWrite(saved, onFailure = {
            messages.removeAll { it.id == answerId }
            LucentToast.show(context, S.chatQuestionNotSaved)
        }) { askModel(answerId) }
    }

    fun regenerateAnswer(answerId: Long) {
        if (!session.ready || session.pendingWriteCount > 0 || session.hasPendingAction) return
        if (streamingAnswerId != null) {
            LucentToast.show(context, S.chatStillAnswering)
            return
        }
        val idx = messages.indexOfFirst { it.id == answerId }
        if (idx < 0) return
        val question = messages.take(idx).lastOrNull { it.role == "user" }?.content.orEmpty()
        if (question.isBlank()) {
            LucentToast.show(context, S.chatRegenerateTargetMissing)
            return
        }
        val selection = ApiProfiles.resolveSelection(savedProfiles, selectedProfileId, selectedProfileName, selectedModel)
        if (selection == null || selection.second.isBlank() || selection.first.apiKey.isBlank()) {
            LucentToast.show(context, S.chatNoModelOrKey)
            return
        }
        settlePendingDelete()
        val atBottom = !listState.canScrollForward
        followStream = ChatScrollMath.shouldFollowRegeneration(
            isLastMessage = idx == messages.lastIndex,
            atBottom = atBottom
        )
        val itemIndex = idx + 1
        val layout = listState.layoutInfo
        val oldSize = layout.visibleItemsInfo.firstOrNull { it.key == "message:$answerId" }?.size
        val readableHeight = (layout.viewportSize.height - layout.afterContentPadding).coerceAtLeast(1)
        val reserve = oldSize?.let { minOf(it, readableHeight) }
        if (reserve != null) regenerateReserve = answerId to reserve
        val answerTopHidden = listState.firstVisibleItemIndex == itemIndex &&
            listState.firstVisibleItemScrollOffset > 0
        if (reserve != null && oldSize != null && oldSize > reserve || answerTopHidden) {
            listState.requestScrollToItem(itemIndex, 0)
        }
        session.afterWrite(session.retryPendingWrites()) { askModel(answerId) }
    }

    fun resendEditedQuestion(questionId: Long, newText: String) {
        if (!session.ready || session.streamingAnswerId != null || session.pendingWriteCount > 0 || session.hasPendingAction) return
        val idx = messages.indexOfFirst { it.id == questionId }
        if (idx < 0) return
        settlePendingDelete()
        val followingIds = messages.drop(idx + 1).map { it.id }
        while (messages.size > idx + 1) messages.removeAt(messages.size - 1)
        messages[idx] = messages[idx].copy(content = newText)
        val saved = session.replaceQuestionAndDropFollowing(questionId, newText, followingIds)
        session.afterWrite(saved) {
            val answerId = System.currentTimeMillis() + 1
            messages.add(UiChatMessage(id = answerId, role = "assistant", content = "", timestamp = answerId))
            askModel(answerId)
        }
    }

    fun saveAnswerEdit(answerId: Long, newText: String) {
        if (!session.ready || session.streamingAnswerId != null || session.pendingWriteCount > 0 || session.hasPendingAction) return
        val idx = messages.indexOfFirst { it.id == answerId }
        if (idx < 0) return
        messages[idx] = messages[idx].copy(content = newText)
        session.updateMessageContent(answerId, newText)
    }

    fun deleteMessage(target: UiChatMessage) {
        if (!session.ready || session.streamingAnswerId != null || session.pendingWriteCount > 0 || session.hasPendingAction) return
        val idx = messages.indexOfFirst { it.id == target.id }
        if (idx < 0) return
        if (pendingDelete != null) LucentToast.show(context, S.chatPreviousDeleteApplied)
        settlePendingDelete()
        val firstIndex = listState.firstVisibleItemIndex
        val keys = listOf("header") + messages.map { "message:${it.id}" } + "footer"
        val viewport = com.lucent.app.ui.ReadingPosition(
            itemKey = keys.getOrElse(firstIndex) { "header" },
            index = firstIndex,
            offset = listState.firstVisibleItemScrollOffset
        )
        messages.removeAt(idx)
        pendingDelete = PendingChatDelete(message = target, index = idx, viewport = viewport)
    }

    fun undoDelete() {
        val pending = pendingDelete ?: return
        pendingDelete = null
        if (messages.none { it.id == pending.message.id }) {
            val at = pending.index.coerceIn(0, messages.size)
            messages.add(at, pending.message)
            followStream = false
            val keys = listOf("header") + messages.map { "message:${it.id}" } + "footer"
            val viewport = pending.viewport.resolve(keys)
            listState.requestScrollToItem(viewport.index, viewport.offset)
        }
    }

    fun stopStreaming() {
        if (streamingAnswerId == null) return
        session.stop()
        Haptics.tick(context)
    }

    val onSendMessage: () -> Unit = send@{
        val text = inputText.trim()
        if (text.isEmpty()) return@send
        if (!session.ready || session.pendingWriteCount > 0 || session.hasPendingAction) {
            LucentToast.show(context, session.storageError ?: S.chatLoadingRetry)
            return@send
        }
        if (streamingAnswerId != null) {
            LucentToast.show(context, S.chatStreamingHint)
            return@send
        }
        inputText = ""
        Haptics.tick(context)
        focusManager.clearFocus()
        keyboardController?.hide()
        startAnswer(text)
    }
}
