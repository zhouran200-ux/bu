package com.lucent.app.ui.settings

import android.content.ClipData
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.collapseExcessBlankLines
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.network.ApiProbe
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ApiConfigSettingsPage(
    repo: SettingsRepository,
    scrollState: ScrollState,
    viewportBounds: Rect?,
    active: Boolean,
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
    onReorderProfiles: (List<ApiProfile>, Int, (Result<Unit>) -> Unit) -> Unit,
    onAddProfile: () -> Unit,
    onSaveProfile: () -> Unit
) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val requestTarget = ApiRequestTarget(profiles.getOrNull(selectedProfileIdx)?.id, url.trim(), spec, key.trim(), active)
    val latestRequestTarget = rememberUpdatedState(requestTarget)
    val modelRequests = remember { ApiRequestOwner() }
    val testRequests = remember { ApiRequestOwner() }
    DisposableEffect(requestTarget, active) {
        modelRequests.cancel()
        testRequests.cancel()
        onDispose {
            modelRequests.cancel()
            testRequests.cancel()
        }
    }
    var fetchedModels by remember(requestTarget.profileId) { mutableStateOf<List<String>>(emptyList()) }
    var pickerOpen by remember(requestTarget.profileId) { mutableStateOf(false) }
    var testingConn by remember(requestTarget.profileId) { mutableStateOf(false) }
    var testStatusText by remember(requestTarget.profileId) { mutableStateOf("") }
    var testSuccess by remember(requestTarget.profileId) { mutableStateOf(false) }
    var lastTestResult by remember(requestTarget.profileId) { mutableStateOf<TestConnectionResult?>(null) }
    var fetchModelsError by remember(requestTarget.profileId) { mutableStateOf("") }
    var copiedRawHint by remember(requestTarget.profileId) { mutableStateOf(false) }
    var customModelInput by remember(requestTarget.profileId) { mutableStateOf("") }
    val choices = remember(fetchedModels, models) { modelChoices(fetchedModels, models) }
    val dragState = remember { mutableStateOf<ProfileDrag?>(null) }
    val settled = dragState.value == null


    LaunchedEffect(requestTarget, selectedModel, customModelInput) {
        testRequests.cancel()
        lastTestResult = null
        testStatusText = ""
        testSuccess = false
        fetchModelsError = ""
    }

    ApiProfileListSection(
        dragState = dragState,
        profiles = profiles, selectedProfileIdx = selectedProfileIdx, models = models,
        active = active, scrollState = scrollState, viewportBounds = viewportBounds,
        onRequestDeleteProfile = onRequestDeleteProfile, onSelectProfile = onSelectProfile,
        onReorderProfiles = onReorderProfiles, onAddProfile = onAddProfile
    )

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
                "custom" to S.apiSpecCustom
            ),
            selectedKey = spec,
            onSelect = { onSpecChange(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        CompactField(
            value = url,
            onValueChange = { onUrlChange(collapseExcessBlankLines(it)) },
            label = S.fieldBaseUrl,
            placeholder = if (spec == "custom") S.apiCustomPlaceholder else "",
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
                enabled = !loading && active,
                onClick = {
                    if (url.trim().isEmpty()) {
                        onErrorTextChange(S.apiUrlRequired)
                    } else {
                        onErrorTextChange("")
                        testStatusText = ""
                        lastTestResult = null
                        fetchModelsError = ""
                        val target = requestTarget
                        modelRequests.start(
                            scope = scope,
                            isTargetCurrent = { latestRequestTarget.value == target },
                            setBusy = onLoadingChange,
                            onFailure = { fetchModelsError = S.apiFetchModelsFailed },
                            request = { ApiProbe.fetchModels(url, spec, key) }
                        ) { result ->
                            result.onSuccess { list ->
                                if (list.isEmpty()) {
                                    onErrorTextChange(S.apiModelsEmpty)
                                } else {
                                    fetchedModels = list
                                    pickerOpen = true
                                }
                            }.onFailure {
                                val detail = (it.message ?: "").trim().ifBlank { S.noDetails }.take(180)
                                fetchModelsError = S.apiModelsFetchFailed(detail)
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
                text = S.apiChatModel,
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
                                contentDescription = S.apiRemoveModel
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
            placeholder = S.apiModelNamePlaceholder,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addCustomModel() }),
            trailing = {
                if (customModelInput.isNotBlank()) {
                    IconButton(onClick = { addCustomModel() }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = S.apiAddModel,
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
        val latestTestModel = rememberUpdatedState(currentTestTarget)
        val testBtnText = if (testingConn) {
            S.apiTesting
        } else if (currentTestTarget.isNotBlank()) {
            S.apiTestConnectionTarget(currentTestTarget)
        } else {
            S.apiTestConnection
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
                testStatusText = ""
                testSuccess = false
                lastTestResult = null
                fetchModelsError = ""
                onErrorTextChange("")
                val target = requestTarget
                val targetModel = currentTestTarget
                testRequests.start(
                    scope = scope,
                    isTargetCurrent = { latestRequestTarget.value == target && latestTestModel.value == targetModel },
                    setBusy = { testingConn = it },
                    onFailure = { testSuccess = false; testStatusText = S.apiTestConnectionFailed },
                    request = { ApiProbe.testConnection(url, spec, key, targetModel) }
                ) { result ->
                    result.onSuccess { res ->
                        lastTestResult = res
                        testSuccess = res.success
                        testStatusText = res.formatDisplayMessage()
                    }.onFailure { err ->
                        testSuccess = false
                        val msg = (err.message ?: "").trim().ifBlank { S.apiConnectionFailed }.take(180)
                        testStatusText = S.apiNetworkFailed(msg)
                    }
                }
            },
            enabled = !testingConn && active,
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
            val prettyBody = remember(res) {
                if (res.success) res.prettyResponseBody() else buildString {
                appendLine(S.apiDetails)
                if (res.httpCode > 0) appendLine(S.apiHttpStatus(res.httpCode))
                appendLine(S.apiLatency(res.latencyMs))
                res.errorMessage?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                appendLine(S.apiRawResponse)
                append(res.prettyResponseBody().ifBlank { S.apiEmptyResponse })
                }
            }
            val textToCopy = prettyBody
            Spacer(modifier = Modifier.height(8.dp))
            ResponseCard(
                body = prettyBody,
                charCount = prettyBody.length,
                copied = copiedRawHint,
                onCopy = { copyRawResponse(textToCopy) }
            )
        }

        if (fetchModelsError.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            ResponseCard(
                body = fetchModelsError,
                charCount = fetchModelsError.length,
                copied = copiedRawHint,
                onCopy = { copyRawResponse(fetchModelsError) }
            )
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
                if (selectedModel !in list) {
                    onSelectedModelChange(list.firstOrNull().orEmpty())
                }
                LucentToast.show(context, S.apiModelsApplied)
            },
            onDismiss = { pickerOpen = false }
        )
    }
}
