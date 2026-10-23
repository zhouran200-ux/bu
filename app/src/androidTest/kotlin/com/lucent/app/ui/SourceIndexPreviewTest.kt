package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.Modifier
import com.lucent.app.data.SourceTagging
import org.junit.Assert.assertEquals
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertTrue
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
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            ReadingPositionMemory.clearNotebook(InstrumentationRegistry.getInstrumentation().targetContext, notebookId)
        }
    }

    private val visible = mutableStateOf(true)
    private val focus = mutableStateOf<SourceFocus?>(null)
    private val revision get() = SourceTagging.revisionOf(documentBody, "Document")

    private fun assertPosition(tag: String, index: Int, key: String) {
        rule.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(
            SourceReadingPositionKey, ReadingPosition(key, index, 0).encode()
        ))
    }

    private fun render() {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    Text("Notebook chrome")
                    if (visible.value) SourceReader(
                        notebookId = notebookId,
                        note = Note(id = 7L, title = "Document", body = documentBody),
                        focus = focus.value,
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

    @Test fun differentTextHeightsKeepTheSameAnchorWithoutReusingPixelOffsets() {
        render()
        rule.onNodeWithTag("source_reader_list").performScrollToIndex(21)
        rule.onNodeWithTag("source_reader_list").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 120f) }
        rule.waitForIdle()
        val readerPosition = ReadingPosition.decode(rule.onNodeWithTag("source_reader_list")
            .fetchSemanticsNode().config[SourceReadingPositionKey])!!
        assertEquals("slice:S11-1", readerPosition.itemKey)
        assertTrue(readerPosition.offset > 0)
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.waitForIdle()
        assertPosition("source_model_list", 21, "slice:S11-1")
        rule.onNodeWithText("〔S：11-1〕", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("source_model_list").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 120f) }
        rule.waitForIdle()
        val rawPosition = ReadingPosition.decode(rule.onNodeWithTag("source_model_list")
            .fetchSemanticsNode().config[SourceReadingPositionKey])!!
        assertEquals("slice:S11-1", rawPosition.itemKey)
        assertTrue(rawPosition.offset > 0)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
        assertPosition("source_reader_list", 21, "slice:S11-1")
        rule.onNodeWithText(paragraph(11), substring = true).assertIsDisplayed()
    }

    @Test fun rawProgressIsSavedWhenReaderLeavesComposition() {
        render()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.onNodeWithTag("source_model_list").performScrollToIndex(41)
        assertPosition("source_model_list", 41, "slice:S21-1")
        rule.runOnIdle { visible.value = false }
        rule.runOnIdle {
            val saved = ReadingPositionMemory.read(
                InstrumentationRegistry.getInstrumentation().targetContext,
                "source:$notebookId:7:slices:$revision"
            )
            assertEquals(ReadingPosition("slice:S21-1", 41, 0), saved)
            visible.value = true
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription(S.sourceIndexPreviewOpen).fetchSemanticsNodes().isNotEmpty()
        }
        assertPosition("source_reader_list", 41, "slice:S21-1")
        rule.onNodeWithText(paragraph(21), substring = true).assertIsDisplayed()
    }

    @Test fun citationFromRawPageOverridesRawProgress() {
        render()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.onNodeWithTag("source_model_list").performScrollToIndex(41)
        rule.runOnIdle { focus.value = SourceFocus(7L, "S11-1", 1L, revision) }
        rule.waitForIdle()
        assertPosition("source_reader_list", 21, "slice:S11-1")
        rule.onNodeWithText(paragraph(11), substring = true).assertIsDisplayed()
        rule.onNodeWithTag("source_model_page").assertDoesNotExist()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.waitForIdle()
        assertPosition("source_model_list", 21, "slice:S11-1")
        rule.onNodeWithText("〔S：11-1〕", substring = true).assertIsDisplayed()
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
        assertPosition("source_reader_list", 21, "slice:S11-1")
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.waitForIdle()
        assertPosition("source_model_list", 21, "slice:S11-1")
        rule.onNodeWithText("〔S：11-1〕", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("source_model_list").performScrollToIndex(41)
        rule.onNodeWithContentDescription(S.sourceIndexPreviewBack).performClick()
        rule.waitForIdle()
        assertPosition("source_reader_list", 41, "slice:S21-1")
        rule.onNodeWithText(paragraph(21), substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("〔S：21-1〕", substring = true).assertIsDisplayed()
    }
}
