package com.lucent.app.ui

import android.view.KeyEvent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogueSettingsSheetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun editingPromptAndRoundsDoesNotSaveUntilSaveIsClicked() {
        val saved = mutableListOf<Pair<String, Int>>()
        var dismissed = 0
        rule.setContent {
            DialogueSettingsSheet("原始提示", 10, { prompt, rounds -> saved += prompt to rounds }, { dismissed++ })
        }

        rule.onNodeWithContentDescription(S.dialogueSystemPrompt).assertTextEquals("原始提示")
            .performTextReplacement("  新提示  ")
        rule.onNodeWithText(S.dialogueCharCount(7)).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.sliderIncrease(S.dialogueHistoryRounds))
            .performScrollTo().performClick()
        rule.onNodeWithText(S.dialogueRoundsValue(11)).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.sliderDecrease(S.dialogueHistoryRounds)).performClick()
        rule.onNodeWithText(S.dialogueRoundsDefault).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.sliderDecrease(S.dialogueHistoryRounds)).performClick()
        rule.runOnIdle {
            assertEquals(emptyList<Pair<String, Int>>(), saved)
            assertEquals(0, dismissed)
        }
        rule.onNodeWithText(S.userAgentSaveButton).performClick()
        rule.runOnIdle {
            assertEquals(listOf("新提示" to 9), saved)
            assertEquals(0, dismissed)
        }
        rule.onNodeWithText(S.dialogueTitle).assertIsDisplayed()
    }

    @Test
    fun restoringDefaultsOnlyChangesDraftUntilExplicitSave() {
        val saved = mutableListOf<Pair<String, Int>>()
        var dismissed = 0
        rule.setContent {
            DialogueSettingsSheet("自定义提示", 3, { prompt, rounds -> saved += prompt to rounds }, { dismissed++ })
        }

        rule.onNodeWithText(S.resetTypographyDefaults).performScrollTo().performClick()
        rule.onNodeWithContentDescription(S.dialogueSystemPrompt)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithText(S.dialoguePromptHint).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(S.dialogueRoundsDefault).performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(emptyList<Pair<String, Int>>(), saved)
            assertEquals(0, dismissed)
        }
        rule.onNodeWithText(S.userAgentSaveButton).performClick()
        rule.runOnIdle {
            assertEquals(listOf("" to 10), saved)
            assertEquals(0, dismissed)
        }
    }

    @Test
    fun cancellingEditedDraftOrRestoredDefaultsNeverSavesAndReopensOriginalValues() {
        val open = mutableStateOf(true)
        val saved = mutableListOf<Pair<String, Int>>()
        var dismissed = 0
        var clearFocus: () -> Unit = {}
        rule.setContent {
            val focusManager = LocalFocusManager.current
            clearFocus = { focusManager.clearFocus(force = true) }
            Text("dialogue settings host")
            if (open.value) {
                DialogueSettingsSheet(
                    initialPrompt = "保留提示",
                    initialHistoryRounds = 4,
                    onSave = { prompt, rounds -> saved += prompt to rounds },
                    onDismiss = { dismissed++; open.value = false }
                )
            }
        }

        rule.onNodeWithContentDescription(S.dialogueSystemPrompt).performTextReplacement("不保存的编辑")
        rule.onNodeWithContentDescription(S.sliderIncrease(S.dialogueHistoryRounds))
            .performScrollTo().performClick()
        rule.runOnIdle { clearFocus() }
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { dismissed == 1 }
        rule.onNodeWithText(S.dialogueTitle).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(emptyList<Pair<String, Int>>(), saved)
            open.value = true
        }
        rule.onNodeWithContentDescription(S.dialogueSystemPrompt).assertTextEquals("保留提示")
        rule.onNodeWithText(S.dialogueRoundsValue(4)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(S.resetTypographyDefaults).performScrollTo().performClick()
        rule.runOnIdle { clearFocus() }
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { dismissed == 2 }
        rule.runOnIdle {
            assertEquals(emptyList<Pair<String, Int>>(), saved)
            open.value = true
        }
        rule.onNodeWithContentDescription(S.dialogueSystemPrompt).assertTextEquals("保留提示")
        rule.onNodeWithText(S.dialogueRoundsValue(4)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun historyRoundButtonsRespectBothBoundsAndSaveTheChosenLimit() {
        val saved = mutableListOf<Pair<String, Int>>()
        rule.setContent {
            DialogueSettingsSheet("", 1, { prompt, rounds -> saved += prompt to rounds }, {})
        }

        rule.onNodeWithContentDescription(S.sliderDecrease(S.dialogueHistoryRounds))
            .performScrollTo().assertIsNotEnabled()
        repeat(19) {
            rule.onNodeWithContentDescription(S.sliderIncrease(S.dialogueHistoryRounds)).performClick()
        }
        rule.onNodeWithText(S.dialogueRoundsValue(20)).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.sliderIncrease(S.dialogueHistoryRounds)).assertIsNotEnabled()
        rule.onNodeWithText(S.userAgentSaveButton).performClick()
        rule.runOnIdle { assertEquals(listOf("" to 20), saved) }
    }
}
