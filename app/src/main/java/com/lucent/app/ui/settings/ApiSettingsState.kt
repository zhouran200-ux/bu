package com.lucent.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.ApiProfileMutation
import com.lucent.app.i18n.S
import com.lucent.app.data.ApiProfileSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ApiSettingsState(
    initial: ApiProfileSnapshot,
    private val queue: ApiProfileCommandQueue,
    private val callbackScope: CoroutineScope,
    private val persist: suspend (ApiProfileMutation) -> Unit,
    private val notify: (String) -> Unit,
    private val savedMessage: () -> String
) {
    private var committed by mutableStateOf(initial)
    private val drafts = ApiProfileDraftStore()
    val profiles: List<ApiProfile> get() = committed.profiles
    val selectedProfileIdx: Int get() = committed.selectedIndex.coerceIn(0, (profiles.size - 1).coerceAtLeast(0))
    val draft: ApiProfileDraft get() = drafts.forProfile(profiles.getOrNull(selectedProfileIdx))
    var loading by mutableStateOf(false)
    var errorText by mutableStateOf("")

    fun acceptCommitted(snapshot: ApiProfileSnapshot) { committed = snapshot }

    private fun submit(command: ApiProfileMutation, complete: (Result<Unit>) -> Unit) {
        queue.submit({ persist(command) }, { outcome -> callbackScope.launch { complete(outcome) } })
    }

    fun selectProfile(index: Int) {
        val profile = profiles.getOrNull(index) ?: return
        drafts.forProfile(profile)
        submit(ApiProfileMutation.Select(profile.id)) {
            if (it.isFailure) notify(S.apiSwitchNotSaved)
        }
    }

    fun addProfile() {
        if (profiles.size >= ApiProfiles.MAX) return
        val profile = ApiProfile(name = ApiProfiles.nextDefaultName(profiles))
        drafts.forProfile(profile)
        submit(ApiProfileMutation.Add(profile)) {
            if (it.isFailure) {
                drafts.discardUnpersisted(profile.id)
                notify(S.apiAddNotSaved)
            }
        }
    }

    fun deleteProfile(index: Int) {
        val id = profiles.getOrNull(index)?.id ?: return
        submit(ApiProfileMutation.Delete(id)) {
            if (it.isSuccess) drafts.removeAfterSuccessfulDelete(id)
            else notify(S.apiDeleteNotSaved)
        }
    }

    fun reorderProfiles(order: List<ApiProfile>, selected: Int, complete: (Result<Unit>) -> Unit) {
        if (order.map { it.id }.toSet() != profiles.map { it.id }.toSet() || order.size != profiles.size) {
            complete(Result.failure(IllegalStateException(S.apiListChanged)))
            return
        }
        submit(ApiProfileMutation.Reorder(order.map { it.id }, order.getOrNull(selected)?.id), complete)
    }

    fun saveActiveProfile() {
        val index = selectedProfileIdx
        val profile = profiles.getOrNull(index) ?: return
        val editing = drafts.forProfile(profile)
        val model = editing.selectedModel.value.trim()
        val models = editing.models.value.let { if (model.isNotBlank() && model !in it) it + model else it }
        editing.models.value = models
        val updated = profile.copy(
            name = editing.name.value.trim().ifBlank { S.apiDefaultName(index + 1) },
            spec = editing.spec.value, baseUrl = editing.url.value.trim(), apiKey = editing.key.value.trim(),
            model = model, selectedModels = models
        )
        submit(ApiProfileMutation.Update(updated)) {
            notify(if (it.isSuccess) savedMessage() else S.apiSaveFailed)
        }
    }
}
