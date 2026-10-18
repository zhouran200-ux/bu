package com.lucent.app.ui

import com.lucent.app.i18n.S
import com.lucent.app.chat.UiChatMessage

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.lucent.app.chat.ChatSessions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.CitationRef
import com.lucent.app.data.Note
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue



@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NotebookChatTab(
    notebookId: Long,
    sourceNotes: List<Note>,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onOpenSource: (Long, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val citationScope = rememberCoroutineScope()
    val openCitation: (CitationRef) -> Unit = { ref ->
        openCitationTarget(citationScope, context, sourceNotes, ref, onOpenSource)
    }
    val session = remember(notebookId) { ChatSessions.forNotebook(context, notebookId) }
    DisposableEffect(session, notebookId) {
        ChatSessions.retain(notebookId)
        onDispose { ChatSessions.release(notebookId) }
    }
    if (!session.ready) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(session.storageError ?: S.chatLoadingConversation, color = LocalOnGradient.current)
        }
        return
    }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) LucentToast.show(context, S.chatNotificationDenied)
    }
    val scope = rememberCoroutineScope()
    val messages = session.messages
    val readingKeys = listOf("header") + messages.map { "message:${it.id}" } + "footer"
    val listState = rememberReadingListState("chat:$notebookId", readingKeys)
    val atBottom by remember {
        derivedStateOf {
            !listState.canScrollForward
        }
    }
    var followStream by remember(notebookId) { mutableStateOf(false) }
    val viewportAnchor = remember(notebookId) { ChatViewportAnchor() }
    LaunchedEffect(active) {
        if (!active) followStream = false
    }
    val readingScrollConnection = remember(listState) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y != 0f) {
                    followStream = false
                    viewportAnchor.userScrolled()
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                followStream = ChatScrollMath.shouldFollowAfterScroll(
                    wasFollowing = followStream,
                    atBottom = !listState.canScrollForward,
                    userScrolling = true
                )
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    var showHistorySheet by remember { mutableStateOf(false) }
    var historyAnchorId by remember { mutableStateOf<Long?>(null) }

    val prefs = remember(notebookId) {
        context.getSharedPreferences("lucent_notebook_chat_prefs", Context.MODE_PRIVATE)
    }

    var inputText by remember { mutableStateOf("") }
    val settingsRepo = remember { SettingsRepository(context) }
    val profilesJson by settingsRepo.apiProfilesJson.collectAsState(initial = SettingsCache.apiProfilesJson)
    val savedProfiles = remember(profilesJson) {
        ApiProfiles.parseStored(profilesJson) ?: ApiProfiles.DEFAULT_PROFILES
    }
    var selectedProfileId by remember(notebookId) {
        mutableStateOf(prefs.getString("profile_id_$notebookId", "") ?: "")
    }
    var selectedProfileName by remember(notebookId) {
        mutableStateOf(prefs.getString("profile_name_$notebookId", "") ?: "")
    }
    var selectedModel by remember(notebookId) {
        mutableStateOf(prefs.getString("model_$notebookId", "") ?: "")
    }

    val resolvedSelection = ApiProfiles.resolveSelection(savedProfiles, selectedProfileId, selectedProfileName, selectedModel)
    LaunchedEffect(notebookId, resolvedSelection) {
        val (profile, model) = resolvedSelection ?: return@LaunchedEffect
        if (profile.id != selectedProfileId || profile.name != selectedProfileName || model != selectedModel) {
            selectedProfileId = profile.id
            selectedProfileName = profile.name
            selectedModel = model
            prefs.edit()
                .putString("profile_id_$notebookId", profile.id)
                .putString("profile_name_$notebookId", profile.name)
                .putString("model_$notebookId", model)
                .apply()
        }
    }
    var reasoningPreset by remember {
        val savedKey = prefs.getString("reasoning_preset_$notebookId", null)
            ?: prefs.getString("reasoning_$notebookId", ReasoningPreset.DEFAULT.key)
        mutableStateOf(ReasoningPreset.fromKey(savedKey))
    }
    var systemPrompt by remember {
        mutableStateOf(prefs.getString("prompt_$notebookId", "") ?: "")
    }
    var historyRounds by remember {
        mutableIntStateOf(prefs.getInt("history_rounds_$notebookId", 10))
    }
    var showDialogueSettingsDialog by remember { mutableStateOf(false) }
    var showProfileModelPickerSheet by remember { mutableStateOf(false) }

    val placementSpec = rememberReorderPlacementSpec()
    val streamingAnswerId = session.streamingAnswerId
    var pendingDelete by remember { mutableStateOf<PendingChatDelete?>(null) }
    var questionToEdit by remember { mutableStateOf<UiChatMessage?>(null) }
    var answerToEdit by remember { mutableStateOf<UiChatMessage?>(null) }
    val streamingThought = session.thought
    LaunchedEffect(session.storageError) {
        session.storageError?.let { LucentToast.show(context, it) }
    }

    fun openHistorySheet() {
        historyAnchorId = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index in 1..messages.size }
            ?.let { messages.getOrNull(it.index - 1)?.id }
        showHistorySheet = true
    }

    fun settlePendingDelete() {
        val pending = pendingDelete ?: return
        pendingDelete = null
        session.deleteMessages(listOf(pending.message.id))
    }

    DisposableEffect(notebookId) {
        onDispose { settlePendingDelete() }
    }

    fun askModel(answerId: Long) {
        if (!session.ready) return
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
        scope.launch {
            delay(100)
            if (followStream && !listState.isScrollInProgress) {
                listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
            }
        }
        scope.launch {
            if (saved.await().isFailure) {
                messages.removeAll { it.id == answerId }
                LucentToast.show(context, S.chatQuestionNotSaved)
            } else {
                askModel(answerId)
            }
        }
    }

    fun regenerateAnswer(answerId: Long) {
        if (!session.ready) return
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
        messages[idx] = messages[idx].copy(
            content = "",
            reasoningText = null,
            answerSeconds = 0,
            promptTokens = 0,
            cachedTokens = 0,
            firstTokenMillis = null,
            generationMillis = null,
            cachedTokensKnown = false,
            sourceNoteId = null,
            sourceRevision = null
        )
        settlePendingDelete()
        session.deleteMessages(listOf(answerId))
        askModel(answerId)
    }

    fun resendEditedQuestion(questionId: Long, newText: String) {
        if (!session.ready || session.streamingAnswerId != null) return
        val idx = messages.indexOfFirst { it.id == questionId }
        if (idx < 0) return
        settlePendingDelete()
        val followingIds = messages.drop(idx + 1).map { it.id }
        while (messages.size > idx + 1) messages.removeAt(messages.size - 1)
        messages[idx] = messages[idx].copy(content = newText)
        session.replaceQuestionAndDropFollowing(questionId, newText, followingIds)
        val answerId = System.currentTimeMillis() + 1
        messages.add(UiChatMessage(id = answerId, role = "assistant", content = "", timestamp = answerId))
        askModel(answerId)
    }

    fun saveAnswerEdit(answerId: Long, newText: String) {
        if (!session.ready || session.streamingAnswerId != null) return
        val idx = messages.indexOfFirst { it.id == answerId }
        if (idx < 0) return
        messages[idx] = messages[idx].copy(content = newText)
        session.updateMessageContent(answerId, newText)
    }

    fun deleteMessage(target: UiChatMessage) {
        if (!session.ready || session.streamingAnswerId != null) return
        val idx = messages.indexOfFirst { it.id == target.id }
        if (idx < 0) return
        if (pendingDelete != null) LucentToast.show(context, S.chatPreviousDeleteApplied)
        settlePendingDelete()
        val firstIndex = listState.firstVisibleItemIndex
        val keys = listOf("header") + messages.map { "message:${it.id}" } + "footer"
        val viewport = ReadingPosition(
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

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible
    val barMotionOn = LocalBackgroundEnvironment.current.motionEnabled
    var showReasoningMenu by remember(notebookId) { mutableStateOf(false) }
    var reasoningButtonBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var chatCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    LaunchedEffect(active) { if (!active) showReasoningMenu = false }

    fun stopStreaming() {
        if (streamingAnswerId == null) return
        session.stop()
        Haptics.tick(context)
    }

    val streamProgress by remember(session) {
        derivedStateOf {
            val id = session.streamingAnswerId ?: return@derivedStateOf 0
            val body = messages.firstOrNull { it.id == id }?.content?.length ?: 0
            body + streamingThought.value.length
        }
    }
    LaunchedEffect(streamingAnswerId, active) {
        if (!active || streamingAnswerId == null) return@LaunchedEffect
        followStreamUpdates(
            updates = androidx.compose.runtime.snapshotFlow { streamProgress },
            canFollow = { followStream && !listState.isScrollInProgress },
            awaitLayout = {
                delay(80)
                androidx.compose.runtime.withFrameNanos { }
            },
            onFollow = {
                listState.requestScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            }
        )
    }

    val onSendMessage: () -> Unit = send@{
        val text = inputText.trim()
        if (text.isEmpty()) return@send
        if (!session.ready) {
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

    LaunchedEffect(listState, active, viewportAnchor) {
        if (!active) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow {
            listState.layoutInfo.viewportSize.height to !listState.canScrollForward
        }.collect { (height, bottom) ->
            if (viewportAnchor.update(height, bottom) && messages.isNotEmpty()) {
                val footer = listState.layoutInfo.totalItemsCount - 1
                if (footer >= 0) listState.requestScrollToItem(footer)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()
        .onGloballyPositioned { chatCoordinates = it }
        .pointerInput(showReasoningMenu) {
            if (showReasoningMenu) awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                    val position = chatCoordinates?.localToWindow(down.position) ?: continue
                    if (!reasoningButtonBounds.contains(position)) showReasoningMenu = false
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .nestedScroll(readingScrollConnection)
                    .fillMaxSize()
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "header") {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (session.unsavedMessageCount > 0) {
                            TextButton(onClick = { session.retryUnsavedMessages() }) {
                                Text(S.chatUnsavedAnswersRetry)
                            }
                        }
                    }
                }

                itemsIndexed(messages, key = { _, msg -> "message:${msg.id}" }) { _, msg ->
                    val actionsEnabled = session.ready && streamingAnswerId == null
                    val itemModifier = if (msg.id == streamingAnswerId) {
                        Modifier
                    } else {
                        Modifier.animateItem(
                            fadeInSpec = tween(200),
                            placementSpec = placementSpec,
                            fadeOutSpec = tween(140)
                        )
                    }
                    if (msg.role == "user") {
                        UserMessageCard(
                            modifier = itemModifier,
                            message = msg,
                            actionsEnabled = actionsEnabled,
                            onEdit = { questionToEdit = msg },
                            onDelete = { deleteMessage(msg) }
                        )
                    } else {
                        AiMessageCard(
                            modifier = itemModifier,
                            message = msg,
                            readingKey = "chat:$notebookId:thought:${msg.id}",
                            streaming = msg.id == streamingAnswerId,
                            liveThought = if (msg.id == streamingAnswerId) streamingThought else null,
                            actionsEnabled = actionsEnabled,
                            onRegenerate = { regenerateAnswer(msg.id) },
                            onEditAnswer = { answerToEdit = msg },
                            onDelete = { deleteMessage(msg) },
                            onCitationClick = openCitation
                        )
                    }
                }

                item(key = "footer") { Spacer(modifier = Modifier.height(8.dp)) }
            }
            FloatingScrollCapsule(
                listState = listState,
                itemKeys = readingKeys,
                enabled = active && !isImeVisible,
                onDragStart = {
                    followStream = false
                    viewportAnchor.userScrolled()
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
            }

            BottomGlassComposer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .then(
                        if (!isImeVisible) Modifier.navigationBarsPadding()
                        else Modifier
                    ),
                inputText = inputText,
                onInputChange = { inputText = it },
                showReasoningMenu = showReasoningMenu,
                onReasoningMenuChange = { showReasoningMenu = it },
                onReasoningButtonBounds = { reasoningButtonBounds = it },
                reasoningPreset = reasoningPreset,
                onSelectReasoning = {
                    reasoningPreset = it
                    saveReasoningSelection(prefs, notebookId, it)
                },
                onOpenHistory = { openHistorySheet() },
                onOpenDialogueSettings = { showDialogueSettingsDialog = true },
                selectedProfileName = resolvedSelection?.first?.name.orEmpty(),
                onOpenModelPicker = { showProfileModelPickerSheet = true },
                streaming = streamingAnswerId != null,
                onSend = onSendMessage,
                onStop = { stopStreaming() }
            )
        }

        ChatDeleteUndoLayer(
            pending = pendingDelete,
            motionOn = barMotionOn,
            imeVisible = isImeVisible,
            onUndo = { undoDelete() },
            onDismiss = { settlePendingDelete() }
        )

        ChatScrollToBottomButton(
            visible = !atBottom && !isImeVisible && messages.isNotEmpty(),
            motionOn = barMotionOn,
            onClick = {
                followStream = true
                scope.launch {
                    listState.animateScrollToItem(
                        (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    )
                }
            }
        )

        if (showHistorySheet) {
            ChatHistorySheet(
                messages = messages.toList(),
                currentMessageId = historyAnchorId,
                streamingAnswerId = streamingAnswerId,
                onMessageClick = { id ->
                    historyAnchorId = id
                    followStream = false
                    val target = messages.indexOfFirst { it.id == id }
                    if (target >= 0) {
                        scope.launch { listState.animateScrollToItem(target + 1) }
                    }
                },
                onTop = {
                    historyAnchorId = messages.firstOrNull()?.id
                    followStream = false
                    scope.launch { listState.animateScrollToItem(0) }
                },
                onBottom = {
                    historyAnchorId = messages.lastOrNull()?.id
                    followStream = true
                    scope.launch {
                        listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                    }
                },
                onDismiss = { showHistorySheet = false }
            )
        }

        if (showDialogueSettingsDialog) {
            DialogueSettingsSheet(
                initialPrompt = systemPrompt,
                initialHistoryRounds = historyRounds,
                onSave = { p, r ->
                    systemPrompt = p
                    historyRounds = r
                    saveDialogueSettings(prefs, notebookId, p, r)
                    LucentToast.show(context, S.chatSettingsSaved)
                },
                onDismiss = { showDialogueSettingsDialog = false }
            )
        }

        if (showProfileModelPickerSheet) {
            ProfileModelPickerSheet(
                profiles = savedProfiles,
                currentProfileId = resolvedSelection?.first?.id.orEmpty(),
                currentModel = resolvedSelection?.second.orEmpty(),
                onSelect = { profile, modelName ->
                    selectedProfileId = profile.id
                    selectedProfileName = profile.name
                    selectedModel = modelName
                    saveProfileSelection(prefs, notebookId, profile, modelName)
                },
                onDismiss = { showProfileModelPickerSheet = false },
                visible = active && com.lucent.app.AppNavigation.apiReturnNotebookId != notebookId,
                onManageApi = { com.lucent.app.AppNavigation.openApiFromModelPicker(notebookId) }
            )
        }

        questionToEdit?.let { target ->
            EditMessageDialog(
                title = S.chatEditQuestionTitle,
                subtitle = S.chatEditQuestionSubtitle,
                initial = target.content,
                confirmLabel = S.chatEditQuestionConfirm,
                onConfirm = { text ->
                    questionToEdit = null
                    resendEditedQuestion(target.id, text)
                },
                onDismiss = { questionToEdit = null }
            )
        }

        answerToEdit?.let { target ->
            EditMessageDialog(
                title = S.chatEditAnswerTitle,
                subtitle = S.chatEditAnswerSubtitle,
                initial = target.content,
                confirmLabel = S.userAgentSaveButton,
                onConfirm = { text ->
                    answerToEdit = null
                    saveAnswerEdit(target.id, text)
                },
                onDismiss = { answerToEdit = null }
            )
        }
    }
}
