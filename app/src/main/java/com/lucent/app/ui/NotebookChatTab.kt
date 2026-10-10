package com.lucent.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lucent.app.AppScope
import com.lucent.app.SettingsNav
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.ChatMessage
import com.lucent.app.data.ChatScope
import com.lucent.app.data.ModelCapabilityRegistry
import com.lucent.app.data.Note
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.ReasoningResolver
import com.lucent.app.data.SettingsCache
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.ChatMessagePayload
import com.lucent.app.network.LlmClient
import com.lucent.app.ui.SettingsRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class UiChatMessage(
    val id: Long,
    val role: String,
    val content: String,
    val reasoningText: String? = null,
    val reasoningDurationSec: Int = 18,
    val reasoningTag: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NotebookChatTab(
    notebookId: Long,
    notebookTitle: String,
    sourceNotes: List<Note>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val prefs = remember(notebookId) {
        context.getSharedPreferences("lucent_notebook_chat_prefs", Context.MODE_PRIVATE)
    }

    var inputText by remember { mutableStateOf("") }
    val savedProfiles = remember(SettingsCache.apiProfilesJson) {
        ApiProfiles.parse(SettingsCache.apiProfilesJson).ifEmpty { ApiProfiles.DEFAULT_PROFILES }
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
    var temperature by remember {
        mutableFloatStateOf(prefs.getFloat("temperature_$notebookId", -1f))
    }

    var showDialogueSettingsDialog by remember { mutableStateOf(false) }
    var showProfileModelPickerSheet by remember { mutableStateOf(false) }

    val messages = remember { mutableStateListOf<UiChatMessage>() }

    LaunchedEffect(notebookId) {
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
                    reasoningDurationSec = 18
                )
            })
        }
    }

    val onSendMessage: () -> Unit = send@{
        val text = inputText.trim()
        if (text.isNotEmpty()) {
            val matchedProfile = savedProfiles.find { it.name == selectedProfileName }
                ?: savedProfiles.firstOrNull()
            val targetModel = selectedModel.ifBlank { matchedProfile?.model.orEmpty() }
            if (targetModel.isBlank()) {
                LucentToast.show(context, "该方案还没有可用模型，请到设置-API 配置获取并保存模型")
                return@send
            }
            inputText = ""
            val userMsg = UiChatMessage(
                id = System.currentTimeMillis(),
                role = "user",
                content = text
            )
            messages.add(userMsg)

            scope.launch {
                delay(100)
                listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
            }

            val convId = ChatScope.forNotebook(notebookId)
            AppScope.io.launch {
                db.chatDao().insert(ChatMessage(role = "user", content = text, conversationId = convId))
            }

            val aiMsgId = System.currentTimeMillis() + 1

            val specStr = matchedProfile?.spec?.lowercase() ?: SettingsCache.apiSpec.lowercase()
            val spec = when {
                specStr.contains("google") || specStr.contains("gemini") -> ApiSpec.GOOGLE
                specStr.contains("anthropic") || specStr.contains("claude") -> ApiSpec.ANTHROPIC
                else -> ApiSpec.OPENAI
            }

            val baseUrl = matchedProfile?.baseUrl?.ifBlank { SettingsCache.baseUrl } ?: SettingsCache.baseUrl
            val apiKey = matchedProfile?.apiKey?.ifBlank { SettingsCache.apiKey } ?: SettingsCache.apiKey
            val customUa = matchedProfile?.customUserAgent.orEmpty()

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
                    content = "【未配置 API 密钥】\n当前所选方案未设置有效的 API Key。请点击底部右侧「模型选择」卡片中的「管理与添加 API 方案」，或前往「设置 -> API 配置」填入 API Key 与基础 URL，即可开始基于本地 Markdown 知识库与大模型对话。",
                    reasoningText = "配置检查：未找到有效 API Key (方案: $selectedProfileName · 模型: $targetModel)",
                    reasoningDurationSec = 1,
                    reasoningTag = effortLabel
                )
                messages.add(pendingAi)
            } else {
                val promptHint = if (systemPrompt.isNotBlank()) "\n• 注入自定义提示词规则..." else ""
                val pendingAi = UiChatMessage(
                    id = aiMsgId,
                    role = "assistant",
                    content = "正在思考并回答中...",
                    reasoningText = "思考过程 ($effortLabel · 模型：$selectedProfileName / $targetModel)：\n• 正在检索笔记本《$notebookTitle》关联的 ${sourceNotes.size} 篇完整 Markdown 文档...$promptHint\n• 提取提问关键词并进行逻辑推理...\n• 生成精炼且富有层次感的结构化回答。",
                    reasoningDurationSec = if (reasoningPreset == ReasoningPreset.MAXIMUM || reasoningPreset == ReasoningPreset.DEEP) 24 else 12,
                    reasoningTag = effortLabel
                )
                messages.add(pendingAi)

                scope.launch {
                    val startTime = System.currentTimeMillis()
                    val scheduler = StreamUiScheduler(
                        timeWindowMs = 35L,
                        charBurstThreshold = 48
                    ) { currentContent, currentReasoning ->
                        val durationSec = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1)
                        val currentAi = UiChatMessage(
                            id = aiMsgId,
                            role = "assistant",
                            content = currentContent.ifBlank { "正在思考并回答中..." },
                            reasoningText = currentReasoning
                                ?: if (currentContent.isEmpty()) {
                                    "思考过程 ($effortLabel · 模型：$selectedProfileName / $targetModel)：\n• 正在检索笔记本《$notebookTitle》关联的 ${sourceNotes.size} 篇完整 Markdown 文档...\n• 正在实时分析推理中..."
                                } else null,
                            reasoningDurationSec = durationSec,
                            reasoningTag = effortLabel
                        )
                        val idx = messages.indexOfFirst { it.id == aiMsgId }
                        if (idx >= 0) {
                            messages[idx] = currentAi
                        }
                    }

                    val chatResult = LlmClient.chatStream(
                        baseUrl = baseUrl,
                        spec = spec,
                        apiKey = apiKey,
                        model = targetModel,
                        messages = historyPayload,
                        systemInstruction = knowledgeBasePrompt,
                        temperature = temperature,
                        customUserAgent = customUa,
                        reasoningConfig = resolvedReasoning
                    ) { deltaText, deltaThought ->
                        scheduler.append(deltaText, deltaThought)
                    }

                    scheduler.forceFlush()

                    val durationSec = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1)

                    chatResult.onSuccess { res ->
                        val finalContent = res.content.ifBlank { scheduler.getFinalContent() }.ifBlank { "（模型返回了空回复）" }
                        val finalReasoning = res.reasoningText ?: scheduler.getFinalReasoning()
                            ?: "思考完毕 ($effortLabel · 用时 ${durationSec}秒)：\n• 知识库全文匹配完成；\n• 生成结构化分析回答。"

                        val finalAi = UiChatMessage(
                            id = aiMsgId,
                            role = "assistant",
                            content = finalContent,
                            reasoningText = finalReasoning,
                            reasoningDurationSec = durationSec,
                            reasoningTag = effortLabel
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
                                    conversationId = convId
                                )
                            )
                        }
                    }.onFailure { err ->
                        val currentText = scheduler.getFinalContent()
                        val errorDisplay = if (currentText.isNotBlank()) {
                            "$currentText\n\n[网络中断：${err.message ?: "连接已关闭"}，已保留已生成内容]"
                        } else {
                            "【请求失败】${err.message ?: "网络或接口异常"}\n\n请检查网络连接、API 密钥与模型端点配置。"
                        }

                        val failedAi = UiChatMessage(
                            id = aiMsgId,
                            role = "assistant",
                            content = errorDisplay,
                            reasoningText = scheduler.getFinalReasoning()
                                ?: "思考中断：调用接口时出现异常 (${err.javaClass.simpleName})",
                            reasoningDurationSec = durationSec,
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
                                        conversationId = convId
                                    )
                                )
                            }
                        }
                    }

                    delay(100)
                    listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
                }
            }
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible

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
                    .imeNestedScroll()
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
                    if (msg.role == "user") {
                        UserMessageCard(
                            message = msg,
                            onEdit = { inputText = msg.content },
                            onDelete = {
                                messages.remove(msg)
                                AppScope.io.launch {
                                    db.chatDao().deleteByIds(listOf(msg.id))
                                }
                            }
                        )
                    } else {
                        AiMessageCard(
                            message = msg
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
                onSend = onSendMessage
            )
        }

        if (showDialogueSettingsDialog) {
            DialogueSettingsDialog(
                initialPrompt = systemPrompt,
                initialHistoryRounds = historyRounds,
                initialTemperature = temperature,
                onSave = { p, r, t ->
                    systemPrompt = p
                    historyRounds = r
                    temperature = t
                    prefs.edit()
                        .putString("prompt_$notebookId", p)
                        .putInt("history_rounds_$notebookId", r)
                        .putFloat("temperature_$notebookId", t)
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
    }
}

@Composable
private fun UserMessageCard(
    message: UiChatMessage,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val bubbleBg = Color(0xFF2563EB).copy(alpha = 0.30f)
    val bubbleBorder = Color(0xFF60A5FA).copy(alpha = 0.35f)
    val textColor = LocalOnGradient.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bubbleBg,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, bubbleBorder, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.content,
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { copyTextToClipboard(context, message.content) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("编辑问题")
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("删除记录", color = Color.Red)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMessageCard(
    message: UiChatMessage
) {
    var expandedThinking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp)
    ) {
        if (!message.reasoningText.isNullOrBlank()) {
            val thinkingTag = message.reasoningTag?.ifBlank { null } ?: "原厂自适应"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expandedThinking = !expandedThinking }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "已深度思考 · $thinkingTag (用时 ${message.reasoningDurationSec}秒)",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expandedThinking) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "展开思考",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = expandedThinking,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .frostedGlass(cornerRadius = 12.dp)
                ) {
                    Text(
                        text = message.reasoningText,
                        color = LocalOnGradient.current.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

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
    }
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
    onSend: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    var showReasoningMenu by remember { mutableStateOf(false) }

    val modelDef = remember(selectedModel, selectedProfileName) {
        ModelCapabilityRegistry.findOrRegister(selectedModel, selectedProfileName)
    }

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
                cursorBrush = SolidColor(Color(0xFF38BDF8)),
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
                            Color(0xFFF59E0B).copy(alpha = 0.28f)
                        } else if (isThinkingActive) {
                            Color(0xFF0284C7).copy(alpha = 0.25f)
                        } else {
                            onGradient.copy(alpha = 0.08f)
                        }
                        val thinkingTint = if (isHighIntensity) {
                            Color(0xFFFBBF24)
                        } else if (isThinkingActive) {
                            Color(0xFF38BDF8)
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
                                    Icons.Default.Psychology,
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
                                text = "思考强度 (三层自适应体系)",
                                fontSize = LucentTitles.SectionSize,
                                fontWeight = LucentTitles.SectionWeight,
                                color = onGradientMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )

                            val isNativeSupported = modelDef.reasoning.supported
                            Text(
                                text = if (isNativeSupported) "模型《${modelDef.id}》· 原生支持深度推理" else "模型《${modelDef.id}》· 遵循原厂自适应策略",
                                fontSize = 11.sp,
                                color = if (isNativeSupported) Color(0xFF38BDF8) else onGradientMuted.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            ReasoningPreset.entries.forEach { preset ->
                                val isSelected = reasoningPreset == preset
                                val icon = when (preset) {
                                    ReasoningPreset.AUTO -> "✨"
                                    ReasoningPreset.FAST -> "⚡"
                                    ReasoningPreset.BALANCED -> "⚖️"
                                    ReasoningPreset.DEEP -> "🧠"
                                    ReasoningPreset.MAXIMUM -> "🚀"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "$icon  ${preset.label}",
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFFF59E0B) else onGradient,
                                                    fontSize = LucentTitles.ItemSize
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFFF59E0B),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = preset.desc,
                                                color = onGradientMuted.copy(alpha = 0.75f),
                                                fontSize = 11.sp
                                            )
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
                        color = Color(0xFF0284C7).copy(alpha = 0.22f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpenModelPicker() }
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedProfileName.ifBlank { selectedModel.take(8) },
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "展开模型选择",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (inputText.isNotBlank()) Color(0xFF0284C7) else onGradient.copy(alpha = 0.15f),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(enabled = inputText.isNotBlank()) { onSend() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (inputText.isNotBlank()) Color.White else onGradient.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogueSettingsDialog(
    initialPrompt: String,
    initialHistoryRounds: Int,
    initialTemperature: Float,
    onSave: (prompt: String, historyRounds: Int, temperature: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by remember { mutableStateOf(initialPrompt) }
    var historyRounds by remember { mutableIntStateOf(initialHistoryRounds) }
    var temperature by remember { mutableFloatStateOf(initialTemperature) }
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val rootScroll = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = lucentPanelBackground(),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, lucentGlassRim(), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "提示词与模型参数",
                        fontSize = LucentTitles.SectionSize,
                        fontWeight = LucentTitles.SectionWeight,
                        color = onGradient
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = onGradient.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rootScroll)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = onGradient.copy(alpha = 0.05f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, onGradient.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            BasicTextField(
                                value = prompt,
                                onValueChange = { prompt = it },
                                textStyle = TextStyle(fontSize = 14.sp, color = onGradient, lineHeight = 20.sp),
                                cursorBrush = SolidColor(accent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp, max = 130.dp),
                                decorationBox = { innerTextField ->
                                    if (prompt.isEmpty()) {
                                        Text(
                                            text = "输入自定义系统提示词，用于指导模型在此笔记本中的回答风格与规则...",
                                            color = onGradient.copy(alpha = 0.40f),
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "${prompt.length} 字",
                                fontSize = 12.sp,
                                color = onGradient.copy(alpha = 0.45f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "模型参数",
                            fontSize = LucentTitles.SectionSize,
                            fontWeight = LucentTitles.SectionWeight,
                            color = onGradientMuted
                        )
                        TextButton(
                            onClick = {
                                historyRounds = 10
                                temperature = -1f
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "重置",
                                fontSize = 13.sp,
                                color = accent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlass()
                            .padding(16.dp)
                    ) {
                        StandardSliderRow(
                            title = "携带历史轮数",
                            valueLabel = "$historyRounds 轮",
                            value = historyRounds.toFloat(),
                            valueRange = 1f..20f,
                            steps = 18,
                            onValueChange = { historyRounds = it.toInt() },
                            onValueChangeFinished = { },
                            onGradient = onGradient
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = onGradient.copy(alpha = 0.08f)
                        )

                        StandardSliderRow(
                            title = "温度 (Temperature)",
                            valueLabel = if (temperature < 0f) "未设置" else String.format("%.1f", temperature),
                            value = if (temperature < 0f) 0.7f else temperature,
                            valueRange = 0f..2f,
                            steps = 19,
                            active = temperature >= 0f,
                            onValueChange = { temperature = it },
                            onValueChangeFinished = { },
                            onGradient = onGradient
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 4.dp),
                    color = onGradient.copy(alpha = 0.08f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = "取消",
                            fontSize = 14.sp,
                            color = onGradientMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onSave(prompt, historyRounds, temperature) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = "保存",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                            .background(if (isSelected) Color(0xFF0284C7).copy(alpha = 0.12f) else Color.Transparent)
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
                                    tint = if (isSelected) Color(0xFF38BDF8) else ink.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = profile.name,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF38BDF8) else ink
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
                                            color = if (isSubSelected) Color(0xFF38BDF8) else ink.copy(alpha = 0.85f),
                                            fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSubSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
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
