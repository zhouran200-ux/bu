package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomGlassComposerInteractionTest {
    @get:Rule
    val rule = createComposeRule()

    private class Host {
        val input = mutableStateOf("")
        val menu = mutableStateOf(false)
        val preset = mutableStateOf(ReasoningPreset.AUTO)
        val streaming = mutableStateOf(false)
        val profileName = mutableStateOf("")
        val menuChanges = mutableListOf<Boolean>()
        val selections = mutableListOf<ReasoningPreset>()
        val selectionMenuStates = mutableListOf<Boolean>()
        var history = 0
        var settings = 0
        var model = 0
        var sends = 0
        var stops = 0
    }

    private fun mount(host: Host) {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    BottomGlassComposer(
                        inputText = host.input.value,
                        onInputChange = { host.input.value = it },
                        showReasoningMenu = host.menu.value,
                        onReasoningMenuChange = {
                            host.menuChanges += it
                            host.menu.value = it
                        },
                        onReasoningButtonBounds = {},
                        reasoningPreset = host.preset.value,
                        onSelectReasoning = {
                            host.selections += it
                            host.selectionMenuStates += host.menu.value
                            host.preset.value = it
                        },
                        onOpenHistory = { host.history++ },
                        onOpenDialogueSettings = { host.settings++ },
                        selectedProfileName = host.profileName.value,
                        onOpenModelPicker = { host.model++ },
                        streaming = host.streaming.value,
                        onSend = { host.sends++ },
                        onStop = { host.stops++ }
                    )
                }
            }
        }
    }

    @Test
    fun reasoningButtonTogglesMenuWithoutSelectingPreset() {
        val host = Host()
        mount(host)
        rule.onNodeWithText(S.composerReasoningTitle).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.composerReasoning).performClick()
        rule.onNodeWithText(S.composerReasoningTitle).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.composerReasoning).performClick()
        rule.onNodeWithText(S.composerReasoningTitle).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(listOf(true, false), host.menuChanges)
            assertEquals(emptyList<ReasoningPreset>(), host.selections)
        }
    }

    @Test
    fun everyPresetReportsTypedSelectionBeforeClosingPopup() {
        val host = Host()
        mount(host)
        ReasoningPreset.entries.forEach { preset ->
            rule.onNodeWithContentDescription(S.composerReasoning).performClick()
            rule.onNodeWithText(preset.label).performScrollTo().performClick()
            rule.onNodeWithText(S.composerReasoningTitle).assertDoesNotExist()
            rule.runOnIdle {
                assertEquals(preset, host.preset.value)
                assertEquals(false, host.menu.value)
            }
        }
        rule.runOnIdle {
            assertEquals(ReasoningPreset.entries.toList(), host.selections)
            assertEquals(List(ReasoningPreset.entries.size) { true }, host.selectionMenuStates)
            assertEquals(ReasoningPreset.entries.flatMap { listOf(true, false) }, host.menuChanges)
        }
    }

    @Test
    fun inputRetainsFocusAcrossNonFocusablePopupOpenAndTouchSelection() {
        val host = Host()
        mount(host)
        val input = rule.onNodeWithTag("bottom_glass_composer_input")
        input.performTouchInput { click() }
        input.performTextReplacement("draft before popup")
        input.assertIsFocused()
        rule.onNodeWithContentDescription(S.composerReasoning).performTouchInput { click() }
        rule.onNodeWithText(S.composerReasoningTitle).assertIsDisplayed()
        input.assertIsFocused()
        rule.onNodeWithText(ReasoningPreset.DEEP.label).performScrollTo()
            .performTouchInput { click() }
        rule.onNodeWithText(S.composerReasoningTitle).assertDoesNotExist()
        input.assertIsFocused().assertTextEquals("draft before popup")
        input.performTextReplacement("continue editing")
        rule.runOnIdle {
            assertEquals("continue editing", host.input.value)
            assertEquals(listOf(ReasoningPreset.DEEP), host.selections)
        }
    }

    @Test
    fun historySettingsAndModelEntrypointsDispatchOnlyTheirOwnCallbacks() {
        val host = Host()
        mount(host)
        rule.onNodeWithText(S.composerPickProfile).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.composerHistory).performClick()
        rule.runOnIdle {
            assertEquals(1, host.history)
            assertEquals(0, host.settings)
            assertEquals(0, host.model)
        }
        rule.onNodeWithContentDescription(S.composerSettings).performClick()
        rule.runOnIdle {
            assertEquals(1, host.history)
            assertEquals(1, host.settings)
            assertEquals(0, host.model)
        }
        rule.onNodeWithContentDescription(S.composerExpandModels).performClick()
        rule.runOnIdle { host.profileName.value = "Selected profile" }
        rule.onNodeWithText("Selected profile").assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertEquals(1, host.history)
            assertEquals(1, host.settings)
            assertEquals(2, host.model)
            assertEquals(0, host.sends)
            assertEquals(0, host.stops)
            assertEquals(emptyList<Boolean>(), host.menuChanges)
        }
    }

    @Test
    fun blankDraftDisablesSendAndStreamingAlwaysDispatchesStopInstead() {
        val host = Host()
        mount(host)
        rule.onNodeWithContentDescription(S.composerSend).assertIsNotEnabled()
        rule.onNodeWithTag("bottom_glass_composer_input").performTextReplacement("   ")
        rule.onNodeWithContentDescription(S.composerSend).assertIsNotEnabled()
        rule.runOnIdle {
            assertEquals(0, host.sends)
            assertEquals(0, host.stops)
        }
        rule.onNodeWithTag("bottom_glass_composer_input").performTextReplacement("send this")
        rule.onNodeWithContentDescription(S.composerSend).performClick()
        rule.runOnIdle {
            assertEquals(1, host.sends)
            assertEquals(0, host.stops)
            host.streaming.value = true
        }
        rule.onNodeWithContentDescription(S.composerSend).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.composerStop).performClick()
        rule.runOnIdle {
            assertEquals(1, host.sends)
            assertEquals(1, host.stops)
            host.input.value = ""
        }
        rule.onNodeWithContentDescription(S.composerStop).performClick()
        rule.runOnIdle {
            assertEquals(1, host.sends)
            assertEquals(2, host.stops)
            host.streaming.value = false
        }
        rule.onNodeWithContentDescription(S.composerStop).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.composerSend).assertIsNotEnabled()
    }
}
