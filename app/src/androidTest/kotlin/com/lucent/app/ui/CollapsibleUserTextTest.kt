package com.lucent.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollapsibleUserTextTest {
    @get:Rule val rule = createComposeRule()

    private val text = mutableStateOf("Selectable " + "word ".repeat(18) + "Hiddenword")
    private val expanded = mutableStateOf(false)
    private val width = mutableStateOf(120.dp)
    private val fontScale = mutableStateOf(1f)
    private val callbacks = mutableListOf<Boolean>()
    private val selectionHandles = SemanticsMatcher("Compose selection handle") { node ->
        node.config.any { (key, _) -> key.name == "SelectionHandleInfo" }
    }
    private val clipboard: ClipboardManager
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Test fun shortTextAndExactlySixLinesHaveNoToggle() {
        text.value = "Short text"
        render()
        assertNoToggle()
        assertEquals(1, layout().lineCount)

        rule.runOnIdle { text.value = (1..6).joinToString("\n") { "Line $it" } }
        assertEquals(6, layout().lineCount)
        assertFalse(layout().didOverflowHeight)
        assertNoToggle()
    }

    @Test fun seventhLineFoldsWhilePreservingTheFullText() {
        text.value = (1..7).joinToString("\n") { "Line $it" }
        render()
        waitForToggle(S.userMessageExpand)
        assertEquals(6, layout().lineCount)
        assertTrue(layout().didOverflowHeight)
        assertEquals(text.value, layout().layoutInput.text.text)

        rule.onNodeWithText(S.userMessageExpand).performClick()
        waitForToggle(S.userMessageCollapse)
        assertEquals(7, layout().lineCount)
        assertFalse(layout().didOverflowHeight)
        assertEquals(listOf(true), callbacks)
    }

    @Test fun naturalWrappingFoldsWithoutExplicitNewlinesAndCanCollapseAgain() {
        assertFalse(text.value.contains('\n'))
        render()
        waitForToggle(S.userMessageExpand)
        assertEquals(6, layout().lineCount)
        assertTrue(layout().didOverflowHeight)

        rule.onNodeWithText(S.userMessageExpand).assertHasClickAction().performClick()
        waitForToggle(S.userMessageCollapse)
        assertTrue(layout().lineCount > 6)
        assertFalse(layout().didOverflowHeight)

        rule.onNodeWithText(S.userMessageCollapse).assertHasClickAction().performClick()
        waitForToggle(S.userMessageExpand)
        assertEquals(6, layout().lineCount)
        assertEquals(listOf(true, false), callbacks)
    }

    @Test fun expansionChangesOnlyWhenTheOwnerUpdatesTheValue() {
        render(updateExpanded = false)
        waitForToggle(S.userMessageExpand)
        rule.onNodeWithText(S.userMessageExpand).performClick()
        rule.runOnIdle {
            assertEquals(listOf(true), callbacks)
            assertFalse(expanded.value)
        }
        assertEquals(6, layout().lineCount)
        waitForToggle(S.userMessageExpand)

        rule.runOnIdle { expanded.value = true }
        waitForToggle(S.userMessageCollapse)
        assertTrue(layout().lineCount > 6)
    }

    @Test fun widthAndFontScaleChangesRecalculateNaturalWrapping() {
        text.value = "word ".repeat(14).trimEnd()
        width.value = 240.dp
        render()
        assertNoToggle()
        assertTrue(layout().lineCount <= 6)

        rule.runOnIdle { width.value = 90.dp }
        waitForToggle(S.userMessageExpand)
        assertTrue(layout().didOverflowHeight)

        rule.runOnIdle { width.value = 240.dp }
        assertNoToggle()
        rule.runOnIdle { fontScale.value = 3f }
        waitForToggle(S.userMessageExpand)
        assertTrue(layout().didOverflowHeight)

        rule.runOnIdle { fontScale.value = 1f }
        assertNoToggle()
    }

    @Test fun replacingLongTextWithShortTextRemovesTheToggleEvenWhenExpanded() {
        render()
        waitForToggle(S.userMessageExpand)
        rule.onNodeWithText(S.userMessageExpand).performClick()
        waitForToggle(S.userMessageCollapse)

        rule.runOnIdle { text.value = "Edited" }
        assertNoToggle()
        assertEquals(1, layout().lineCount)
        rule.runOnIdle { assertTrue(expanded.value) }

        rule.runOnIdle { text.value = "word ".repeat(20) }
        waitForToggle(S.userMessageCollapse)
        assertTrue(layout().lineCount > 6)
    }

    @Test fun collapsedTextRemainsSelectableAndExpandedTailCanBeCopied() {
        render()
        waitForToggle(S.userMessageExpand)
        rule.runOnIdle { clipboard.clearPrimaryClip() }
        selectAndCopy("Selectable")
        rule.runOnIdle { assertEquals(emptyList<Boolean>(), callbacks) }

        rule.onNodeWithText(S.userMessageExpand).performClick()
        waitForToggle(S.userMessageCollapse)
        assertEquals(text.value, layout().layoutInput.text.text)
        selectAndCopy("Hiddenword")
        rule.runOnIdle { assertEquals(listOf(true), callbacks) }
    }

    private fun render(updateExpanded: Boolean = true) {
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale.value),
                    LocalTextStyle provides TextStyle(fontFamily = FontFamily.Monospace)
                ) {
                    CollapsibleUserText(
                        text = text.value,
                        expanded = expanded.value,
                        onExpandedChange = {
                            callbacks += it
                            if (updateExpanded) expanded.value = it
                        },
                        modifier = Modifier.width(width.value)
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun layout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text.value)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(layouts))
            }
        return layouts.single()
    }

    private fun waitForToggle(label: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(label).fetchSemanticsNodes().size == 1
        }
    }

    private fun assertNoToggle() {
        rule.waitForIdle()
        rule.onNodeWithText(S.userMessageExpand).assertDoesNotExist()
        rule.onNodeWithText(S.userMessageCollapse).assertDoesNotExist()
    }

    private fun selectAndCopy(word: String) {
        val position = layout().getBoundingBox(text.value.indexOf(word) + 3).center
        rule.onNodeWithText(text.value).performTouchInput { longClick(position) }
        rule.waitUntil(5_000) {
            rule.onAllNodes(selectionHandles, useUnmergedTree = true)
                .fetchSemanticsNodes().size == 2
        }
        rule.onNodeWithText(text.value).performKeyInput { pressKey(Key.Copy) }
        rule.waitUntil(5_000) {
            rule.runOnIdle {
                clipboard.primaryClip?.getItemAt(0)?.text?.toString() == word
            }
        }
    }
}
