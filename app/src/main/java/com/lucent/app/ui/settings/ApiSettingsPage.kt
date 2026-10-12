package com.lucent.app.ui.settings

import android.content.ClipData
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key as itemKey
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lucent.app.AppScope
import com.lucent.app.collapseExcessBlankLines
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.UserAgentPresets
import com.lucent.app.i18n.S
import com.lucent.app.network.ApiSpec
import com.lucent.app.network.LlmClient
import com.lucent.app.network.TestConnectionResult
import com.lucent.app.ui.ApiModelPickerDialog
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.LucentIcons
import com.lucent.app.ui.Haptics
import com.lucent.app.ui.LocalOnGradient
import com.lucent.app.ui.LocalOnGradientMuted
import com.lucent.app.ui.LucentToast
import com.lucent.app.ui.LucentTitles
import com.lucent.app.ui.frostedGlass
import com.lucent.app.ui.selectedCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun modelChoices(fetched: List<String>, saved: List<String>): List<String> {
    val out = LinkedHashSet<String>()
    fetched.forEach { if (it.isNotBlank()) out.add(it.trim()) }
    saved.sorted().forEach { if (it.isNotBlank()) out.add(it.trim()) }
    return out.toList()
}

private val PROFILE_SLIDE_SPRING = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
private val ROW_CARD_RADIUS = 14.dp
private const val HELD_SCALE = 1.02f
private const val HELD_ALPHA = 0.94f
private const val REORDER_COMMIT_TIMEOUT_MS = 1200L

private val CompactFieldHeight = 42.dp
private val CompactFieldLabelWidth = 56.dp

private data class ProfileDrag(
    val order: List<ApiProfile>,
    val keys: List<String>,
    val selected: Int,
    val held: Int,
    val travel: Float
)

private fun ProfileDrag.stepped(step: Int): ProfileDrag {
    val to = held + step
    if (held !in order.indices || to !in order.indices) return this
    return ProfileDrag(
        order = ApiProfiles.moved(order, held, to),
        keys = ApiProfiles.moved(keys, held, to),
        selected = ApiProfiles.selectionAfterMove(selected, held, to),
        held = to,
        travel = travel
    )
}

