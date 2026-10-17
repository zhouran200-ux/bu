package com.lucent.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.data.ApiProfileSnapshot
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.SettingsCache
import com.lucent.app.data.SettingsRepository
import com.lucent.app.i18n.S
import com.lucent.app.ui.LucentToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal object ApiSettingsCallbacks {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

@Composable
internal fun rememberApiSettingsState(repo: SettingsRepository): ApiSettingsState {
    val app = LocalContext.current.applicationContext
    val owner = remember(repo, app) {
        val initial = SettingsCache.snapshot()
        val profiles = ApiProfiles.parseStored(initial.apiProfilesJson) ?: ApiProfiles.DEFAULT_PROFILES
        ApiSettingsState(
            initial = ApiProfileSnapshot(profiles, profiles.getOrNull(initial.apiProfileSelected)?.id),
            queue = ApiProfileCommandQueue.application,
            callbackScope = ApiSettingsCallbacks.scope,
            persist = { repo.mutateApiProfiles(it) },
            notify = { LucentToast.show(app, it) },
            savedMessage = { S.apiSavedToast }
        )
    }
    LaunchedEffect(repo) {
        try {
            repo.ensureApiProfilesInitialized()
            repo.apiProfilePreferences.collect { saved ->
                val profiles = ApiProfiles.parseStored(saved.json) ?: ApiProfiles.DEFAULT_PROFILES
                owner.acceptCommitted(ApiProfileSnapshot(profiles, profiles.getOrNull(saved.selectedIndex)?.id))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            owner.errorText = "读取方案失败，请重新打开设置"
        }
    }
    return owner
}
