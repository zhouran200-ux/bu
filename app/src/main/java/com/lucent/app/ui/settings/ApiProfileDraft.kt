package com.lucent.app.ui.settings

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.lucent.app.data.ApiProfile

internal class ApiProfileDraftStore {
    private val drafts = mutableMapOf<String?, ApiProfileDraft>()
    private val revision = mutableIntStateOf(0)

    fun forProfile(profile: ApiProfile?): ApiProfileDraft {
        revision.intValue
        return drafts.getOrPut(profile?.id) { ApiProfileDraft(profile) }
    }

    fun removeAfterSuccessfulDelete(id: String) {
        drafts.remove(id)
        revision.intValue++
    }

    fun discardUnpersisted(id: String) {
        drafts.remove(id)
        revision.intValue++
    }

    fun clear() {
        drafts.clear()
        revision.intValue++
    }
}

internal class ApiProfileDraft(profile: ApiProfile?) {
    val name = mutableStateOf(profile?.name.orEmpty())
    val url = mutableStateOf(profile?.baseUrl.orEmpty())
    val spec = mutableStateOf(profile?.spec ?: "openai")
    val key = mutableStateOf(profile?.apiKey.orEmpty())
    val models = mutableStateOf(profile?.selectedModels ?: emptyList())
    val customModelInput = mutableStateOf("")
}
