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
        handler?.invoke(route)
    }
}

object AppNavigation {

    var requestedScreen by mutableStateOf<Screen?>(null)
        private set

    var pendingNoteId by mutableStateOf<Long?>(null)
        private set

    var returnScreen by mutableStateOf<Screen?>(null)
        private set

    internal var settingsRoute by mutableStateOf(SettingsRoute.Root)
        private set

    var composeNoteRequested by mutableStateOf(false)
        private set

    var requestedNotebookId by mutableStateOf<Long?>(null)
        private set

    var activeNotebookId by mutableStateOf<Long?>(null)

    var createNotebookRequested by mutableStateOf(false)
        private set

    var pendingEditNoteId by mutableStateOf<Long?>(null)
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

    fun openNote(id: Long, from: Screen? = null) {
        pendingNoteId = id
        returnScreen = from
        requestedScreen = Screen.Notebooks
    }

    fun requestComposeNote() {
        composeNoteRequested = true
        requestedScreen = Screen.Notebooks
    }

    fun requestScreen(screen: Screen) {
        requestedScreen = screen
    }

    fun openNotebook(id: Long) {
        requestedNotebookId = id
        requestedScreen = Screen.Notebooks
    }

    fun consumeNotebookId(): Long? = requestedNotebookId.also { requestedNotebookId = null }

    fun editNote(id: Long) {
        pendingEditNoteId = id
        requestedScreen = Screen.Notebooks
    }

    internal fun rememberSettingsRoute(route: SettingsRoute) {
        settingsRoute = route
    }

    internal fun resetSettingsRoute() {
        settingsRoute = SettingsRoute.Root
    }

    fun consumeScreen(): Screen? = requestedScreen.also { requestedScreen = null }

    fun consumeNoteId(): Long? = pendingNoteId.also { pendingNoteId = null }

    fun consumeReturnScreen(): Screen? = returnScreen.also { returnScreen = null }

    fun consumeComposeNote(): Boolean = composeNoteRequested.also { composeNoteRequested = false }

    fun consumeEditNoteId(): Long? = pendingEditNoteId.also { pendingEditNoteId = null }

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
