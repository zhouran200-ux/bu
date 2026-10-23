package com.lucent.app.ui

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucent.app.data.ApiProfile
import com.lucent.app.data.ReasoningPreset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookPreferenceMemoryTest {
    @Test fun notebookChoicesSurviveNewPreferencesHandleWithoutCrossNotebookLeak() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "notebook-memory-test-" + System.nanoTime()
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            val first = ApiProfile(name = "First", model = "model-a", id = "profile-a")
            val second = ApiProfile(name = "Second", model = "model-b", id = "profile-b")
            saveReasoningSelection(prefs, 1L, ReasoningPreset.DEEP)
            saveDialogueSettings(prefs, 1L, "Notebook one instructions", 18)
            saveProfileSelection(prefs, 1L, first, first.model)
            saveReasoningSelection(prefs, 2L, ReasoningPreset.FAST)
            saveDialogueSettings(prefs, 2L, "Notebook two instructions", 4)
            saveProfileSelection(prefs, 2L, second, second.model)
            val reopened = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            assertEquals("deep", reopened.getString("reasoning_preset_1", null))
            assertEquals("fast", reopened.getString("reasoning_preset_2", null))
            assertEquals("Notebook one instructions", reopened.getString("prompt_1", null))
            assertEquals("Notebook two instructions", reopened.getString("prompt_2", null))
            assertEquals(18, reopened.getInt("history_rounds_1", 0))
            assertEquals(4, reopened.getInt("history_rounds_2", 0))
            assertEquals("profile-a", reopened.getString("profile_id_1", null))
            assertEquals("model-a", reopened.getString("model_1", null))
            assertEquals("profile-b", reopened.getString("profile_id_2", null))
            assertEquals("model-b", reopened.getString("model_2", null))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
