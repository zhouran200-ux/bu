package com.lucent.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.State
import com.lucent.app.chat.ChatSessions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.ChatMessage
import com.lucent.app.data.ChatScope
import com.lucent.app.data.CitationRef
import com.lucent.app.data.Note
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.ReasoningResolver
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.ChatMessagePayload
import com.lucent.app.network.LlmClient
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class UiChatMessage(
    val id: Long,
    val role: String,
    val content: String,
    val reasoningText: String? = null,
    val answerSeconds: Int = 0,
    val reasoningTag: String? = null,
    val promptTokens: Int = 0,
    val cachedTokens: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val firstTokenMillis: Long? = null,
    val generationMillis: Long? = null,
    val cachedTokensKnown: Boolean = false,
    val sourceNoteId: Long? = null,
    val sourceRevision: String? = null
)

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
        val target = sourceNotes.firstOrNull { it.id == ref.sourceId }
        citationScope.launch {
            val index = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                target?.let { com.lucent.app.data.NovelSourceIndex.get(it) }
            }
            val reason = com.lucent.app.data.SourceCitations.rejectionReason(ref, index?.noteId, index?.revision)
            when {
                reason != null -> LucentToast.show(context, reason)
                index?.tagged?.sliceOf(ref.sliceId) == null -> LucentToast.show(context, "引用切片不存在，无法定位原文")
                else -> onOpenSource(index.noteId, ref.sliceId, index.revision)
            }
        }
    }
    val session = remember(notebookId) { ChatSessions.forNotebook(context, notebookId) }
    if (!session.ready) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(session.storageError ?: "正在读取对话…", color = LocalOnGradient.current)
        }
        return
    }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) LucentToast.show(context, "通知未开启，后台生成仍可继续；请返回对话页停止生成")
    }
    val db = remember { AppDatabase.getInstance(context) }
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
    val lastDelete = remember { mutableStateOf<PendingChatDelete?>(null) }
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
        session.write { db.chatDao().deleteByIds(listOf(pending.message.id)) }
    }

    DisposableEffect(notebookId) {
        onDispose { settlePendingDelete() }
    }

    LaunchedEffect(pendingDelete) {
        pendingDelete?.let { lastDelete.value = it }
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
            LucentToast.show(context, "该方案还没有可用模型，请到设置-API 配置获取并保存模型")
            val blankIdx = messages.indexOfFirst { it.id == aiMsgId }
            if (blankIdx >= 0) {
                messages[blankIdx] = messages[blankIdx].copy(content = "【未配置可用模型】请先在设置-API 配置里获取并保存模型。")
                session.saveMessage(messages[blankIdx])
            }
            return
        }

        val specStr = matchedProfile?.spec?.lowercase().orEmpty()
        val spec = when {
            specStr.contains("google") || specStr.contains("gemini") -> ApiSpec.GOOGLE
            specStr.contains("anthropic") || specStr.contains("claude") -> ApiSpec.ANTHROPIC
            else -> ApiSpec.OPENAI
        }

        val baseUrl = matchedProfile?.baseUrl.orEmpty()
        val apiKey = matchedProfile?.apiKey.orEmpty()
        val rawEndpoint = specStr == "custom"

        val resolvedReasoning = ReasoningResolver.resolve(
            preset = reasoningPreset,
            provider = specStr,
            model = targetModel
        )
        val effortLabel = resolvedReasoning.displayTag

        if (sourceNotes.size > 1) {
            LucentToast.show(context, "当前笔记本资料关联异常，请先整理为一份文档")
            return
        }
        val sourceNote = sourceNotes.singleOrNull()
        val customPrompt = systemPrompt

        val historyPayload = messages.takeWhile { it.id != aiMsgId }.takeLast(historyRounds * 2).map {
            ChatMessagePayload(
                role = if (it.role == "assistant") "assistant" else "user",
                content = it.content
            )
        }

        if (apiKey.isBlank()) {
            val pendingAi = UiChatMessage(
                id = aiMsgId,
                role = "assistant",
                content = "【未配置 API 密钥】\n当前所选方案未设置有效的 API Key。请点击底部右侧「模型选择」卡片中的「管理API配置」，或前往「设置 -> API 配置」填入 API Key 与基础 URL，即可开始基于本地 Markdown 知识库与大模型对话。"
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
                var knowledgeBasePrompt = ""
                session.start(aiMsgId, effortLabel, sourceBinding = {
                    val index = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        sourceNote?.let { com.lucent.app.data.NovelSourceIndex.get(it) }
                    }
                    knowledgeBasePrompt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        com.lucent.app.data.NovelPromptBuilder.system(index, customPrompt)
                    }
                    index?.noteId to index?.revision
                }) { handle, onChunk ->
                    LlmClient.chatStream(
                        baseUrl = baseUrl,
                        spec = spec,
                        apiKey = apiKey,
                        model = targetModel,
                        messages = historyPayload.mapIndexed { index, message ->
                            if (index == historyPayload.lastIndex && message.role == "user")
                                message.copy(content = com.lucent.app.data.NovelPromptBuilder.user(message.content, sourceNote != null))
                            else message
                        },
                        systemInstruction = knowledgeBasePrompt,
                        reasoningConfig = resolvedReasoning,
                        handle = handle,
                        rawEndpoint = rawEndpoint,
                        onChunk = onChunk
                    )
                }
            } catch (e: Exception) {
                val failed = UiChatMessage(aiMsgId, "assistant",
                    "【无法启动后台生成】${e.message ?: "请重试"}", timestamp = answerStamp)
                upsertAnswerSlot(failed)
                session.saveMessage(failed)
                LucentToast.show(context, "无法启动后台生成，请保持应用在前台后重试")
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
        val convId = ChatScope.forNotebook(notebookId)
        session.write {
            db.chatDao().insert(
                ChatMessage(
                    id = userMsg.id,
                    role = "user",
                    content = promptText,
                    timestamp = userMsg.timestamp,
                    conversationId = convId
                )
            )
        }
        val answerId = userMsg.id + 1
        messages.add(UiChatMessage(id = answerId, role = "assistant", content = "", timestamp = answerId))
        followStream = true
        scope.launch {
            delay(100)
            if (followStream && !listState.isScrollInProgress) {
                listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
            }
        }
        askModel(answerId)
    }

    fun regenerateAnswer(answerId: Long) {
        if (!session.ready) return
        if (streamingAnswerId != null) {
            LucentToast.show(context, "上一条回答还没有结束")
            return
        }
        val idx = messages.indexOfFirst { it.id == answerId }
        if (idx < 0) return
        val question = messages.take(idx).lastOrNull { it.role == "user" }?.content.orEmpty()
        if (question.isBlank()) {
            LucentToast.show(context, "找不到这条回答对应的问题，无法重新生成")
            return
        }
        val selection = ApiProfiles.resolveSelection(savedProfiles, selectedProfileId, selectedProfileName, selectedModel)
        if (selection == null || selection.second.isBlank() || selection.first.apiKey.isBlank()) {
            LucentToast.show(context, "当前方案未配置有效模型或密钥，已保留原回答")
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
        session.write { db.chatDao().deleteByIds(listOf(answerId)) }
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
        session.write {
            db.chatDao().updateContent(questionId, newText)
            if (followingIds.isNotEmpty()) db.chatDao().deleteByIds(followingIds)
        }
        val answerId = System.currentTimeMillis() + 1
        messages.add(UiChatMessage(id = answerId, role = "assistant", content = "", timestamp = answerId))
        askModel(answerId)
    }

    fun saveAnswerEdit(answerId: Long, newText: String) {
        if (!session.ready || session.streamingAnswerId != null) return
        val idx = messages.indexOfFirst { it.id == answerId }
        if (idx < 0) return
        messages[idx] = messages[idx].copy(content = newText)
        session.write { db.chatDao().updateContent(answerId, newText) }
    }

    fun deleteMessage(target: UiChatMessage) {
        if (!session.ready || session.streamingAnswerId != null) return
        val idx = messages.indexOfFirst { it.id == target.id }
        if (idx < 0) return
        if (pendingDelete != null) LucentToast.show(context, "上一条删除已生效")
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
    var modelPickerButtonWidthPx by remember { mutableIntStateOf(0) }
    val drawerHandleWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
        (modelPickerButtonWidthPx * 1.25f).toDp()
    }
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
        androidx.compose.runtime.snapshotFlow { streamProgress }
            .collectLatest { progress ->
                if (progress <= 0 || !followStream || listState.isScrollInProgress) return@collectLatest
                delay(80)
                if (followStream && !listState.isScrollInProgress) {
                    listState.requestScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                }
            }
    }

    val onSendMessage: () -> Unit = send@{
        val text = inputText.trim()
        if (text.isEmpty()) return@send
        if (!session.ready) {
            LucentToast.show(context, session.storageError ?: "正在读取对话，请稍候")
            return@send
        }
        if (streamingAnswerId != null) {
            LucentToast.show(context, "正在回答，点右下角的方块可以停止")
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
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "header") { Spacer(modifier = Modifier.height(8.dp)) }

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
                    prefs.edit()
                        .putString("reasoning_preset_$notebookId", it.key)
                        .putString("reasoning_$notebookId", it.key)
                        .apply()
                },
                onOpenHistory = { openHistorySheet() },
                onOpenDialogueSettings = { showDialogueSettingsDialog = true },
                selectedProfileName = resolvedSelection?.first?.name.orEmpty(),
                onOpenModelPicker = { showProfileModelPickerSheet = true },
                onModelPickerWidth = { modelPickerButtonWidthPx = it },
                streaming = streamingAnswerId != null,
                onSend = onSendMessage,
                onStop = { stopStreaming() }
            )
        }

        AnimatedVisibility(
            visible = pendingDelete != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .then(
                    if (!isImeVisible) Modifier.navigationBarsPadding()
                    else Modifier
                )
                .padding(bottom = 112.dp),
            enter = if (barMotionOn) slideInVertically(
                animationSpec = LucentMotion.toIntOffset(LucentMotion.Sheet)
            ) { it } + fadeIn(tween(180)) else fadeIn(tween(0)),
            exit = if (barMotionOn) slideOutVertically(
                animationSpec = LucentMotion.toIntOffset(LucentMotion.Firm)
            ) { it } + fadeOut(tween(150)) else fadeOut(tween(0))
        ) {
            lastDelete.value?.let { pending ->
                DeleteUndoBar(
                    messageId = pending.message.id,
                    label = if (pending.message.role == "user") "已删除提问" else "已删除回答",
                    onUndo = { undoDelete() },
                    onDismiss = { settlePendingDelete() }
                )
            }
        }

        AnimatedVisibility(
            visible = !atBottom && !isImeVisible && messages.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 118.dp),
            enter = if (barMotionOn) fadeIn(tween(160)) + slideInVertically(tween(160)) { it } else fadeIn(tween(0)),
            exit = if (barMotionOn) fadeOut(tween(120)) + slideOutVertically(tween(120)) { it } else fadeOut(tween(0))
        ) {
            Surface(
                shape = CircleShape,
                color = glassPanelColor(),
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable {
                        followStream = true
                        scope.launch {
                            listState.animateScrollToItem(
                                (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            )
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ArrowDownward,
                        contentDescription = "回到底部",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (showHistorySheet) {
            ChatHistorySheet(
                messages = messages.toList(),
                handleWidth = drawerHandleWidth,
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
                    prefs.edit()
                        .putString("prompt_$notebookId", p)
                        .putInt("history_rounds_$notebookId", r)
                        .remove("temperature_$notebookId")
                        .apply()
                    LucentToast.show(context, "对话设置已保存")
                },
                onDismiss = { showDialogueSettingsDialog = false }
            )
        }

        if (showProfileModelPickerSheet) {
            ProfileModelPickerSheet(
                profiles = savedProfiles,
                handleWidth = drawerHandleWidth,
                currentProfileId = resolvedSelection?.first?.id.orEmpty(),
                currentModel = resolvedSelection?.second.orEmpty(),
                onSelect = { profile, modelName ->
                    selectedProfileId = profile.id
                    selectedProfileName = profile.name
                    selectedModel = modelName
                    prefs.edit()
                        .putString("profile_id_$notebookId", profile.id)
                        .putString("profile_name_$notebookId", profile.name)
                        .putString("model_$notebookId", modelName)
                        .apply()
                },
                onDismiss = { showProfileModelPickerSheet = false },
                visible = active && com.lucent.app.AppNavigation.apiReturnNotebookId != notebookId,
                onManageApi = { com.lucent.app.AppNavigation.openApiFromModelPicker(notebookId) }
            )
        }

        questionToEdit?.let { target ->
            EditMessageDialog(
                title = "编辑提问",
                subtitle = "保存后会删掉这条之后的对话，并用新文字重新提问。",
                initial = target.content,
                confirmLabel = "重新提问",
                onConfirm = { text ->
                    questionToEdit = null
                    resendEditedQuestion(target.id, text)
                },
                onDismiss = { questionToEdit = null }
            )
        }

        answerToEdit?.let { target ->
            EditMessageDialog(
                title = "编辑回答",
                subtitle = "只改写这条回答的文字，不会重新提问。",
                initial = target.content,
                confirmLabel = "保存",
                onConfirm = { text ->
                    answerToEdit = null
                    saveAnswerEdit(target.id, text)
                },
                onDismiss = { answerToEdit = null }
            )
        }
    }
}


private data class PendingChatDelete(
    val message: UiChatMessage,
    val index: Int,
    val viewport: ReadingPosition
)

private const val SWIPE_FRACTION = 0.34f
private const val FLING_VELOCITY = 1200f

@Composable
private fun DeleteUndoBar(
    messageId: Long,
    label: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(0f) }
    var barWidth by remember { mutableStateOf(0f) }
    val motionOn = LocalBackgroundEnvironment.current.motionEnabled

    LaunchedEffect(messageId) {
        slide.snapTo(0f)
        delay(10_000)
        onDismiss()
    }

    Row(
        modifier = Modifier
            .wrapContentWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .onSizeChanged { barWidth = it.width.toFloat() }
            .graphicsLayer {
                translationX = slide.value
                alpha = (1f - abs(slide.value) / barWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch { slide.snapTo(slide.value + delta) }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val travel = barWidth.coerceAtLeast(1f)
                    val distance = abs(slide.value)
                    val kept = distance < travel * SWIPE_FRACTION && abs(velocity) < FLING_VELOCITY
                    val direction = if (distance > 1f) slide.value else velocity
                    scope.launch {
                        if (kept) {
                            if (motionOn) slide.animateTo(0f, LucentMotion.toSpring(LucentMotion.Settle)) else slide.snapTo(0f)
                        } else {
                            val target = if (direction >= 0f) travel else -travel
                            if (motionOn) {
                                slide.animateTo(target, LucentMotion.toSpring(LucentMotion.Firm))
                            } else {
                                slide.snapTo(target)
                            }
                            onDismiss()
                        }
                    }
                }
            )
            .frostedGlass(cornerRadius = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = LocalOnGradient.current,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 14.dp)
        )
        TextButton(
            onClick = {
                Haptics.tick(context)
                onUndo()
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text(text = "撤销", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EditMessageDialog(
    title: String,
    subtitle: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var text by remember { mutableStateOf(initial) }
    val keepEditing = rememberImeVisibleOnOpen()
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    AlertDialog(
        shape = RoundedCornerShape(24.dp),
        containerColor = glassPanelColor(),
        tonalElevation = 0.dp,
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = LucentTitles.SectionSize, fontWeight = LucentTitles.SectionWeight) },
        text = {
            PreserveEditingKeyboardOnOpen(keepEditing, inputFocus)
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(subtitle, color = onGradientMuted, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(onGradient.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(fontSize = 14.sp, color = onGradient, lineHeight = 21.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp, max = 220.dp)
                            .focusRequester(inputFocus),
                        decorationBox = { inner ->
                            if (text.isEmpty()) {
                                Text("在这里输入…", color = onGradientMuted.copy(alpha = 0.6f), fontSize = 14.sp)
                            }
                            inner()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

internal fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Copied Text", text)
    clipboard.setPrimaryClip(clip)
    com.lucent.app.ui.LucentToast.show(context, "已复制到剪贴板")
}
