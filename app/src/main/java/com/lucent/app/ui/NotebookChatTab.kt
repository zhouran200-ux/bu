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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.AppScope
import com.lucent.app.Screen
import com.lucent.app.SettingsNav
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.ChatMessage
import com.lucent.app.data.ChatScope
import com.lucent.app.data.Note
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.ReasoningResolver
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.ChatMessagePayload
import com.lucent.app.network.LlmClient
import com.lucent.app.network.StreamCallHandle
import com.lucent.app.ui.SettingsRoute
import kotlin.math.abs
import kotlinx.coroutines.delay
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
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NotebookChatTab(
    notebookId: Long,
    sourceNotes: List<Note>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    var followStream by remember { mutableStateOf(true) }
    LaunchedEffect(atBottom) {
        if (atBottom && listState.isScrollInProgress) followStream = true
        else if (!atBottom && listState.isScrollInProgress) followStream = false
    }

    var railExpanded by remember { mutableStateOf(false) }
    var railDragging by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var historyAnchorId by remember { mutableStateOf<Long?>(null) }
    val railVisible by remember {
        derivedStateOf { listState.isScrollInProgress || railExpanded || railDragging }
    }
    LaunchedEffect(railVisible, railExpanded) {
        if (railVisible && railExpanded) {
            delay(2600)
            if (!railDragging) railExpanded = false
        }
    }

    val prefs = remember(notebookId) {
        context.getSharedPreferences("lucent_notebook_chat_prefs", Context.MODE_PRIVATE)
    }

    var inputText by remember { mutableStateOf("") }
    val settingsRepo = remember { SettingsRepository(context) }
    val profilesJson by settingsRepo.apiProfilesJson.collectAsState(initial = SettingsCache.apiProfilesJson)
    val savedProfiles = remember(profilesJson) {
        ApiProfiles.parse(profilesJson).ifEmpty { ApiProfiles.DEFAULT_PROFILES }
    }
    var selectedProfileName by remember {
        mutableStateOf(prefs.getString("profile_name_$notebookId", "") ?: "")
    }
    var selectedModel by remember {
        mutableStateOf(prefs.getString("model_$notebookId", "") ?: "")
    }

    LaunchedEffect(notebookId, savedProfiles) {
        val (name, model) = ApiProfiles.selectionFor(savedProfiles, selectedProfileName, selectedModel)
        if (name.isEmpty()) return@LaunchedEffect
        if (name != selectedProfileName || model != selectedModel) {
            selectedProfileName = name
            selectedModel = model
            prefs.edit()
                .putString("profile_name_$notebookId", name)
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

    val messages = remember { mutableStateListOf<UiChatMessage>() }
    val placementSpec = rememberReorderPlacementSpec()
    var streamingAnswerId by remember { mutableStateOf<Long?>(null) }
    val streamHandle = remember { StreamCallHandle() }
    var stoppedByUser by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PendingChatDelete?>(null) }
    var questionToEdit by remember { mutableStateOf<UiChatMessage?>(null) }
    var answerToEdit by remember { mutableStateOf<UiChatMessage?>(null) }
    val streamingThought = remember { mutableStateOf("") }
    val lastDelete = remember { mutableStateOf<PendingChatDelete?>(null) }

    fun openHistorySheet() {
        historyAnchorId = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index in 1..messages.size }
            ?.let { messages.getOrNull(it.index - 1)?.id }
        showHistorySheet = true
    }

    fun settlePendingDelete() {
        val pending = pendingDelete ?: return
        pendingDelete = null
        AppScope.io.launch { db.chatDao().deleteByIds(listOf(pending.message.id)) }
    }

    DisposableEffect(notebookId) {
        onDispose { settlePendingDelete() }
    }

    LaunchedEffect(pendingDelete) {
        pendingDelete?.let { lastDelete.value = it }
    }

    LaunchedEffect(notebookId) {
        settlePendingDelete()
        val convId = ChatScope.forNotebook(notebookId)
        val existing = db.chatDao().getForConversationOnce(convId)
        messages.clear()
        if (existing.isNotEmpty()) {
            messages.addAll(existing.map { m ->
                UiChatMessage(
                    id = m.id,
                    role = m.role,
                    content = m.content,
                    reasoningText = m.reasoningText,
                    answerSeconds = m.answerSeconds,
                    promptTokens = m.tokens,
                    cachedTokens = m.cachedTokens,
                    timestamp = m.timestamp
                )
            })
        }
    }

    fun askModel(answerId: Long) {
        val matchedProfile = savedProfiles.find { it.name == selectedProfileName }
            ?: savedProfiles.firstOrNull()
        val targetModel = selectedModel.ifBlank { matchedProfile?.model.orEmpty() }
        val convId = ChatScope.forNotebook(notebookId)
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
            }
            streamingAnswerId = null
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

        val knowledgeBasePrompt = buildString {
            append("你是一个专业的笔记本知识库与对话助手。用户在此笔记本中上传了参考资料文档（已在本地转换为 Markdown 格式）。\n")
            append("请严格基于以下参考资料的内容回答用户的问题。如果问题在参考资料中没有提及，请如实告知并结合通用常识客观回答。\n\n")
            if (sourceNotes.isNotEmpty()) {
                append("==================== 参考资料库开始 ====================\n")
                sourceNotes.forEachIndexed { index, note ->
                    val docTitle = note.title.ifBlank { "资料文档 ${index + 1}" }
                    append("\n【文档 ${index + 1}：《$docTitle》】\n")
                    append(note.body)
                    append("\n")
                }
                append("\n==================== 参考资料库结束 ====================\n\n")
            } else {
                append("（当前笔记本暂无关联参考资料，请按通用笔记助手角色回答）\n\n")
            }

            if (systemPrompt.isNotBlank()) {
                append("【自定义提示词与设定】\n")
                append(systemPrompt.trim())
                append("\n\n")
            }

            when (reasoningPreset) {
                ReasoningPreset.FAST -> append("【思考规范】请进行简明精炼的快速推理后再作答。\n")
                ReasoningPreset.BALANCED -> append("【思考规范】请进行条理清晰、层次分明的逻辑分析后再给出结论。\n")
                ReasoningPreset.DEEP -> append("【思考规范】请进行深度、细致、详尽的逻辑推理，充分论证每一个要点后再给出最终结论。\n")
                ReasoningPreset.MAXIMUM -> append("【思考规范】请调用最高复杂度逻辑推演与多角度攻坚，严格反思校验每一个假设后再给出结论。\n")
                ReasoningPreset.AUTO -> {}
            }
        }

        val historyPayload = messages.filter { it.id != aiMsgId }.takeLast(historyRounds * 2).map {
            ChatMessagePayload(
                role = if (it.role == "assistant") "assistant" else "user",
                content = it.content
            )
        }

        if (apiKey.isBlank()) {
            val pendingAi = UiChatMessage(
                id = aiMsgId,
                role = "assistant",
                content = "【未配置 API 密钥】\n当前所选方案未设置有效的 API Key。请点击底部右侧「模型选择」卡片中的「管理与添加 API 方案」，或前往「设置 -> API 配置」填入 API Key 与基础 URL，即可开始基于本地 Markdown 知识库与大模型对话。"
            )
            upsertAnswerSlot(pendingAi)
        } else {
            val pendingAi = UiChatMessage(
                id = aiMsgId,
                role = "assistant",
                content = "",
                reasoningTag = effortLabel
            )
            upsertAnswerSlot(pendingAi)
            streamingThought.value = ""
            streamingAnswerId = aiMsgId
            stoppedByUser = false

            scope.launch {
                val startTime = System.currentTimeMillis()
                val scheduler = StreamUiScheduler(
                    timeWindowMs = 35L,
                    charBurstThreshold = 48,
                    thoughtWindowMs = 16L,
                    thoughtBurstThreshold = 24,
                    onFlush = { currentContent, _ ->
                        val idx = messages.indexOfFirst { it.id == aiMsgId }
                        if (idx >= 0) {
                            messages[idx] = messages[idx].copy(content = currentContent)
                        }
                    },
                    onThought = { reasoning -> streamingThought.value = reasoning }
                )

                val chatResult = LlmClient.chatStream(
                    baseUrl = baseUrl,
                    spec = spec,
                    apiKey = apiKey,
                    model = targetModel,
                    messages = historyPayload,
                    systemInstruction = knowledgeBasePrompt,
                    reasoningConfig = resolvedReasoning,
                    handle = streamHandle,
                    rawEndpoint = rawEndpoint
                ) { deltaText, deltaThought ->
                    scheduler.append(deltaText, deltaThought)
                }

                scheduler.forceFlush()

                val durationSec = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1)

                chatResult.onSuccess { res ->
                    val finalContent = res.content.ifBlank { scheduler.getFinalContent() }.ifBlank { "（模型返回了空回复）" }
                    val finalReasoning = res.reasoningText ?: scheduler.getFinalReasoning()

                    val finalAi = UiChatMessage(
                        id = aiMsgId,
                        role = "assistant",
                        content = finalContent,
                        reasoningText = finalReasoning,
                        answerSeconds = durationSec,
                        reasoningTag = effortLabel,
                        promptTokens = res.promptTokens,
                        cachedTokens = res.cachedTokens,
                        timestamp = answerStamp
                    )
                    val idx = messages.indexOfFirst { it.id == aiMsgId }
                    if (idx >= 0) {
                        messages[idx] = finalAi
                    }

                    AppScope.io.launch {
                        db.chatDao().insert(
                            ChatMessage(
                                id = aiMsgId,
                                role = "assistant",
                                content = finalAi.content,
                                reasoningText = finalAi.reasoningText,
                                conversationId = convId,
                                tokens = finalAi.promptTokens,
                                cachedTokens = finalAi.cachedTokens,
                                answerSeconds = finalAi.answerSeconds
                            )
                        )
                    }
                }.onFailure { err ->
                    val currentText = scheduler.getFinalContent()
                    val errorDisplay = when {
                        stoppedByUser -> currentText.ifBlank { "（已停止生成）" }
                        currentText.isNotBlank() ->
                            "$currentText\n\n[网络中断：${err.message ?: "连接已关闭"}，已保留已生成内容]"
                        else -> "【请求失败】${err.message ?: "网络或接口异常"}\n\n请检查网络连接、API 密钥与模型端点配置。"
                    }

                    val failedAi = UiChatMessage(
                        id = aiMsgId,
                        role = "assistant",
                        content = errorDisplay,
                        reasoningText = scheduler.getFinalReasoning(),
                        answerSeconds = durationSec,
                        reasoningTag = effortLabel
                    )
                    val idx = messages.indexOfFirst { it.id == aiMsgId }
                    if (idx >= 0) {
                        messages[idx] = failedAi
                    }

                    if (currentText.isNotBlank()) {
                        AppScope.io.launch {
                            db.chatDao().insert(
                                ChatMessage(
                                    id = aiMsgId,
                                    role = "assistant",
                                    content = failedAi.content,
                                    reasoningText = failedAi.reasoningText,
                                    conversationId = convId,
                                    answerSeconds = failedAi.answerSeconds
                                )
                            )
                        }
                    }
                }

                streamingAnswerId = null
                delay(100)
                if (atBottom) {
                    listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
                }
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
        AppScope.io.launch {
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
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
        askModel(answerId)
    }

    fun regenerateAnswer(answerId: Long) {
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
        messages[idx] = messages[idx].copy(
            content = "",
            reasoningText = null,
            answerSeconds = 0,
            promptTokens = 0,
            cachedTokens = 0
        )
        settlePendingDelete()
        AppScope.io.launch { db.chatDao().deleteByIds(listOf(answerId)) }
        askModel(answerId)
    }

    fun resendEditedQuestion(questionId: Long, newText: String) {
        val idx = messages.indexOfFirst { it.id == questionId }
        if (idx < 0) return
        settlePendingDelete()
        val followingIds = messages.drop(idx + 1).map { it.id }
        while (messages.size > idx + 1) messages.removeAt(messages.size - 1)
        messages[idx] = messages[idx].copy(content = newText)
        AppScope.io.launch {
            db.chatDao().updateContent(questionId, newText)
            if (followingIds.isNotEmpty()) db.chatDao().deleteByIds(followingIds)
        }
        val answerId = System.currentTimeMillis() + 1
        messages.add(UiChatMessage(id = answerId, role = "assistant", content = "", timestamp = answerId))
        askModel(answerId)
    }

    fun saveAnswerEdit(answerId: Long, newText: String) {
        val idx = messages.indexOfFirst { it.id == answerId }
        if (idx < 0) return
        messages[idx] = messages[idx].copy(content = newText)
        AppScope.io.launch { db.chatDao().updateContent(answerId, newText) }
    }

    fun deleteMessage(target: UiChatMessage) {
        val idx = messages.indexOfFirst { it.id == target.id }
        if (idx < 0) return
        if (pendingDelete != null) LucentToast.show(context, "上一条删除已生效")
        settlePendingDelete()
        messages.removeAt(idx)
        pendingDelete = PendingChatDelete(message = target, index = idx)
    }

    fun undoDelete() {
        val pending = pendingDelete ?: return
        pendingDelete = null
        if (messages.none { it.id == pending.message.id }) {
            val at = pending.index.coerceIn(0, messages.size)
            messages.add(at, pending.message)
            scope.launch {
                val targetItem = at + 1
                val info = listState.layoutInfo
                val visible = info.visibleItemsInfo
                val firstFull = visible.firstOrNull { it.offset >= info.viewportStartOffset }?.index
                    ?: visible.firstOrNull()?.index
                val lastFull = visible.lastOrNull { it.offset + it.size <= info.viewportEndOffset }?.index
                    ?: visible.lastOrNull()?.index
                val onScreen = firstFull != null && lastFull != null &&
                    targetItem in firstFull..lastFull
                if (!onScreen) {
                    listState.animateScrollToItem(targetItem.coerceAtLeast(0))
                }
            }
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible
    val barMotionOn = LocalBackgroundEnvironment.current.motionEnabled

    fun stopStreaming() {
        if (streamingAnswerId == null) return
        stoppedByUser = true
        Haptics.tick(context)
        streamHandle.cancel()
    }

    val streamProgress by remember {
        derivedStateOf {
            val id = streamingAnswerId ?: return@derivedStateOf 0
            val body = messages.firstOrNull { it.id == id }?.content?.length ?: 0
            body + streamingThought.value.length
        }
    }
    LaunchedEffect(streamProgress) {
        if (streamingAnswerId != null && followStream && streamProgress > 0) {
            listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }

    val onSendMessage: () -> Unit = send@{
        val text = inputText.trim()
        if (text.isEmpty()) return@send
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

    LaunchedEffect(isImeVisible) {
        if (isImeVisible && messages.isNotEmpty()) {
            delay(50)
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
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
                item { Spacer(modifier = Modifier.height(8.dp)) }

                itemsIndexed(messages, key = { _, msg -> msg.id }) { _, msg ->
                    val actionsEnabled = streamingAnswerId == null
                    val itemModifier = Modifier.animateItem(
                        fadeInSpec = tween(200),
                        placementSpec = placementSpec,
                        fadeOutSpec = tween(140)
                    )
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
                            streaming = msg.id == streamingAnswerId,
                            liveThought = if (msg.id == streamingAnswerId) streamingThought else null,
                            actionsEnabled = actionsEnabled,
                            onRegenerate = { regenerateAnswer(msg.id) },
                            onEditAnswer = { answerToEdit = msg },
                            onDelete = { deleteMessage(msg) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
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
                reasoningPreset = reasoningPreset,
                onSelectReasoning = {
                    reasoningPreset = it
                    prefs.edit()
                        .putString("reasoning_preset_$notebookId", it.key)
                        .putString("reasoning_$notebookId", it.key)
                        .apply()
                },
                onOpenDialogueSettings = { showDialogueSettingsDialog = true },
                selectedProfileName = selectedProfileName,
                selectedModel = selectedModel,
                onOpenModelPicker = { showProfileModelPickerSheet = true },
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
                color = LocalOnGradient.current.copy(alpha = 0.10f),
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
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "回到底部",
                        tint = LocalOnGradient.current,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        ChatScrollRail(
            listState = listState,
            visible = railVisible && !isImeVisible,
            expanded = railExpanded,
            dragging = railDragging,
            onRequestExpand = {
                Haptics.tick(context)
                railExpanded = true
            },
            onOpenHistory = {
                Haptics.tick(context)
                openHistorySheet()
            },
            onDragStart = {
                railExpanded = true
                railDragging = true
                followStream = false
            },
            onDragFraction = { fraction ->
                scope.launch { scrollChatToFraction(listState, fraction) }
            },
            onDragEnd = { railDragging = false },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .navigationBarsPadding()
                .padding(end = 6.dp)
        )

        if (showHistorySheet) {
            ChatHistorySheet(
                messages = messages.toList(),
                currentMessageId = historyAnchorId,
                streamingAnswerId = streamingAnswerId,
                onMessageClick = { id ->
                    showHistorySheet = false
                    followStream = false
                    val target = messages.indexOfFirst { it.id == id }
                    if (target >= 0) {
                        scope.launch { listState.animateScrollToItem(target + 1) }
                    }
                },
                onTop = {
                    showHistorySheet = false
                    followStream = false
                    scope.launch { listState.animateScrollToItem(0) }
                },
                onBottom = {
                    showHistorySheet = false
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
                    showDialogueSettingsDialog = false
                    LucentToast.show(context, "对话设置已保存")
                },
                onDismiss = { showDialogueSettingsDialog = false }
            )
        }

        if (showProfileModelPickerSheet) {
            ProfileModelPickerSheet(
                profiles = savedProfiles,
                currentProfileName = selectedProfileName,
                currentModel = selectedModel,
                onSelect = { profileName, modelName ->
                    selectedProfileName = profileName
                    selectedModel = modelName
                    prefs.edit()
                        .putString("profile_name_$notebookId", profileName)
                        .putString("model_$notebookId", modelName)
                        .apply()
                    showProfileModelPickerSheet = false
                },
                onDismiss = { showProfileModelPickerSheet = false }
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

@Composable
private fun UserMessageCard(
    modifier: Modifier = Modifier,
    message: UiChatMessage,
    actionsEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val textColor = LocalOnGradient.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            modifier = Modifier
                .frostedGlass(cornerRadius = 20.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.content,
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BubbleAction(
                icon = Icons.Rounded.ContentCopy,
                label = "复制全文",
                tint = textColor,
                onClick = { copyTextToClipboard(context, message.content) }
            )
            BubbleAction(
                icon = Icons.Rounded.Edit,
                label = "编辑",
                tint = textColor,
                enabled = actionsEnabled,
                onClick = onEdit
            )
            BubbleAction(
                icon = Icons.Rounded.Delete,
                label = "删除",
                tint = textColor,
                enabled = actionsEnabled,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun AiMessageCard(
    modifier: Modifier = Modifier,
    message: UiChatMessage,
    streaming: Boolean = false,
    liveThought: State<String>? = null,
    actionsEnabled: Boolean = true,
    onRegenerate: () -> Unit = {},
    onEditAnswer: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 12.dp)
    ) {
        if (streaming || liveThought != null || !message.reasoningText.isNullOrBlank()) {
            ThinkingLane(
                messageId = message.id,
                liveThought = liveThought,
                storedThought = message.reasoningText.orEmpty(),
                tag = message.reasoningTag,
                streaming = streaming,
                answerSeconds = message.answerSeconds
            )
        }

        if (message.content.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(cornerRadius = 18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MarkdownText(
                        text = message.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!streaming) {
                val stats = ChatStats.summary(
                    seconds = message.answerSeconds,
                    cachedTokens = message.cachedTokens,
                    promptTokens = message.promptTokens,
                    chars = message.content.length
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BubbleAction(
                        icon = Icons.Rounded.ContentCopy,
                        label = "复制",
                        tint = LocalOnGradient.current,
                        onClick = { copyTextToClipboard(context, message.content) }
                    )
                    BubbleAction(
                        icon = Icons.Rounded.Refresh,
                        label = "重新生成",
                        tint = LocalOnGradient.current,
                        enabled = actionsEnabled,
                        onClick = onRegenerate
                    )
                    BubbleAction(
                        icon = Icons.Rounded.Edit,
                        label = "编辑",
                        tint = LocalOnGradient.current,
                        enabled = actionsEnabled,
                        onClick = onEditAnswer
                    )
                    BubbleAction(
                        icon = Icons.Rounded.Delete,
                        label = "删除",
                        tint = LocalOnGradient.current,
                        enabled = actionsEnabled,
                        onClick = onDelete
                    )
                    if (stats.isNotEmpty()) {
                        Text(
                            text = stats,
                            color = LocalOnGradientMuted.current.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motionOn = LocalBackgroundEnvironment.current.motionEnabled
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.30f
            pressed -> 1f
            else -> 0.62f
        },
        animationSpec = tween(130),
        label = "bubbleActionAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && motionOn) 0.96f else 1f,
        animationSpec = LucentMotion.toSpring(LucentMotion.Control),
        label = "bubbleActionScale"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) {
                Haptics.tick(context)
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

private data class PendingChatDelete(
    val message: UiChatMessage,
    val index: Int
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = LucentTitles.SectionSize, fontWeight = LucentTitles.SectionWeight) },
        text = {
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
                            .heightIn(min = 96.dp, max = 220.dp),
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

@Composable
private fun ThinkingLane(
    messageId: Long,
    liveThought: State<String>?,
    storedThought: String,
    tag: String?,
    streaming: Boolean,
    answerSeconds: Int
) {
    val accent = MaterialTheme.colorScheme.primary
    val onGradientMuted = LocalOnGradientMuted.current
    var expanded by remember(messageId) { mutableStateOf(streaming) }
    val laneState = rememberScrollState()
    val thought = liveThought?.value ?: storedThought

    LaunchedEffect(streaming) {
        if (!streaming) expanded = false
    }

    val title = when {
        streaming -> "思考中"
        answerSeconds > 0 -> "已深度思考（用时 ${answerSeconds} 秒）"
        else -> "已深度思考"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = title,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(animationSpec = LucentMotion.toIntSize(LucentMotion.Settle)),
            exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = LucentMotion.toIntSize(LucentMotion.Settle))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(start = 10.dp, bottom = 8.dp)
                    .drawBehind {
                        drawLine(
                            color = accent.copy(alpha = 0.28f),
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .padding(start = 10.dp)
            ) {
                if (!tag.isNullOrBlank()) {
                    Text(
                        text = tag,
                        color = onGradientMuted.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = thought.ifBlank { "等待模型给出推理内容…" },
                    color = if (thought.isBlank()) onGradientMuted.copy(alpha = 0.7f) else onGradientMuted,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 19.sp,
                    modifier = if (streaming) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(laneState)
                    }
                )
                if (streaming) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ThoughtPulse(color = accent)
                }
            }
        }
    }
}

@Composable
private fun ThoughtPulse(color: Color) {
    val pulse = rememberInfiniteTransition(label = "thoughtPulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thoughtPulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun BottomGlassComposer(
    modifier: Modifier = Modifier,
    inputText: String,
    onInputChange: (String) -> Unit,
    reasoningPreset: ReasoningPreset,
    onSelectReasoning: (ReasoningPreset) -> Unit,
    onOpenDialogueSettings: () -> Unit,
    selectedProfileName: String,
    selectedModel: String,
    onOpenModelPicker: () -> Unit,
    streaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    var showReasoningMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        modifier = modifier.frostedGlass(cornerRadius = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                textStyle = TextStyle(fontSize = 15.sp, color = onGradient),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp, max = 130.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "输入问题或针对笔记本提问...",
                            color = onGradientMuted.copy(alpha = 0.65f),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        val isHighIntensity = reasoningPreset == ReasoningPreset.DEEP || reasoningPreset == ReasoningPreset.MAXIMUM
                        val isThinkingActive = reasoningPreset != ReasoningPreset.AUTO
                        val thinkingBg = if (isHighIntensity) {
                            accent.copy(alpha = 0.85f)
                        } else if (isThinkingActive) {
                            accent.copy(alpha = 0.25f)
                        } else {
                            onGradient.copy(alpha = 0.08f)
                        }
                        val thinkingTint = if (isHighIntensity) {
                            onAccent
                        } else if (isThinkingActive) {
                            accent
                        } else {
                            onGradientMuted
                        }

                        Surface(
                            shape = CircleShape,
                            color = thinkingBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { showReasoningMenu = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    LucentIcons.presetIcon(reasoningPreset),
                                    contentDescription = "思考程度",
                                    tint = thinkingTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showReasoningMenu,
                            onDismissRequest = { showReasoningMenu = false }
                        ) {
                            Text(
                                text = "思考强度",
                                fontSize = LucentTitles.SectionSize,
                                fontWeight = LucentTitles.SectionWeight,
                                color = onGradientMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            ReasoningPreset.entries.forEach { preset ->
                                val isSelected = reasoningPreset == preset
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = LucentIcons.presetIcon(preset),
                                            contentDescription = null,
                                            tint = if (isSelected) accent else onGradientMuted,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = preset.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) accent else onGradient,
                                                fontSize = LucentTitles.ItemSize
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectReasoning(preset)
                                        showReasoningMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = LocalOnGradient.current.copy(alpha = 0.08f),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onOpenDialogueSettings() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "对话设置",
                                tint = onGradient,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = accent.copy(alpha = 0.22f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenModelPicker() }
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accent,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        LucentIcons.Model,
                                        contentDescription = null,
                                        tint = onAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedProfileName.ifBlank { selectedModel.take(8) },
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "展开模型选择",
                                tint = accent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = when {
                        streaming || inputText.isNotBlank() -> accent
                        else -> onGradient.copy(alpha = 0.15f)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = streaming || inputText.isNotBlank()) {
                            if (streaming) onStop() else onSend()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (streaming) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "停止生成",
                                tint = onAccent,
                                modifier = Modifier.size(19.dp)
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (inputText.isNotBlank()) onAccent else onGradient.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetCloseHandle(onDismiss: () -> Unit) {
    val ink = LocalOnGradient.current
    var travel by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    travel = (travel + delta).coerceAtLeast(0f)
                },
                onDragStopped = { velocity ->
                    val closing = travel >= 72f || velocity > 2000f
                    travel = 0f
                    if (closing) onDismiss()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = ink.copy(alpha = 0.3f),
            modifier = Modifier
                .graphicsLayer { translationY = travel }
                .size(width = 36.dp, height = 4.dp)
        ) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogueSettingsSheet(
    initialPrompt: String,
    initialHistoryRounds: Int,
    onSave: (prompt: String, historyRounds: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by remember { mutableStateOf(initialPrompt) }
    var historyRounds by remember { mutableIntStateOf(initialHistoryRounds) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val promptScroll = rememberScrollState()
    val ink = LocalOnGradient.current
    val muted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = lucentPanelBackground(),
        sheetGesturesEnabled = false,
        dragHandle = { SheetCloseHandle(onDismiss) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.width(64.dp)) {
                    Text(text = "取消", fontSize = 15.sp, color = muted)
                }
                Text(
                    text = "对话设置",
                    fontSize = LucentTitles.ItemSize,
                    fontWeight = LucentTitles.ItemWeight,
                    color = ink,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
                TextButton(
                    onClick = { onSave(prompt.trim(), historyRounds) },
                    modifier = Modifier.width(64.dp)
                ) {
                    Text(
                        text = "完成",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "系统提示词",
                fontSize = 13.sp,
                color = muted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ink.copy(alpha = 0.05f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, lucentGlassRim(), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    BasicTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        textStyle = TextStyle(fontSize = 15.sp, color = ink, lineHeight = 22.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 88.dp, max = 188.dp)
                            .verticalScroll(promptScroll),
                        decorationBox = { innerTextField ->
                            if (prompt.isEmpty()) {
                                Text(
                                    text = "告诉模型该如何回答：语气、篇幅、格式，或必须遵守的规则。",
                                    color = ink.copy(alpha = 0.40f),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${prompt.length} 字",
                        fontSize = 12.sp,
                        color = ink.copy(alpha = 0.45f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "对话上下文",
                fontSize = 13.sp,
                color = muted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ink.copy(alpha = 0.05f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, lucentGlassRim(), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    StepperSlider(
                        title = "携带历史轮数",
                        valueLabel = if (historyRounds == 10) "默认 10 轮" else "$historyRounds 轮",
                        value = historyRounds.toFloat(),
                        valueRange = 1f..20f,
                        step = 1f,
                        onValueChange = { historyRounds = it.toInt() }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        prompt = ""
                        historyRounds = 10
                    }
                ) {
                    Text(
                        text = "恢复默认",
                        fontSize = 13.sp,
                        color = muted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileModelPickerSheet(
    profiles: List<ApiProfile>,
    currentProfileName: String,
    currentModel: String,
    onSelect: (profileName: String, modelName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var expandedProfileIndex by remember { mutableIntStateOf(-1) }

    val ink = LocalOnGradient.current
    val sheetBg = lucentPanelBackground()
    val accent = MaterialTheme.colorScheme.primary
    val selectedBg = accent.copy(alpha = 0.22f)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = {
            Surface(
                shape = CircleShape,
                color = ink.copy(alpha = 0.3f),
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "模型选择",
                fontSize = LucentTitles.SectionSize,
                fontWeight = LucentTitles.SectionWeight,
                color = ink,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(profiles) { index, profile ->
                    val isExpanded = expandedProfileIndex == index
                    val isSelected = currentProfileName == profile.name
                    val modelCount = profile.selectedModels.size

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) selectedBg else Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (profile.selectedModels.size <= 1) {
                                        val modelName = profile.selectedModels.firstOrNull() ?: profile.model
                                        onSelect(profile.name, modelName)
                                    } else {
                                        expandedProfileIndex = if (isExpanded) -1 else index
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = if (isSelected) accent else ink.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = profile.name,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) accent else ink
                                )
                            }

                            Text(
                                text = "$modelCount",
                                fontSize = 14.sp,
                                color = ink.copy(alpha = 0.45f)
                            )
                        }

                        if (isExpanded && profile.selectedModels.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 40.dp, end = 12.dp, bottom = 8.dp)
                            ) {
                                profile.selectedModels.forEach { subModel ->
                                    val isSubSelected = isSelected && currentModel == subModel
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                onSelect(profile.name, subModel)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = subModel,
                                            fontSize = LucentTitles.ItemSize,
                                            color = if (isSubSelected) accent else ink.copy(alpha = 0.85f),
                                            fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSubSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ink.copy(alpha = 0.06f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        AppNavigation.requestScreen(Screen.Settings)
                        SettingsNav.go(SettingsRoute.Api)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = ink.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "管理与添加 API 方案",
                            fontSize = LucentTitles.SectionSize,
                            color = ink.copy(alpha = 0.90f),
                            fontWeight = LucentTitles.SectionWeight
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "前往 API 设置",
                        tint = ink.copy(alpha = 0.50f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Copied Text", text)
    clipboard.setPrimaryClip(clip)
    com.lucent.app.ui.LucentToast.show(context, "已复制到剪贴板")
}
