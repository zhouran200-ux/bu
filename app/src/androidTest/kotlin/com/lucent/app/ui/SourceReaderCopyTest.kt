package com.lucent.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.Note
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceReaderCopyTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val documentBody = listOf("# Chapter", "", "Selectable paragraph.", "", "Second paragraph.")
        .joinToString(System.lineSeparator())
    private val paragraph = "Selectable paragraph."
    private val selectionHandles = SemanticsMatcher("Compose selection handle") { node ->
        node.config.any { (key, _) -> key.name == "SelectionHandleInfo" }
    }
    private val clipboard: ClipboardManager
        get() = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Before
    fun prepareSystemWindow() {
        rule.prepareSystemInteraction(rule.activity)
    }

    @After
    fun clearSystemClipboard() {
        rule.runOnUiThread { clipboard.clearPrimaryClip() }
    }

    private fun render() {
        rule.setContent {
            MaterialTheme {
                SourceReader(
                    notebookId = 1L,
                    note = Note(id = 7L, title = "Document", body = documentBody),
                    focus = null,
                    active = false,
                    emphasizedFocus = null,
                    onEmphasize = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(paragraph).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clipboardText(): String? = clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.text?.toString()

    private fun waitForClipboard(expected: String) {
        rule.awaitSystemWindowFocus(rule.activity.window.decorView)
        rule.waitUntil(5_000) {
            rule.runOnIdle { clipboardText() == expected }
        }
        rule.runOnIdle { assertEquals(expected, clipboardText()) }
    }

    @Test
    fun longPressSelectsDocumentTextAndSystemCopyCopiesSelection() {
        render()
        val node = rule.onNodeWithText(paragraph)
        rule.awaitSystemWindowFocus(node)
        rule.runOnIdle { clipboard.clearPrimaryClip() }
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue(action(layouts))
        }
        val position = layouts.single().getBoundingBox(3).center
        node.performTouchInput { longClick(position) }
        rule.waitUntil(5_000) {
            rule.onAllNodes(selectionHandles, useUnmergedTree = true).fetchSemanticsNodes().size == 2
        }
        node.performKeyInput { pressKey(Key.Copy) }
        waitForClipboard("Selectable")
    }
}
