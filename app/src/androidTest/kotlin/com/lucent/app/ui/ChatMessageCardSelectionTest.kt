package com.lucent.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucent.app.chat.UiChatMessage
import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMessageCardSelectionTest {
    @get:Rule val rule = createComposeRule()

    private val callbacks = mutableListOf<String>()
    private val word = "Selectable"
    private val userBody = "Selectable\n\nSecond paragraph."
    private val aiBody = "Selectable\n\n**Second** paragraph."
    private val selectionHandles = SemanticsMatcher("Compose selection handle") { node ->
        node.config.any { (key, _) -> key.name == "SelectionHandleInfo" }
    }
    private val clipboard: ClipboardManager
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Test fun userLongPressCopiesSelectedWordAndActionsRemainIndependent() {
        render(ai = false, enabled = true)
        assertSelectionCopiesWord(userBody)
        assertIndependentActions(ai = false, bodyNode = userBody)
    }

    @Test fun aiMarkdownLongPressCopiesSelectedWordAndActionsRemainIndependent() {
        render(ai = true, enabled = true)
        waitForText("Second paragraph.")
        assertSelectionCopiesWord(word)
        assertIndependentActions(ai = true, bodyNode = word)
    }

    @Test fun disabledUserActionsIgnoreTouchesWhileFullCopyRemainsAvailable() {
        render(ai = false, enabled = false)
        assertDisabledActions(ai = false, bodyNode = userBody)
    }

    @Test fun disabledAiActionsIgnoreTouchesWhileFullCopyRemainsAvailable() {
        render(ai = true, enabled = false)
        waitForText("Second paragraph.")
        assertDisabledActions(ai = true, bodyNode = word)
    }

    private fun render(ai: Boolean, enabled: Boolean) {
        rule.setContent {
            MaterialTheme {
                if (ai) {
                    AiMessageCard(
                        message = UiChatMessage(id = 2, role = "assistant", content = aiBody),
                        readingKey = "selection-test",
                        actionsEnabled = enabled,
                        onRegenerate = { callbacks += "regenerate" },
                        onEditAnswer = { callbacks += "edit" },
                        onDelete = { callbacks += "delete" }
                    )
                } else {
                    UserMessageCard(
                        message = UiChatMessage(id = 1, role = "user", content = userBody),
                        actionsEnabled = enabled,
                        onEdit = { callbacks += "edit" },
                        onDelete = { callbacks += "delete" }
                    )
                }
            }
        }
        waitForText(if (ai) word else userBody)
        rule.runOnIdle { clipboard.clearPrimaryClip() }
    }

    private fun waitForText(text: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun selectWord(bodyNode: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        val body = rule.onNodeWithText(bodyNode)
        body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue(action(layouts))
        }
        val position = layouts.single().getBoundingBox(3).center
        body.performTouchInput { longClick(position) }
        rule.waitUntil(5_000) {
            rule.onAllNodes(selectionHandles, useUnmergedTree = true)
                .fetchSemanticsNodes().size == 2
        }
    }

    private fun assertSelectionCopiesWord(bodyNode: String) {
        selectWord(bodyNode)
        assertCallbacks(emptyList())
        rule.runOnIdle { assertEquals(null, clipboardText()) }
        rule.onNodeWithText(bodyNode).performKeyInput { pressKey(Key.Copy) }
        waitForClipboard(word)
        assertCallbacks(emptyList())
    }

    private fun assertIndependentActions(ai: Boolean, bodyNode: String) {
        val copyLabel = if (ai) S.msgCopy else S.msgCopyFull
        val expectedBody = if (ai) aiBody else userBody
        val expectedCallbacks = mutableListOf<String>()
        val actions = buildList {
            add(copyLabel to null)
            add(S.msgEdit to "edit")
            add(S.actionDelete to "delete")
            if (ai) add(S.msgRegenerate to "regenerate")
        }
        actions.forEach { (label, callback) ->
            selectWord(bodyNode)
            assertCallbacks(expectedCallbacks)
            val previousClipboard = rule.runOnIdle { clipboardText() }
            rule.onNodeWithContentDescription(label)
                .assertIsEnabled()
                .assertHasClickAction()
                .performTouchInput { click() }
            if (callback == null) {
                waitForClipboard(expectedBody)
            } else {
                expectedCallbacks += callback
                rule.runOnIdle { assertEquals(previousClipboard, clipboardText()) }
            }
            assertCallbacks(expectedCallbacks)
        }
    }

    private fun assertDisabledActions(ai: Boolean, bodyNode: String) {
        val disabledLabels = buildList {
            add(S.msgEdit)
            add(S.actionDelete)
            if (ai) add(S.msgRegenerate)
        }
        disabledLabels.forEach { label ->
            selectWord(bodyNode)
            rule.onNodeWithContentDescription(label)
                .assertIsNotEnabled()
                .performTouchInput { click() }
            assertCallbacks(emptyList())
            rule.runOnIdle { assertEquals(null, clipboardText()) }
        }
        selectWord(bodyNode)
        rule.onNodeWithContentDescription(if (ai) S.msgCopy else S.msgCopyFull)
            .assertIsEnabled()
            .performTouchInput { click() }
        waitForClipboard(if (ai) aiBody else userBody)
        assertCallbacks(emptyList())
    }

    private fun assertCallbacks(expected: List<String>) {
        rule.runOnIdle { assertEquals(expected, callbacks.toList()) }
    }

    private fun clipboardText(): String? = clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.text?.toString()

    private fun waitForClipboard(expected: String) {
        rule.waitUntil(5_000) {
            rule.runOnIdle { clipboardText() == expected }
        }
        rule.runOnIdle { assertEquals(expected, clipboardText()) }
    }
}
