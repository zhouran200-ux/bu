package com.lucent.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles
import com.lucent.app.data.ApiProfileImportMode
import com.lucent.app.data.planApiProfileImport
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
    private val persistAll: suspend (List<ApiProfile>, Int) -> Unit,
    private val notify: (String) -> Unit,
    private val savedMessage: () -> String
) {
    private var committed by mutableStateOf(initial)
    private val drafts = ApiProfileDraftStore()
    val profiles: List<ApiProfile> get() = committed.profiles
    val selectedProfileIdx: Int get() = committed.selectedIndex.coerceIn(0, (profiles.size - 1).coerceAtLeast(0))
    val draft: ApiProfileDraft get() = drafts.forProfile(profiles.getOrNull(selectedProfileIdx))
    var saving by mutableStateOf(false)
        private set
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

    fun importProfiles(incoming: List<ApiProfile>, mode: ApiProfileImportMode) {
        if (saving) return
        if (hasUnsavedChanges()) {
            notify(S.apiUnsavedBody)
            return
        }
        val plan = planApiProfileImport(profiles, committed.selectedId, incoming, mode)
        if (mode == ApiProfileImportMode.Merge && plan.added == 0) {
            notify(S.apiTransferNothing)
            return
        }
        saving = true
        val index = plan.profiles.indexOfFirst { it.id == plan.selectedId }.coerceAtLeast(0)
        queue.submit({ persistAll(plan.profiles, index) }, { outcome ->
            callbackScope.launch {
                saving = false
                if (outcome.isSuccess) {
                    committed = ApiProfileSnapshot(plan.profiles, plan.selectedId)
                    drafts.clear()
                    notify(S.apiTransferApplied(plan.added, plan.skipped))
                } else {
                    notify(S.apiSaveFailed)
                }
            }
        })
    }

    fun saveActiveProfile(complete: (Result<Unit>) -> Unit = {}) {
        if (saving) return
        val index = selectedProfileIdx
        val profile = profiles.getOrNull(index)
        if (profile == null) {
            notify(S.apiListChanged)
            complete(Result.failure(IllegalStateException(S.apiListChanged)))
            return
        }
        val editing = drafts.forProfile(profile)
        val customInput = editing.customModelInput.value.trim()
        val models = editing.models.value
            .let { if (customInput.isNotBlank() && customInput !in it) it + customInput else it }
        val updated = profile.copy(
            name = editing.name.value.trim().ifBlank { S.apiDefaultName(index + 1) },
            spec = editing.spec.value, baseUrl = editing.url.value.trim(), apiKey = editing.key.value.trim(),
            selectedModels = models
        )
        val duplicate = updated.apiKey.takeIf { it.isNotEmpty() }?.let { key ->
            profiles.firstOrNull { it.id != updated.id && it.apiKey.trim() == key }
        }
        if (duplicate != null) {
            val duplicateName = duplicate.name.ifBlank {
                S.apiProfileDefaultName(profiles.indexOfFirst { it.id == duplicate.id } + 1)
            }
            val message = S.apiDuplicateKey(duplicateName)
            notify(message)
            complete(Result.failure(IllegalStateException(message)))
            return
        }
        editing.models.value = models
        editing.customModelInput.value = ""
        saving = true
        submit(ApiProfileMutation.Update(updated)) { outcome ->
            saving = false
            if (outcome.isSuccess) {
                committed = committed.copy(profiles = committed.profiles.map {
                    if (it.id == updated.id) updated else it
                })
            }
            notify(if (outcome.isSuccess) savedMessage() else S.apiSaveFailed)
            complete(outcome)
        }
    }

    fun hasUnsavedChanges(): Boolean {
        val index = selectedProfileIdx
        val profile = profiles.getOrNull(index) ?: return false
        val editing = drafts.forProfile(profile)
        val customInput = editing.customModelInput.value.trim()
        val models = editing.models.value
            .let { if (customInput.isNotBlank() && customInput !in it) it + customInput else it }
        val name = editing.name.value.trim().ifBlank { S.apiDefaultName(index + 1) }
        return customInput.isNotEmpty() ||
            name != profile.name ||
            editing.spec.value != profile.spec ||
            editing.url.value.trim() != profile.baseUrl.trim() ||
            editing.key.value.trim() != profile.apiKey.trim() ||
            models != profile.selectedModels
    }

    fun discardActiveDraft() {
        val profile = profiles.getOrNull(selectedProfileIdx) ?: return
        drafts.discardUnpersisted(profile.id)
    }
}
