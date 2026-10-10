package com.lucent.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.AppNavigation
import com.lucent.app.BackClaim
import com.lucent.app.AppScope
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.FontStore
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.data.StartupLog
import com.lucent.app.i18n.S
import com.lucent.app.ui.settings.ApiConfigSettingsPage
import com.lucent.app.ui.settings.AppearanceSettingsPage
import com.lucent.app.ui.settings.BackgroundSettingsPage
import com.lucent.app.ui.settings.EditorSettingsPage
import com.lucent.app.ui.settings.FontSettingsPage
import com.lucent.app.ui.settings.RootSettingsPage
import com.lucent.app.ui.settings.ThemeSettingsPage
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val WRONG_PASSWORD = "__wrong_password__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(active: Boolean = true) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val appVersionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            ?: com.lucent.app.LucentBuild.VERSION
    }
    val appBuildNumber = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString()
        }.getOrNull() ?: com.lucent.app.LucentBuild.BUILD_NUMBER
    }

    val appContext = context.applicationContext
    val savedFont by repo.font.collectAsState(initial = SettingsCache.font)
    val savedProfilesJson by repo.apiProfilesJson.collectAsState(initial = SettingsCache.apiProfilesJson)
    val savedSelectedIdx by repo.apiProfileSelected.collectAsState(initial = SettingsCache.apiProfileSelected)
    val profiles = remember(savedProfilesJson) {
        val parsed = com.lucent.app.data.ApiProfiles.parse(savedProfilesJson)
        if (parsed.none { it.name == "商汤" || it.baseUrl.contains("sensenova") }) {
            listOf(com.lucent.app.data.ApiProfiles.DEFAULT_PROFILES.first()) + parsed.filterNot { it.baseUrl.isBlank() && it.apiKey.isBlank() }
        } else {
            parsed
        }
    }
    val selectedProfileIdx = savedSelectedIdx.coerceIn(0, (profiles.size - 1).coerceAtLeast(0))

    LaunchedEffect(profiles) {
        val currentInDb = com.lucent.app.data.ApiProfiles.parse(savedProfilesJson)
        val hasSenseNova = currentInDb.any { it.name == "商汤" || it.baseUrl.contains("sensenova") }
        if (!hasSenseNova) {
            repo.saveApiProfiles(profiles, 0)
        }
    }

    val activeApiProfile = profiles.getOrNull(selectedProfileIdx)
    var editingProfileName by remember(activeApiProfile) { mutableStateOf(activeApiProfile?.name.orEmpty()) }
    var url by remember(activeApiProfile) { mutableStateOf(activeApiProfile?.baseUrl.orEmpty()) }
    var spec by remember(activeApiProfile) { mutableStateOf(activeApiProfile?.spec ?: "openai") }
    var key by remember(activeApiProfile) { mutableStateOf(activeApiProfile?.apiKey.orEmpty()) }
    var selectedModel by remember(activeApiProfile) { mutableStateOf(activeApiProfile?.model.orEmpty()) }
    var models by remember(activeApiProfile) { mutableStateOf(activeApiProfile?.selectedModels ?: emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    fun selectProfile(idx: Int) {
        val p = profiles.getOrNull(idx) ?: return
        url = p.baseUrl; spec = p.spec; key = p.apiKey; selectedModel = p.model
        editingProfileName = p.name; models = p.selectedModels
        AppScope.io.launch { repo.saveApiProfiles(profiles, idx) }
    }

    fun addProfile() {
        if (profiles.size >= com.lucent.app.data.ApiProfiles.MAX) return
        val defaultName = com.lucent.app.data.ApiProfiles.nextDefaultName(profiles)
        val newProfile = com.lucent.app.data.ApiProfile(name = defaultName)
        val newList = profiles + newProfile
        val newIdx = newList.size - 1
        url = ""; spec = "openai"; key = ""; selectedModel = ""; editingProfileName = defaultName
        models = emptyList()
        AppScope.io.launch { repo.saveApiProfiles(newList, newIdx) }
    }

    fun deleteProfile(idx: Int) {
        if (idx !in profiles.indices) return
        val newList = profiles.toMutableList().also { it.removeAt(idx) }
        val newSelected = if (newList.isEmpty()) 0 else selectedProfileIdx.coerceIn(0, newList.size - 1)
        val nextActive = newList.getOrNull(newSelected)
        url = nextActive?.baseUrl.orEmpty(); spec = nextActive?.spec ?: "openai"; key = nextActive?.apiKey.orEmpty()
        selectedModel = nextActive?.model.orEmpty(); editingProfileName = nextActive?.name.orEmpty()
        models = nextActive?.selectedModels ?: emptyList()
        AppScope.io.launch { repo.saveApiProfiles(newList, newSelected) }
    }

    fun reorderProfiles(order: List<com.lucent.app.data.ApiProfile>, selected: Int) {
        if (order.size != profiles.size) return
        AppScope.io.launch { repo.saveApiProfiles(order, selected) }
    }

    fun saveActiveProfile(idx: Int, selectedModels: List<String>) {
        val updated = profiles.toMutableList()
        val typedModel = selectedModel.trim()
        val mergedModels = if (typedModel.isNotBlank() && typedModel !in selectedModels) {
            selectedModels + typedModel
        } else {
            selectedModels
        }
        models = mergedModels
        val edited = com.lucent.app.data.ApiProfile(
            name = editingProfileName.trim().ifBlank { "方案 ${idx + 1}" },
            spec = spec,
            baseUrl = url.trim(),
            apiKey = key.trim(),
            model = typedModel,
            provider = com.lucent.app.data.ApiProviders.CUSTOM,
            selectedModels = mergedModels
        )
        if (idx in updated.indices) updated[idx] = edited else updated.add(edited)
        val newSelected = idx.coerceIn(0, updated.size - 1)
        AppScope.io.launch {
            repo.saveApiProfiles(updated, newSelected)
            withContext(Dispatchers.Main) { LucentToast.show(appContext, S.apiSavedToast) }
        }
    }

    var backupStatus by remember { mutableStateOf("") }

    val appLockOn = false
    val selfDestructOn = false
    val gateLockedOut = false
    val gateWiping = false

    fun settingsGateSuccess() {}
    fun chargeSettingsGate() {}

    @Composable
    fun SettingsGateFeedback() {}

    fun requireLockAuth(action: () -> Unit) {
        action()
    }

    var showOpenLinksWarning by remember { mutableStateOf(false) }
    LaunchedEffect(backupStatus) {
        if (backupStatus.isNotBlank()) {
            kotlinx.coroutines.delay(8000)
            backupStatus = ""
        }
    }

    val logsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(StartupLog.buildExport(context).toByteArray())
                        } != null
                    } catch (e: Exception) {
                        false
                    }
                }
                backupStatus = if (ok) S.logsExported else S.logsExportFailed
            }
        }
    }

    var fontRefresh by remember { mutableStateOf(0) }
    val importedFonts = remember(fontRefresh) { FontStore.index(context) }.slots
    val fontCanImportMore = importedFonts.size < FontStore.MAX_FONTS
    var fontImporting by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf("") }
    var fontPendingDelete by remember { mutableStateOf<FontStore.FontSlot?>(null) }
    var fontPendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var fontImportName by remember { mutableStateOf("") }

    val fontImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !fontImporting) {
            fontError = ""
            val picked = try {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (_: Throwable) { null }
            fontImportName = (picked ?: "").substringBeforeLast('.').take(60)
            fontPendingImportUri = uri
        }
    }

    fun startFontImport(uri: Uri, name: String) {
        if (fontImporting) return
        fontImporting = true
        fontError = ""
        scope.launch {
            var importedId: String? = null
            val error = withContext(Dispatchers.IO) {
                try {
                    importedId = FontStore.import(context, uri, name).id
                    null
                } catch (e: FontStore.TooManyFontsException) {
                    S.fontImportFailedTooMany(FontStore.MAX_FONTS)
                } catch (e: FontStore.NotFontException) {
                    S.fontImportFailedNotFont
                } catch (e: Exception) {
                    S.fontImportFailedGeneric(e.message ?: "")
                }
            }
            fontImporting = false
            if (error == null) {
                importedId?.let { id -> AppScope.io.launch { repo.setFont(id) } }
                fontRefresh++
                LucentToast.show(context.applicationContext, S.fontImportedToast)
            } else {
                fontError = error
            }
        }
    }

    fun deleteImportedFont(slot: FontStore.FontSlot) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (savedFont == slot.id) repo.setFont(SYSTEM_FONT_KEY)
                FontStore.delete(context, slot.id)
            }
            LucentFontResolver.evict(slot.id)
            fontRefresh++
        }
    }

    val route = AppNavigation.settingsRoute

    fun setRoute(next: SettingsRoute) {
        AppNavigation.rememberSettingsRoute(next)
    }

    fun navigate(next: SettingsRoute) {
        if (next == route) return
        setRoute(next)
    }

    fun goBack() {
        val parent = com.lucent.app.ui.SettingsTrail.parent(route) ?: SettingsRoute.Root
        navigate(parent)
    }

    SideEffect { com.lucent.app.SettingsNav.handler = ::navigate }
    DisposableEffect(Unit) { onDispose { com.lucent.app.SettingsNav.handler = null } }

    BackHandler(enabled = route != SettingsRoute.Root) { goBack() }
    BackClaim(active && route != SettingsRoute.Root)

    var manualReveal by remember { mutableStateOf(false) }
    var typingReveal by remember { mutableStateOf(false) }
    var keystrokeSeq by remember { mutableStateOf(0) }
    LaunchedEffect(manualReveal) {
        if (manualReveal) {
            delay(3000)
            manualReveal = false
        }
    }
    LaunchedEffect(keystrokeSeq) {
        if (keystrokeSeq > 0) {
            delay(1000)
            typingReveal = false
        }
    }
    val keyVisible = manualReveal || typingReveal



    @Composable
    @NonRestartableComposable
    fun OpenLinksWarningDialog() {
        AlertDialog(
            onDismissRequest = { showOpenLinksWarning = false },
            title = { Text(S.openLinksWarnTitle) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(S.openLinksWarnBody)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showOpenLinksWarning = false
                    scope.launch {
                        repo.setOpenLinksExternally(true)
                        SettingsCache.openLinksExternally = true
                    }
                }) { Text(S.turnOn) }
            },
            dismissButton = {
                TextButton(onClick = { showOpenLinksWarning = false }) { Text(S.actionCancel) }
            }
        )
    }
    if (showOpenLinksWarning) { OpenLinksWarningDialog() }

    @Composable
    @NonRestartableComposable
    fun DeleteImportedFontDialog(slot: FontStore.FontSlot) {
        AlertDialog(
            onDismissRequest = { fontPendingDelete = null },
            title = { Text(S.fontDeleteTitle) },
            text = { Text(S.fontDeleteBody(slot.name.ifBlank { slot.fileName })) },
            confirmButton = {
                TextButton(onClick = {
                    deleteImportedFont(slot)
                    fontPendingDelete = null
                }) { Text(S.actionDelete) }
            },
            dismissButton = { TextButton(onClick = { fontPendingDelete = null }) { Text(S.actionCancel) } }
        )
    }
    fontPendingDelete?.let { DeleteImportedFontDialog(it) }

    @Composable
    @NonRestartableComposable
    fun NameImportedFontDialog(uri: Uri) {
        AlertDialog(
            onDismissRequest = { fontPendingImportUri = null },
            title = { Text(S.fontNameTitle) },
            text = {
                Column {
                    Text(S.fontNameBody, color = onGradientMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fontImportName,
                        onValueChange = { fontImportName = it.take(60) },
                        label = { Text(S.fontNameField) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    fontPendingImportUri = null
                    startFontImport(uri, fontImportName)
                }) { Text(S.actionConfirm) }
            },
            dismissButton = { TextButton(onClick = { fontPendingImportUri = null }) { Text(S.actionCancel) } }
        )
    }
    fontPendingImportUri?.let { NameImportedFontDialog(it) }

    val routeScrolls = remember { mutableMapOf<SettingsRoute, ScrollState>() }
    val rootScroll = routeScrolls.getOrPut(route) { ScrollState(SettingsScrollMemory.of(route)) }
    LaunchedEffect(rootScroll) {
        snapshotFlow { rootScroll.value }.collect { SettingsScrollMemory.write(route, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rootScroll)
            .hazeSource(state = LocalHazeState.current)
            .padding(16.dp)
            .padding(bottom = LocalBottomBarInset.current)
    ) {
        when (route) {
            SettingsRoute.Root -> RootSettingsPage(onRoute = { navigate(it) })

            SettingsRoute.Appearance -> AppearanceSettingsPage(repo = repo, onRoute = { navigate(it) })

            SettingsRoute.Theme -> ThemeSettingsPage(repo = repo, onRoute = { navigate(it) })

            SettingsRoute.Background -> BackgroundSettingsPage(repo = repo, onRoute = { navigate(it) })

            SettingsRoute.Editor -> EditorSettingsPage(
                repo = repo,
                onRequestOpenLinksWarning = { showOpenLinksWarning = true },
                onRoute = { navigate(it) }
            )

            SettingsRoute.Font -> FontSettingsPage(
                repo = repo,
                onRoute = { navigate(it) }
            )

            SettingsRoute.Api -> ApiConfigSettingsPage(
                repo = repo,
                profiles = profiles,
                selectedProfileIdx = selectedProfileIdx,
                editingProfileName = editingProfileName,
                onEditingProfileNameChange = { editingProfileName = it },
                url = url,
                onUrlChange = { url = it },
                spec = spec,
                onSpecChange = { spec = it },
                key = key,
                onKeyChange = { key = it; typingReveal = true; keystrokeSeq++ },
                keyVisible = keyVisible,
                onRevealKey = { manualReveal = true },
                selectedModel = selectedModel,
                onSelectedModelChange = { selectedModel = it },
                models = models,
                onModelsChange = { picked -> models = picked },
                loading = loading,
                onLoadingChange = { loading = it },
                errorText = errorText,
                onErrorTextChange = { errorText = it },
                onRequestDeleteProfile = { deleteProfile(it) },
                onSelectProfile = { selectProfile(it) },
                onReorderProfiles = { order, selected -> reorderProfiles(order, selected) },
                onAddProfile = { addProfile() },
                onSaveProfile = { saveActiveProfile(selectedProfileIdx, models) },
                onRoute = { navigate(it) }
            )
        }
    }
}

