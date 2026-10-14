package com.lucent.app.ui

import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ApiProfiles

internal data class ModelPickerPosition(val expandedProfileId: String?, val firstVisibleItemIndex: Int)

internal fun modelPickerPosition(
    profiles: List<ApiProfile>,
    selectedProfileId: String,
    selectedModel: String
): ModelPickerPosition {
    val profileIndex = profiles.indexOfFirst { it.id == selectedProfileId }
    if (profileIndex < 0) return ModelPickerPosition(null, 0)
    val profile = profiles[profileIndex]
    val models = ApiProfiles.availableModels(profile)
    if (models.isEmpty()) return ModelPickerPosition(null, profileIndex + 1)
    val modelIndex = models.indexOf(selectedModel).coerceAtLeast(0)
    return ModelPickerPosition(profile.id, profileIndex + 2 + modelIndex)
}
