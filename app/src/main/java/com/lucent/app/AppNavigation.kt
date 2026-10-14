package com.lucent.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.ui.SettingsRoute

internal object SettingsNav {
    var handler: ((SettingsRoute) -> Unit)? = null

    fun go(route: SettingsRoute) {
        AppNavigation.rememberSettingsRoute(route)
        handler?.invoke(route)
    }
}

object AppNavigation {

    var requestedScreen by mutableStateOf<Screen?>(null)
        private set

    internal var settingsRoute by mutableStateOf(SettingsRoute.Root)
        private set

    var activeNotebookId by mutableStateOf<Long?>(null)

    var createNotebookRequested by mutableStateOf(false)
        private set

    fun requestCreateNotebook() {
        createNotebookRequested = true
        requestedScreen = Screen.Notebooks
    }

    fun consumeCreateNotebook(): Boolean {
        val value = createNotebookRequested
        createNotebookRequested = false
        return value
    }

    fun requestScreen(screen: Screen) {
        requestedScreen = screen
    }

    internal fun rememberSettingsRoute(route: SettingsRoute) {
        settingsRoute = route
    }

    internal fun resetSettingsRoute() {
        settingsRoute = SettingsRoute.Root
    }

    fun consumeScreen(): Screen? = requestedScreen.also { requestedScreen = null }

    private var backClaims by mutableStateOf(0)

    val innerBackActive: Boolean
        get() = backClaims > 0

    internal fun claimBack() {
        backClaims += 1
    }

    internal fun releaseBack() {
        backClaims = (backClaims - 1).coerceAtLeast(0)
    }
}

@Composable
fun BackClaim(active: Boolean) {
    DisposableEffect(active) {
        if (active) AppNavigation.claimBack()
        onDispose { if (active) AppNavigation.releaseBack() }
    }
}
