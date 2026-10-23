package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucent.app.data.Note
import com.lucent.app.i18n.S
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceIndexPreviewTest {
    @get:Rule val rule = createComposeRule()
    private val notebookId = -System.nanoTime()
    private fun paragraph(chapter: Int) = "Paragraph $chapter. " + "Reading text. ".repeat(100)
    private val documentBody = (1..40).joinToString("\n\n") { "# Chapter $it\n\n" + paragraph(it) }

    @After fun clearProgress() {
        rule.runOnIdle {
            ReadingPositionMemory.clearNotebook(InstrumentationRegistry.getInstrumentation().targetContext, notebookId)
        }
    }

    private fun render() {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    Text("Notebook chrome")
                    SourceReader(
                        notebookId = notebookId,
                        note = Note(id = 7L, title = "Document", body = documentBody),
                        focus = null,
                        active = true,
                        emphasizedFocus = null,
                        onEmphasize = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription(S.sourceIndexPreviewOpen).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    @Test fun entrySlidesToInlineTaggedPageAndReturnsToReader() {
        render()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.waitForIdle()
        rule.onAllNodes(isDialog()).assertCountEquals(0)
        rule.onNodeWithText("Notebook chrome").assertIsDisplayed()
        rule.onNodeWithTag("source_model_page").assertIsDisplayed()
        rule.onNodeWithText(S.sourceIndexPreviewTitle).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewBack).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("source_reader_page").assertIsDisplayed()
        rule.onNodeWithTag("source_model_page").assertDoesNotExist()
    }

    @Test fun systemBackReturnsToDocumentWithoutLeavingNotebook() {
        render()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
        rule.onNodeWithTag("source_reader_page").assertIsDisplayed()
        rule.onNodeWithText("Notebook chrome").assertIsDisplayed()
        rule.onNodeWithTag("source_model_page").assertDoesNotExist()
    }

    @Test fun pageSwitchesCarryReadingProgressInBothDirections() {
        render()
        rule.onNodeWithTag("source_reader_list").performScrollToIndex(21)
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("〔S：11-1〕", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("source_model_list").performScrollToIndex(41)
        rule.onNodeWithContentDescription(S.sourceIndexPreviewBack).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(paragraph(21), substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("〔S：21-1〕", substring = true).assertIsDisplayed()
    }
}
