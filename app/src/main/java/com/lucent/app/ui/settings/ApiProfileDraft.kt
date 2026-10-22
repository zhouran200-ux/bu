package com.lucent.app.ui.settings

import androidx.compose.runtime.mutableStateOf
import com.lucent.app.data.ApiProfile

internal class ApiProfileDraftStore {
    private val drafts = mutableMapOf<String?, ApiProfileDraft>()

    fun forProfile(profile: ApiProfile?): ApiProfileDraft =
        drafts.getOrPut(profile?.id) { ApiProfileDraft(profile) }

    fun removeAfterSuccessfulDelete(id: String) { drafts.remove(id) }

    fun discardUnpersisted(id: String) {
        drafts.remove(id)
    }

    fun clear() {
        drafts.clear()
    }
}

internal class ApiProfileDraft(profile: ApiProfile?) {
    val name = mutableStateOf(profile?.name.orEmpty())
    val url = mutableStateOf(profile?.baseUrl.orEmpty())
    val spec = mutableStateOf(profile?.spec ?: "openai")
    val key = mutableStateOf(profile?.apiKey.orEmpty())
    val selectedModel = mutableStateOf(profile?.model.orEmpty())
    val models = mutableStateOf(profile?.selectedModels ?: emptyList())
}
