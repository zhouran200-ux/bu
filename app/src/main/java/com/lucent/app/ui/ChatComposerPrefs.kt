package com.lucent.app.ui

import android.content.SharedPreferences
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ReasoningPreset

internal fun saveReasoningSelection(
    prefs: SharedPreferences,
    notebookId: Long,
    preset: ReasoningPreset
) {
    prefs.edit()
        .putString("reasoning_preset_$notebookId", preset.key)
        .putString("reasoning_$notebookId", preset.key)
        .apply()
}

internal fun saveDialogueSettings(
    prefs: SharedPreferences,
    notebookId: Long,
    prompt: String,
    rounds: Int
) {
    prefs.edit()
        .putString("prompt_$notebookId", prompt)
        .putInt("history_rounds_$notebookId", rounds)
        .remove("temperature_$notebookId")
        .apply()
}

internal fun saveProfileSelection(
    prefs: SharedPreferences,
    notebookId: Long,
    profile: ApiProfile,
    model: String
) {
    prefs.edit()
        .putString("profile_id_$notebookId", profile.id)
        .putString("profile_name_$notebookId", profile.name)
        .putString("model_$notebookId", model)
        .apply()
}
