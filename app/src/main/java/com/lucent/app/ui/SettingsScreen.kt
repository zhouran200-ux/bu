package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lucent.app.AppNavigation
import com.lucent.app.BackClaim
import com.lucent.app.AppScope
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.settings.ApiConfigSettingsPage
import com.lucent.app.ui.settings.BackgroundSettingsPage
import com.lucent.app.ui.settings.FontSettingsPage
import com.lucent.app.ui.settings.RootSettingsPage
import com.lucent.app.ui.settings.ThemeSettingsPage
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(active: Boolean = true) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }

    val appContext = context.applicationContext
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
            id = profiles.getOrNull(idx)?.id ?: java.util.UUID.randomUUID().toString(),
            spec = spec,
            baseUrl = url.trim(),
            apiKey = key.trim(),
            model = typedModel,
            selectedModels = mergedModels
        )
        if (idx in updated.indices) updated[idx] = edited else updated.add(edited)
        val newSelected = idx.coerceIn(0, updated.size - 1)
        AppScope.io.launch {
            repo.saveApiProfiles(updated, newSelected)
            withContext(Dispatchers.Main) { LucentToast.show(appContext, S.apiSavedToast) }
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

    val routeScrolls = remember { mutableMapOf<SettingsRoute, ScrollState>() }
    val rootScroll = routeScrolls.getOrPut(route) { ScrollState(SettingsScrollMemory.of(route)) }
    LaunchedEffect(rootScroll) {
        snapshotFlow { rootScroll.value }.collect { SettingsScrollMemory.write(route, it) }
    }

    val imeVisible = WindowInsets.isImeVisible
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rootScroll)
            .hazeSource(state = LocalHazeState.current)
            .padding(16.dp)
            .padding(bottom = if (imeVisible) 0.dp else LocalBottomBarInset.current)
    ) {
        when (route) {
            SettingsRoute.Root -> RootSettingsPage(repo = repo, onRoute = { navigate(it) })

            SettingsRoute.Theme -> ThemeSettingsPage(repo = repo)

            SettingsRoute.Background -> BackgroundSettingsPage(repo = repo)

            SettingsRoute.Font -> FontSettingsPage(repo = repo)

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
                onSaveProfile = { saveActiveProfile(selectedProfileIdx, models) }
            )
        }
    }
}

