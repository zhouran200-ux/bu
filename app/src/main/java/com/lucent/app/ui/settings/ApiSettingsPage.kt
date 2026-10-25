package com.lucent.app.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfileImportMode
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.network.ApiProbe
import com.lucent.app.network.TestConnectionResult
import com.lucent.app.ui.GlassButton
import com.lucent.app.ui.Haptics
import com.lucent.app.ui.copyPlainText
import com.lucent.app.ui.frostedGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    models: List<String>,
    onModelsChange: (List<String>) -> Unit,
    customModelInput: String,
    onCustomModelInputChange: (String) -> Unit,
    loading: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    errorText: String,
    onErrorTextChange: (String) -> Unit,
    onRequestDeleteProfile: (Int) -> Unit,
    onSelectProfile: (Int) -> Unit,
    onReorderProfiles: (List<ApiProfile>, Int, (Result<Unit>) -> Unit) -> Unit,
    onAddProfile: () -> Unit,
    onSaveProfile: () -> Unit,
    onRequestTransfer: (() -> Unit) -> Unit,
    onImportProfiles: (List<ApiProfile>, ApiProfileImportMode) -> Unit
) {
    val context = LocalContext.current
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
    var testModel by remember(requestTarget.profileId) { mutableStateOf(models.firstOrNull().orEmpty()) }
    val latestTestModel = rememberUpdatedState(testModel)
    val choices = remember(fetchedModels, models) { modelChoices(fetchedModels, models) }
    val dragState = remember { mutableStateOf<ProfileDrag?>(null) }

    fun commitAndSaveProfile() {
        val input = customModelInput.trim()
        val finalModels = if (input.isNotBlank() && input !in models) models + input else models
        if (input.isNotBlank()) {
            onCustomModelInputChange("")
        }
        onModelsChange(finalModels)
        onSaveProfile()
    }

    LaunchedEffect(models) {
        if (testModel.isNotBlank() && testModel !in models) {
            testModel = models.firstOrNull().orEmpty()
        }
    }

    LaunchedEffect(requestTarget, testModel) {
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
        onReorderProfiles = onReorderProfiles, onAddProfile = onAddProfile,
        headerActions = {
            ApiProfileTransferActions(
                profiles = profiles,
                active = active,
                onRequestTransfer = onRequestTransfer,
                onApply = onImportProfiles
            )
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    Column(modifier = Modifier.fillMaxWidth().frostedGlass().padding(12.dp)) {
        ApiProfileFieldsSection(
            editingProfileName = editingProfileName,
            onEditingProfileNameChange = onEditingProfileNameChange,
            key = key,
            onKeyChange = onKeyChange,
            keyVisible = keyVisible,
            onRevealKey = onRevealKey,
            spec = spec,
            onSpecChange = onSpecChange,
            url = url,
            onUrlChange = onUrlChange
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

            ApiProfileSaveButton(
                dragState = dragState,
                onClick = { commitAndSaveProfile() }
            )
        }

        ApiModelsSection(
            models = models,
            customModelInput = customModelInput,
            onModelsChange = onModelsChange,
            onCustomModelInputChange = onCustomModelInputChange
        )

        Spacer(modifier = Modifier.height(10.dp))

        fun copyRawResponse(text: String) {
            if (text.isNotBlank()) {
                copyPlainText(context, text)
                Haptics.tick(context)
                copiedRawHint = true
                scope.launch {
                    delay(2000)
                    copiedRawHint = false
                }
            }
        }

        ApiConnectionDiagnosticsSection(
            active = active,
            testingConn = testingConn,
            testStatusText = testStatusText,
            testSuccess = testSuccess,
            lastTestResult = lastTestResult,
            fetchModelsError = fetchModelsError,
            errorText = errorText,
            copiedRawHint = copiedRawHint,
            testModels = models,
            testModel = testModel,
            onTestModelChange = { testModel = it },
            onTest = test@{
                if (url.trim().isEmpty()) {
                    onErrorTextChange(S.apiUrlRequired)
                    return@test
                }
                testStatusText = ""
                testSuccess = false
                lastTestResult = null
                fetchModelsError = ""
                onErrorTextChange("")
                val target = requestTarget
                val targetModel = testModel
                testRequests.start(
                    scope = scope,
                    isTargetCurrent = {
                        latestRequestTarget.value == target && latestTestModel.value == targetModel
                    },
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
            copyRawResponse = ::copyRawResponse
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    GlobalUserAgentSection(repo = repo)

    ApiModelsPickerDialog(
        pickerOpen = pickerOpen,
        choices = choices,
        models = models,
        onModelsChange = onModelsChange,
        onApplied = {},
        onDismiss = { pickerOpen = false }
    )
}