@Composable
private fun DeleteWithConfirmButton(
    onDelete: () -> Unit,
    tint: Color,
    buttonSize: Dp = 26.dp,
    iconSize: Dp = 16.dp,
    confirmTextPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    confirmTextSize: TextUnit = 11.sp,
    modifier: Modifier = Modifier,
    contentDescription: String = "删除"
) {
    val context = LocalContext.current
    var confirmOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (confirmOpen) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE53935))
                    .clickable {
                        confirmOpen = false
                        Haptics.tick(context)
                        onDelete()
                    }
                    .padding(confirmTextPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "确认删除",
                    color = Color.White,
                    fontSize = confirmTextSize,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    confirmOpen = false
                    Haptics.tick(context)
                },
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消",
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        } else {
            IconButton(
                onClick = {
                    confirmOpen = true
                    Haptics.tick(context)
                },
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    placeholder: String = "",
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CompactFieldHeight)
            .clip(shape)
            .background(onGradient.copy(alpha = if (focused) 0.07f else 0.04f))
            .border(1.dp, if (focused) accent else onGradientMuted.copy(alpha = 0.3f), shape)
            .padding(horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label.isNotBlank()) {
                Text(
                    text = label,
                    color = onGradientMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.width(CompactFieldLabelWidth)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                readOnly = readOnly,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = if (readOnly) onGradientMuted else onGradient
                ),
                cursorBrush = SolidColor(accent),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interaction,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (label.isNotBlank()) 6.dp else 0.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotBlank()) {
                            Text(
                                text = placeholder,
                                color = onGradientMuted.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        inner()
                    }
                }
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(2.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun SectionRule() {
    val line = LocalOnGradient.current.copy(alpha = 0.18f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, line, line, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(LocalOnGradient.current.copy(alpha = 0.055f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    val onGradientMuted = LocalOnGradientMuted.current
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (key, label) ->
            val selected = key == selectedKey
            val shape = RoundedCornerShape(percent = 50)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) accent else onGradientMuted.copy(alpha = 0.3f),
                        shape
                    )
                    .clickable { onSelect(key) }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) accent else onGradientMuted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FloatingCopyButton(
    copied: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = if (copied) 0.22f else 0.12f))
            .border(1.dp, accent.copy(alpha = if (copied) 0.7f else 0.42f), shape)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = "复制全文",
            tint = accent,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (copied) "已复制" else "复制全文",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ApiConfigSettingsPage(
    repo: SettingsRepository,
    profiles: List<ApiProfile>,
    selectedProfileIdx: Int,
    editingProfileName: String,
    onEditingProfileNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    spec: String,
    onSpecChange: (String) -> Unit,
    key: String,
    onKeyChange: (String) -> Unit,
    keyVisible: Boolean,
    onRevealKey: () -> Unit,
    selectedModel: String,
    onSelectedModelChange: (String) -> Unit,
    models: List<String>,
    onModelsChange: (List<String>) -> Unit,
    loading: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    errorText: String,
    onErrorTextChange: (String) -> Unit,
    onRequestDeleteProfile: (Int) -> Unit,
    onSelectProfile: (Int) -> Unit,
    onReorderProfiles: (List<ApiProfile>, Int) -> Unit,
    onAddProfile: () -> Unit,
    onSaveProfile: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var testingConn by remember { mutableStateOf(false) }
    var testStatusText by remember { mutableStateOf("") }
    var testSuccess by remember { mutableStateOf(false) }
    var lastTestResult by remember { mutableStateOf<TestConnectionResult?>(null) }
    var copiedRawHint by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf("") }
    val choices = modelChoices(fetchedModels, models)

    var drag by remember { mutableStateOf<ProfileDrag?>(null) }
    var rowStride by remember { mutableFloatStateOf(0f) }
    val baseKeys = remember(profiles) { ApiProfiles.orderKeys(profiles) }
    val shownProfiles = drag?.order ?: profiles
    val shownKeys = drag?.keys ?: baseKeys
    val activeProfileIdx = drag?.selected ?: selectedProfileIdx
    val canReorder = shownProfiles.size > 1
    val settled = drag == null
    val liveProfiles = rememberUpdatedState(profiles)
    val liveSelected = rememberUpdatedState(selectedProfileIdx)
    val liveReorder = rememberUpdatedState(onReorderProfiles)

    fun beginDrag(idx: Int) {
        val current = drag
        if (current != null && current.held >= 0) return
        Haptics.tick(context)
        if (current != null) {
            drag = current.copy(held = idx, travel = 0f)
        } else {
            val base = liveProfiles.value
            drag = ProfileDrag(
                order = base,
                keys = ApiProfiles.orderKeys(base),
                selected = liveSelected.value,
                held = idx,
                travel = 0f
            )
        }
    }

    fun endDrag() {
        val current = drag ?: return
        if (current.held < 0) return
        if (current.order == liveProfiles.value) {
            drag = null
        } else {
            drag = current.copy(held = -1)
            liveReorder.value(current.order, current.selected)
        }
    }

    LaunchedEffect(drag?.order, drag?.held, profiles) {
        val pending = drag ?: return@LaunchedEffect
        if (pending.held >= 0) return@LaunchedEffect
        if (pending.order == profiles) {
            drag = null
        } else {
            delay(REORDER_COMMIT_TIMEOUT_MS)
            if (drag === pending) drag = null
        }
    }

    LaunchedEffect(selectedProfileIdx, selectedModel) {
        lastTestResult = null
        testStatusText = ""
        testSuccess = false
    }

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.apiSelectionTitle, color = onGradient, fontSize = LucentTitles.SectionSize, fontWeight = LucentTitles.SectionWeight, modifier = Modifier.weight(1f))
            Text("${shownProfiles.size}/${ApiProfiles.MAX}", color = onGradientMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        SectionRule()
        Spacer(modifier = Modifier.height(8.dp))
        if (profiles.isEmpty()) {
            Text(S.apiNoneTitle, color = onGradient, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(S.apiNoneBody, color = onGradientMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(3.dp))
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            shownProfiles.forEachIndexed { idx, p ->
                val identity = shownKeys[idx]
                itemKey(identity) {
                    var restingIdx by remember { mutableIntStateOf(idx) }
                    val slide = remember { Animatable(0f) }
                    val liveIdx = rememberUpdatedState(idx)
                    val held = drag?.held == idx
                    val isActive = idx == activeProfileIdx
                    LaunchedEffect(idx, held) {
                        val stride = rowStride
                        if (restingIdx != idx) {
                            val shift = (restingIdx - idx) * stride
                            restingIdx = idx
                            if (held || stride <= 0f) {
                                slide.snapTo(0f)
                            } else {
                                slide.snapTo(shift)
                                slide.animateTo(0f, PROFILE_SLIDE_SPRING)
                            }
                        } else if (!held) {
                            val residual = drag?.travel ?: 0f
                            if (residual != 0f && stride > 0f) {
                                slide.snapTo(residual)
                                slide.animateTo(0f, PROFILE_SLIDE_SPRING)
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .zIndex(if (held) 1f else 0f)
                            .fillMaxWidth()
                            .onSizeChanged { if (it.height > 0) rowStride = it.height.toFloat() }
                            .padding(bottom = 6.dp)
                            .graphicsLayer {
                                val carried = drag?.travel ?: 0f
                                translationY = slide.value + (if (held) carried else 0f)
                                scaleX = if (held) HELD_SCALE else 1f
                                scaleY = if (held) HELD_SCALE else 1f
                                alpha = if (held) HELD_ALPHA else 1f
                            }
                            .frostedGlass(cornerRadius = ROW_CARD_RADIUS)
                            .selectedCard(isActive, ROW_CARD_RADIUS)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        if (canReorder) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .pointerInput(identity) {
                                        var travelled = 0f
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                travelled = 0f
                                                beginDrag(liveIdx.value)
                                            },
                                            onDrag = { change, delta ->
                                                change.consume()
                                                val stride = rowStride
                                                val current = drag
                                                if (stride > 0f && current != null && current.held >= 0) {
                                                    travelled += delta.y
                                                    var next: ProfileDrag = current
                                                    while (travelled >= stride) {
                                                        val stepped = next.stepped(+1)
                                                        if (stepped === next) {
                                                            travelled = 0f
                                                            break
                                                        }
                                                        next = stepped
                                                        travelled -= stride
                                                    }
                                                    while (travelled <= -stride) {
                                                        val stepped = next.stepped(-1)
                                                        if (stepped === next) {
                                                            travelled = 0f
                                                            break
                                                        }
                                                        next = stepped
                                                        travelled += stride
                                                    }
                                                    val edge = when {
                                                        next.held == 0 -> travelled.coerceIn(0f, stride)
                                                        next.held == next.order.lastIndex -> travelled.coerceIn(-stride, 0f)
                                                        else -> travelled.coerceIn(-stride, stride)
                                                    }
                                                    drag = next.copy(travel = edge)
                                                    if (next !== current) Haptics.tick(context)
                                                }
                                            },
                                            onDragEnd = { endDrag() },
                                            onDragCancel = { endDrag() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = S.a11yDragToReorder,
                                    tint = onGradientMuted.copy(alpha = onGradientMuted.alpha * 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = settled,
                                    onClick = { onSelectProfile(idx) }
                                )
                        ) {
                            Text(
                                text = p.name.ifBlank { "方案 ${idx + 1}" },
                                color = onGradient,
                                fontSize = LucentTitles.ItemSize,
                                fontWeight = LucentTitles.ItemWeight,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val modelCount = if (idx == activeProfileIdx) models.size else p.selectedModels.size
                            Text(
                                text = "· $modelCount 个模型",
                                color = onGradientMuted,
                                fontSize = 12.sp
                            )
                        }
                        DeleteWithConfirmButton(
                            onDelete = { if (settled) onRequestDeleteProfile(idx) },
                            tint = onGradientMuted,
                            buttonSize = 30.dp,
                            iconSize = 18.dp,
                            confirmTextPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            confirmTextSize = 11.sp,
                            contentDescription = S.apiDeleteA11y
                        )
                    }
                }
            }
        }
        if (profiles.size < ApiProfiles.MAX) {
            Spacer(modifier = Modifier.height(4.dp))
            GlassButton(
                text = S.apiAddButton,
                icon = Icons.Default.Add,
                enabled = settled,
                onClick = onAddProfile
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(12.dp)) {
        CompactField(
            value = editingProfileName,
            onValueChange = { onEditingProfileNameChange(collapseExcessBlankLines(it)) },
            label = S.fieldName,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Spacer(modifier = Modifier.height(8.dp))
        CompactField(
            value = key,
            onValueChange = { onKeyChange(collapseExcessBlankLines(it)) },
            label = S.fieldApiKey,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                IconButton(onClick = onRevealKey, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = S.a11yToggleKeyVisibility,
                        tint = onGradientMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        ChipRow(
            options = listOf(
                "openai" to "OpenAI",
                "anthropic" to "Anthropic",
                "google" to "Google",
                "custom" to "自定义"
            ),
            selectedKey = spec,
            onSelect = { onSpecChange(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        CompactField(
            value = url,
            onValueChange = { onUrlChange(collapseExcessBlankLines(it)) },
            label = S.fieldBaseUrl,
            placeholder = if (spec == "custom") "完整地址，不再自动补路径" else "",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            GlassButton(
                text = S.fetchModels,
                onClick = {
                    if (url.trim().isEmpty()) {
                        onErrorTextChange(S.apiUrlRequired)
                    } else {
                        onLoadingChange(true)
                        onErrorTextChange("")
                        testStatusText = ""
                        scope.launch {
                            val apiSpecEnum = when (spec) {
                                "anthropic" -> ApiSpec.ANTHROPIC
                                "google" -> ApiSpec.GOOGLE
                                else -> ApiSpec.OPENAI
                            }
                            val result = LlmClient.fetchModels(
                                baseUrl = url.trim(),
                                spec = apiSpecEnum,
                                apiKey = key.trim(),
                                rawEndpoint = spec == "custom"
                            )
                            onLoadingChange(false)
                            result.onSuccess { list ->
                                if (list.isEmpty()) {
                                    onErrorTextChange(S.apiModelsEmpty)
                                } else {
                                    fetchedModels = list
                                    pickerOpen = true
                                }
                            }.onFailure {
                                val detail = (it.message ?: "").trim().ifBlank { S.noDetails }.take(180)
                                onErrorTextChange(S.apiModelsFetchFailed(detail))
                            }
                        }
                    }
                }
            )
            if (loading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            GlassButton(
                text = S.saveApi,
                enabled = settled,
                onClick = {
                    val input = customModelInput.trim()
                    val finalModels = if (input.isNotBlank() && input !in models) {
                        models + input
                    } else {
                        models
                    }
                    if (selectedModel.isBlank() && input.isNotBlank()) {
                        onSelectedModelChange(input)
                    }
                    if (input.isNotBlank()) {
                        customModelInput = ""
                    }
                    onModelsChange(finalModels)
                    onSaveProfile()
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "对话模型",
                color = onGradient,
                fontSize = LucentTitles.SectionSize,
                fontWeight = LucentTitles.SectionWeight
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${models.size}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        if (models.isNotEmpty()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    models.forEach { m ->
                        val isSelected = m == selectedModel
                        val cardShape = RoundedCornerShape(10.dp)
                        val cardBg = onGradient.copy(alpha = 0.04f)
                        val cardBorder = onGradientMuted.copy(alpha = 0.2f)
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else onGradient

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cardShape)
                                .background(cardBg)
                                .border(1.dp, cardBorder, cardShape)
                                .selectedCard(isSelected, 10.dp)
                                .combinedClickable(
                                    onClick = { onSelectedModelChange(if (isSelected) "" else m) },
                                    onLongClick = {
                                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("model", m))) }
                                        Haptics.tick(context)
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        else onGradient.copy(alpha = 0.08f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = LucentIcons.Model,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else onGradientMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = m,
                                color = contentColor,
                                fontSize = LucentTitles.ItemSize,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            DeleteWithConfirmButton(
                                onDelete = {
                                    val newModels = models - m
                                    onModelsChange(newModels)
                                    if (selectedModel == m) {
                                        onSelectedModelChange(newModels.firstOrNull().orEmpty())
                                    }
                                },
                                tint = onGradientMuted.copy(alpha = 0.7f),
                                buttonSize = 26.dp,
                                iconSize = 16.dp,
                                confirmTextPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
                                confirmTextSize = 11.sp,
                                contentDescription = "删除模型"
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        fun addCustomModel() {
            val trimmed = customModelInput.trim()
            if (trimmed.isNotBlank()) {
                if (trimmed !in models) {
                    onModelsChange(models + trimmed)
                }
                onSelectedModelChange(trimmed)
                customModelInput = ""
            }
        }

        CompactField(
            value = customModelInput,
            onValueChange = { customModelInput = collapseExcessBlankLines(it) },
            placeholder = "输入模型名称",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addCustomModel() }),
            trailing = {
                if (customModelInput.isNotBlank()) {
                    IconButton(onClick = { addCustomModel() }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加模型",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))
        val currentTestTarget = if (selectedModel.isNotBlank()) {
            selectedModel
        } else {
            customModelInput.trim()
        }
        val testBtnText = if (testingConn) {
            "测试中..."
        } else if (currentTestTarget.isNotBlank()) {
            "测试连接 ($currentTestTarget)"
        } else {
            "测试连接"
        }

        fun copyRawResponse(text: String) {
            if (text.isNotBlank()) {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("response", text))) }
                Haptics.tick(context)
                copiedRawHint = true
                scope.launch {
                    delay(2000)
                    copiedRawHint = false
                }
            }
        }

        Button(
            onClick = {
                if (url.trim().isEmpty()) {
                    onErrorTextChange(S.apiUrlRequired)
                    return@Button
                }
                testingConn = true
                testStatusText = ""
                testSuccess = false
                lastTestResult = null
                onErrorTextChange("")
                scope.launch {
                    val apiSpecEnum = when (spec) {
                        "anthropic" -> ApiSpec.ANTHROPIC
                        "google" -> ApiSpec.GOOGLE
                        else -> ApiSpec.OPENAI
                    }
                    val targetToTest = currentTestTarget
                    val result = LlmClient.testConnection(
                        baseUrl = url.trim(),
                        spec = apiSpecEnum,
                        apiKey = key.trim(),
                        model = targetToTest,
                        rawEndpoint = spec == "custom"
                    )
                    testingConn = false
                    result.onSuccess { res ->
                        lastTestResult = res
                        testSuccess = res.success
                        testStatusText = res.formatDisplayMessage()
                    }.onFailure { err ->
                        testSuccess = false
                        val msg = (err.message ?: "").trim().ifBlank { "连接失败" }.take(180)
                        testStatusText = "[网络连接失败/无法访问服务器] $msg"
                    }
                }
            },
            enabled = !testingConn,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = testBtnText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (testingConn) {
            Spacer(modifier = Modifier.height(6.dp))
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        if (testStatusText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = testStatusText,
                color = if (testSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            )
        }

        lastTestResult?.let { res ->
            val prettyBody = res.prettyResponseBody()
            val textToCopy = if (res.rawResponseBody.isNotBlank()) res.rawResponseBody else res.toFullReport()

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(cornerRadius = 12.dp)
                    .combinedClickable(
                        onClick = { },
                        onLongClick = { copyRawResponse(textToCopy) }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(onGradient.copy(alpha = 0.05f))
                            .padding(8.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = prettyBody.ifBlank { res.errorMessage ?: "(空响应体)" },
                            color = onGradient,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(scrollState)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "长按卡片或点右上角按钮复制全文",
                            color = onGradientMuted.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${res.rawResponseBody.length} 字符",
                            color = onGradientMuted.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    FloatingCopyButton(copied = copiedRawHint) { copyRawResponse(textToCopy) }
                }
            }
        }
        if (errorText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(errorText, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    GlobalUserAgentSection(repo = repo)

    if (pickerOpen) {
        ApiModelPickerDialog(
            names = choices,
            selected = models.toSet(),
            onDone = { picked ->
                val list = choices.filter { it in picked }
                onModelsChange(list)
                if (selectedModel.isBlank() && list.isNotEmpty()) {
                    onSelectedModelChange(list.first())
                }
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

@Composable
internal fun GlobalUserAgentSection(repo: SettingsRepository) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val savedUserAgent by repo.customUserAgent.collectAsState(initial = SettingsCache.customUserAgent)

    var isTavernSelected by remember(savedUserAgent) {
        mutableStateOf(savedUserAgent == UserAgentPresets.SILLY_TAVERN_NODE_FETCH)
    }
    var customInput by remember(savedUserAgent) {
        mutableStateOf(if (savedUserAgent == UserAgentPresets.SILLY_TAVERN_NODE_FETCH) "" else savedUserAgent)
    }

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = S.userAgentSectionTitle,
                color = onGradient,
                fontSize = LucentTitles.SectionSize,
                fontWeight = LucentTitles.SectionWeight,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GlassButton(
                text = S.userAgentSaveButton,
                onClick = {
                    Haptics.tick(context)
                    focusManager.clearFocus()
                    val target = if (isTavernSelected) UserAgentPresets.SILLY_TAVERN_NODE_FETCH else customInput.trim()
                    AppScope.io.launch { repo.setCustomUserAgent(target) }
                    LucentToast.show(context, S.userAgentSavedToast)
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        ChipRow(
            options = listOf(
                "default" to S.userAgentPresetDefault,
                "tavern" to S.userAgentPresetSillyTavern
            ),
            selectedKey = if (isTavernSelected) "tavern" else "default",
            onSelect = { picked ->
                Haptics.tick(context)
                isTavernSelected = picked == "tavern"
                if (isTavernSelected) focusManager.clearFocus()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        CompactField(
            value = if (isTavernSelected) UserAgentPresets.SILLY_TAVERN_NODE_FETCH else customInput,
            onValueChange = {
                if (!isTavernSelected) {
                    customInput = collapseExcessBlankLines(it)
                }
            },
            label = "标识",
            placeholder = S.fieldUserAgent,
            readOnly = isTavernSelected,
            trailing = {
                if (isTavernSelected) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = onGradientMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )
    }
}
