package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lucent.app.AppNavigation
import com.lucent.app.BackClaim
import com.lucent.app.data.SettingsRepository
import com.lucent.app.ui.settings.ApiConfigSettingsPage
import com.lucent.app.ui.settings.BackgroundSettingsPage
import com.lucent.app.ui.settings.FontSettingsPage
import com.lucent.app.ui.settings.RootSettingsPage
import com.lucent.app.ui.settings.ThemeSettingsPage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(active: Boolean = true) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val apiState = com.lucent.app.ui.settings.rememberApiSettingsState(repo)
    val profiles = apiState.profiles
    val selectedProfileIdx = apiState.selectedProfileIdx
    val draft = apiState.draft
    var editingProfileName by draft.name
    var url by draft.url
    var spec by draft.spec
    var key by draft.key
    var selectedModel by draft.selectedModel
    var models by draft.models

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

    BackHandler(enabled = active && route != SettingsRoute.Root) { goBack() }
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

    val motionEnabled = LocalBackgroundEnvironment.current.motionEnabled
    val enterProgress = remember { Animatable(1f) }
    var displayedRoute by remember { mutableStateOf(route) }
    LaunchedEffect(route, motionEnabled) {
        if (route != displayedRoute) {
            displayedRoute = route
            if (motionEnabled) {
                enterProgress.snapTo(0f)
                enterProgress.animateTo(1f, tween(180))
            } else enterProgress.snapTo(1f)
        } else if (!motionEnabled) enterProgress.snapTo(1f)
    }
    val imeVisible = WindowInsets.isImeVisible
    var viewportBounds by remember { mutableStateOf<Rect?>(null) }
    CompositionLocalProvider(LocalLiveGlassEnabled provides false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .graphicsLayer { alpha = 0.85f + enterProgress.value * 0.15f }
                .onGloballyPositioned { viewportBounds = it.boundsInRoot() }
                .verticalScroll(rootScroll)
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
                    scrollState = rootScroll,
                    viewportBounds = viewportBounds,
                    active = active,
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
                    loading = apiState.loading,
                    onLoadingChange = { apiState.loading = it },
                    errorText = apiState.errorText,
                    onErrorTextChange = { apiState.errorText = it },
                    onRequestDeleteProfile = { apiState.deleteProfile(it) },
                    onSelectProfile = { apiState.selectProfile(it) },
                    onReorderProfiles = { order, selected, complete -> apiState.reorderProfiles(order, selected, complete) },
                    onAddProfile = { apiState.addProfile() },
                    onSaveProfile = { apiState.saveActiveProfile() }
                )
            }
        }
    }
}
