package com.lucent.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.ApiProfile
import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
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
    fun pickingAModelReportsTheSelectionBeforeClosingTheSheet() {
        val events = mutableListOf<String>()
        rule.setContent {
            ProfileModelPickerSheet(
                profiles = listOf(profile),
                currentProfileId = profile.id,
                currentModel = "模型一",
                onSelect = { selectedProfile, model -> events += "select:${selectedProfile.id}:$model" },
                onDismiss = { events += "dismiss" },
                onManageApi = {}
            )
        }

        rule.onNodeWithText("模型一").assertIsSelected()
        rule.onNodeWithText("模型二").performScrollTo().assertIsNotSelected().performClick()
        rule.waitUntil(5_000) { events.size == 2 }
        rule.runOnIdle { assertEquals(listOf("select:profile-a:模型二", "dismiss"), events) }
    }

    @Test
    fun clickingTheCurrentModelStillReportsItAndCloses() {
        val events = mutableListOf<String>()
        rule.setContent {
            ProfileModelPickerSheet(
                profiles = listOf(profile),
                currentProfileId = profile.id,
                currentModel = "模型一",
                onSelect = { selectedProfile, model -> events += "select:${selectedProfile.id}:$model" },
                onDismiss = { events += "dismiss" },
                onManageApi = {}
            )
        }

        rule.onNodeWithText("模型一").assertIsSelected().performClick()
        rule.waitUntil(5_000) { events.size == 2 }
        rule.runOnIdle { assertEquals(listOf("select:profile-a:模型一", "dismiss"), events) }
    }

    @Test
    fun expandingAndCollapsingGroupsNeverSelectsAModelOrDismisses() {
        val second = profile.copy(id = "profile-b", name = "方案 B", selectedModels = listOf("模型三"))
        val events = mutableListOf<String>()
        rule.setContent {
            ProfileModelPickerSheet(
                profiles = listOf(profile, second),
                currentProfileId = "",
                currentModel = "",
                onSelect = { _, _ -> events += "select" },
                onDismiss = { events += "dismiss" },
                onManageApi = {}
            )
        }

        rule.onNodeWithText("模型一").assertDoesNotExist()
        rule.onNodeWithText("方案 A").performClick()
        rule.onNodeWithText("模型一").assertIsDisplayed()
        rule.onNodeWithText("方案 A").performClick()
        rule.onNodeWithText("模型一").assertDoesNotExist()
        rule.onNodeWithText("方案 A").performClick()
        rule.onNodeWithText("方案 B").performScrollTo().performClick()
        rule.onNodeWithText("模型一").assertDoesNotExist()
        rule.onNodeWithText("模型三").assertIsDisplayed()
        rule.onNodeWithText("方案 B").performClick()
        rule.onNodeWithText("模型三").assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }
    }

    @Test
    fun managingApiDoesNotSelectOrDismissAndRestoresTheExpandedGroup() {
        val visible = mutableStateOf(true)
        val events = mutableListOf<String>()
        rule.setContent {
            ProfileModelPickerSheet(
                profiles = listOf(profile),
                currentProfileId = "",
                currentModel = "",
                visible = visible.value,
                onSelect = { _, _ -> events += "select" },
                onDismiss = { events += "dismiss" },
                onManageApi = { events += "manage" }
            )
        }

        rule.onNodeWithText("方案 A").performClick()
        rule.onNodeWithText(S.pickerManageApi).performClick()
        rule.onNodeWithText("模型二").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(listOf("manage"), events)
            visible.value = false
        }
        rule.onNodeWithText(S.pickerTitle).assertDoesNotExist()
        rule.runOnIdle { visible.value = true }
        rule.onNodeWithText("模型二").assertIsDisplayed()
        rule.runOnIdle { assertEquals(listOf("manage"), events) }
    }

    @Test
    fun emptyProfileCannotExpandButManagementRemainsAvailable() {
        val events = mutableListOf<String>()
        rule.setContent {
            ProfileModelPickerSheet(
                profiles = listOf(ApiProfile(id = "empty", name = "空方案")),
                currentProfileId = "empty",
                currentModel = "",
                onSelect = { _, _ -> events += "select" },
                onDismiss = { events += "dismiss" },
                onManageApi = { events += "manage" }
            )
        }

        rule.onNodeWithText("空方案").assertIsNotEnabled()
        rule.onNodeWithText(S.pickerManageApi).performClick()
        rule.runOnIdle { assertEquals(listOf("manage"), events) }
    }

    @Test
    fun noProfilesShowsEmptyStateAndManagementStillDoesNotDismiss() {
        val events = mutableListOf<String>()
        rule.setContent {
            ProfileModelPickerSheet(
                profiles = emptyList(),
                currentProfileId = "",
                currentModel = "",
                onSelect = { _, _ -> events += "select" },
                onDismiss = { events += "dismiss" },
                onManageApi = { events += "manage" }
            )
        }

        rule.onNodeWithText(S.pickerEmpty).assertIsDisplayed()
        rule.onNodeWithText(S.pickerManageApi).performClick()
        rule.onNodeWithText(S.pickerEmpty).assertIsDisplayed()
        rule.runOnIdle { assertEquals(listOf("manage"), events) }
    }
}
