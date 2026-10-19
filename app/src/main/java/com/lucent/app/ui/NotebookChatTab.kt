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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import com.lucent.app.ui.chat.ChatActionsCoordinator
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
    val prefs = remember(notebookId) {
        context.getSharedPreferences("lucent_notebook_chat_prefs", Context.MODE_PRIVATE)
    }
    val messages = session.messages
    val readingKeys by remember(messages) {
        derivedStateOf(structuralEqualityPolicy()) {
            listOf("header") + messages.map { "message:${it.id}" } + "footer"
        }
    }
    val listState = rememberReadingListState("chat:$notebookId", readingKeys)
    val atBottom by remember {
        derivedStateOf {
            !listState.canScrollForward
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val actions = remember(session, notebookId) {
        ChatActionsCoordinator(
            context = context,
            session = session,
            scope = scope,
            listState = listState,
            sourceNotes = sourceNotes,
            prefs = prefs,
            notebookId = notebookId,
            notificationPermission = notificationPermission,
            focusManager = focusManager,
            keyboardController = keyboardController
        )
    }
    val viewportAnchor = remember(notebookId) { ChatViewportAnchor() }
    LaunchedEffect(active) {
        if (!active) actions.followStream = false
    }
    val readingScrollConnection = remember(listState) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y != 0f) {
                    actions.followStream = false
                    viewportAnchor.userScrolled()
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                actions.followStream = ChatScrollMath.shouldFollowAfterScroll(
                    wasFollowing = actions.followStream,
                    atBottom = !listState.canScrollForward,
                    userScrolling = true
                )
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    var showHistorySheet by remember { mutableStateOf(false) }
    var historyAnchorId by remember { mutableStateOf<Long?>(null) }

    val settingsRepo = remember { SettingsRepository(context) }
    val profilesJson by settingsRepo.apiProfilesJson.collectAsState(initial = SettingsCache.apiProfilesJson)
    actions.profilesJson = profilesJson
    val savedProfiles = remember(actions.profilesJson) { actions.savedProfiles }
    val resolvedSelection = ApiProfiles.resolveSelection(savedProfiles, actions.selectedProfileId, actions.selectedProfileName, actions.selectedModel)
    LaunchedEffect(notebookId, resolvedSelection) {
        val (profile, model) = resolvedSelection ?: return@LaunchedEffect
        if (profile.id != actions.selectedProfileId || profile.name != actions.selectedProfileName || model != actions.selectedModel) {
            actions.selectedProfileId = profile.id
            actions.selectedProfileName = profile.name
            actions.selectedModel = model
            prefs.edit()
                .putString("profile_id_$notebookId", profile.id)
                .putString("profile_name_$notebookId", profile.name)
                .putString("model_$notebookId", model)
                .apply()
        }
    }
    var showDialogueSettingsDialog by remember { mutableStateOf(false) }
    var showProfileModelPickerSheet by remember { mutableStateOf(false) }
    val streamingAnswerId = session.streamingAnswerId
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
    DisposableEffect(notebookId) {
        onDispose { actions.settlePendingDelete() }
    }

    val isImeVisible = WindowInsets.isImeVisible
    val barMotionOn = LocalBackgroundEnvironment.current.motionEnabled
    var showReasoningMenu by remember(notebookId) { mutableStateOf(false) }
    var reasoningButtonBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    var chatCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    LaunchedEffect(active) { if (!active) showReasoningMenu = false }
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
            canFollow = { actions.followStream && !listState.isScrollInProgress && !actions.streamReserveBlocksFollow() },
            awaitLayout = {
                delay(80)
                androidx.compose.runtime.withFrameNanos { }
            },
            onFollow = {
                listState.requestScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            }
        )
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
            CompositionLocalProvider(LocalLiveGlassEnabled provides false) {
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
                        if (session.pendingWriteCount > 0) {
                            TextButton(onClick = { session.retryPendingWrites() }) {
                                Text(S.chatPendingWritesRetry)
                            }
                        }
                    }
                }

                itemsIndexed(messages, key = { _, msg -> "message:${msg.id}" }) { _, msg ->
                    val actionsEnabled = session.ready && streamingAnswerId == null && session.pendingWriteCount == 0 && !session.hasPendingAction
                    val itemModifier = if (msg.id == streamingAnswerId) {
                        Modifier
                    } else {
                        Modifier.animateItem(
                            fadeInSpec = tween(200),
                            placementSpec = if (streamingAnswerId == null) tween(180) else null,
                            fadeOutSpec = tween(140)
                        )
                    }
                    if (msg.role == "user") {
                        UserMessageCard(
                            modifier = itemModifier,
                            message = msg,
                            actionsEnabled = actionsEnabled,
                            onEdit = { questionToEdit = msg },
                            onDelete = { actions.deleteMessage(msg) }
                        )
                    } else {
                        AiMessageCard(
                            modifier = itemModifier,
                            message = msg,
                            readingKey = "chat:$notebookId:thought:${msg.id}",
                            streaming = msg.id == streamingAnswerId,
                            liveThought = if (msg.id == streamingAnswerId) streamingThought else null,
                            actionsEnabled = actionsEnabled,
                            minHeightReservePx = actions.regenerateReserve
                                ?.takeIf { it.first == msg.id && msg.id == streamingAnswerId }?.second,
                            onRegenerate = { actions.regenerateAnswer(msg.id) },
                            onEditAnswer = { answerToEdit = msg },
                            onDelete = { actions.deleteMessage(msg) },
                            onCitationClick = openCitation
                        )
                    }
                }

                item(key = "footer") { Spacer(modifier = Modifier.height(8.dp)) }
            }
            }
            FloatingScrollCapsule(
                listState = listState,
                itemKeys = readingKeys,
                enabled = active && !isImeVisible,
                onDragStart = {
                    actions.followStream = false
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
                inputText = actions.inputText,
                onInputChange = { actions.inputText = it },
                showReasoningMenu = showReasoningMenu,
                onReasoningMenuChange = { showReasoningMenu = it },
                onReasoningButtonBounds = { reasoningButtonBounds = it },
                reasoningPreset = actions.reasoningPreset,
                onSelectReasoning = {
                    actions.reasoningPreset = it
                    saveReasoningSelection(prefs, notebookId, it)
                },
                onOpenHistory = { openHistorySheet() },
                onOpenDialogueSettings = { showDialogueSettingsDialog = true },
                selectedProfileName = resolvedSelection?.first?.name.orEmpty(),
                onOpenModelPicker = { showProfileModelPickerSheet = true },
                streaming = streamingAnswerId != null,
                onSend = actions.onSendMessage,
                onStop = { actions.stopStreaming() }
            )
        }

        ChatDeleteUndoLayer(
            pending = actions.pendingDelete,
            motionOn = barMotionOn,
            imeVisible = isImeVisible,
            onUndo = { actions.undoDelete() },
            onDismiss = { actions.settlePendingDelete() }
        )

        ChatScrollToBottomButton(
            visible = !atBottom && !isImeVisible && messages.isNotEmpty(),
            motionOn = barMotionOn,
            onClick = {
                actions.followStream = true
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
                    actions.followStream = false
                    val target = messages.indexOfFirst { it.id == id }
                    if (target >= 0) {
                        scope.launch { listState.animateScrollToItem(target + 1) }
                    }
                },
                onTop = {
                    historyAnchorId = messages.firstOrNull()?.id
                    actions.followStream = false
                    scope.launch { listState.animateScrollToItem(0) }
                },
                onBottom = {
                    historyAnchorId = messages.lastOrNull()?.id
                    actions.followStream = true
                    scope.launch {
                        listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                    }
                },
                onDismiss = { showHistorySheet = false }
            )
        }

        if (showDialogueSettingsDialog) {
            DialogueSettingsSheet(
                initialPrompt = actions.systemPrompt,
                initialHistoryRounds = actions.historyRounds,
                onSave = { p, r ->
                    actions.systemPrompt = p
                    actions.historyRounds = r
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
                    actions.selectedProfileId = profile.id
                    actions.selectedProfileName = profile.name
                    actions.selectedModel = modelName
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
                    actions.resendEditedQuestion(target.id, text)
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
                    actions.saveAnswerEdit(target.id, text)
                },
                onDismiss = { answerToEdit = null }
            )
        }
    }
}
