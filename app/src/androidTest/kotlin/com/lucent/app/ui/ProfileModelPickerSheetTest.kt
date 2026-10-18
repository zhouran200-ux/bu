package com.lucent.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.ApiProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileModelPickerSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val profile = ApiProfile(
        name = "方案 A",
        selectedModels = listOf("模型一", "模型二"),
        id = "profile-a"
    )

    @Test
    fun pickingAModelReportsTheSelectionAndClosesTheSheet() {
        var picked: Pair<String, String>? = null
        var dismissed = false

        rule.setContent {
            ProfileModelPickerSheet(
                profiles = listOf(profile),
                currentProfileId = "profile-a",
                currentModel = "模型一",
                onSelect = { selectedProfile, model -> picked = selectedProfile.id to model },
                onDismiss = { dismissed = true },
                onManageApi = {}
            )
        }

        rule.onNodeWithText("模型二").performClick()

        assertEquals("profile-a" to "模型二", picked)
        rule.waitUntil(timeoutMillis = 5_000) { dismissed }
        assertTrue(dismissed)
    }
}
