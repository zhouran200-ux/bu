package com.lucent.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookMessageUiStateComposeTest {
    @get:Rule val rule = createComposeRule()

    private val notebookOpen = mutableStateOf(true)
    private val showChat = mutableStateOf(true)
    private val text = (1..7).joinToString("\n") { "Line $it" }
    private lateinit var owner: NotebookMessageUiState
    private val itemCompositions = mutableListOf<Any>()
    private var itemDisposals = 0

    @Test fun expandedMessageSurvivesLazyItemDisposalAndRecreation() {
        render()
        expand()
        val originalOwner = owner
        val originalItem = rule.runOnIdle { itemCompositions.single() }

        rule.onNodeWithTag("messages").performScrollToIndex(60)
        rule.waitUntil(5_000) { rule.runOnIdle { itemDisposals > 0 } }
        rule.onNodeWithText(text).assertDoesNotExist()
        rule.runOnIdle { assertTrue(owner.userExpanded(42)) }

        rule.onNodeWithTag("messages").performScrollToIndex(0)
        waitForToggle(S.userMessageCollapse)
        rule.runOnIdle {
            assertSame(originalOwner, owner)
            assertTrue(itemCompositions.size >= 2)
            assertNotSame(originalItem, itemCompositions.last())
        }
        assertEquals(7, lineCount())
    }

    @Test fun removingChatContentForSourcesKeepsTheNotebookOwnerExpansion() {
        render()
        expand()
        val originalOwner = owner
        rule.runOnIdle { showChat.value = false }
        rule.waitForIdle()
        rule.onNodeWithText(text).assertDoesNotExist()
        rule.runOnIdle {
            assertTrue(itemDisposals > 0)
            assertTrue(owner.userExpanded(42))
            showChat.value = true
        }

        waitForToggle(S.userMessageCollapse)
        rule.runOnIdle { assertSame(originalOwner, owner) }
        assertEquals(7, lineCount())
    }

    @Test fun leavingAndReenteringNotebookCreatesCollapsedOwner() {
        render()
        expand()
        val previousOwner = owner
        rule.runOnIdle { notebookOpen.value = false }
        rule.waitForIdle()
        rule.onNodeWithText(text).assertDoesNotExist()
        rule.runOnIdle { notebookOpen.value = true }

        waitForToggle(S.userMessageExpand)
        rule.runOnIdle {
            assertNotSame(previousOwner, owner)
            assertTrue(previousOwner.userExpanded(42))
            assertFalse(owner.userExpanded(42))
        }
        assertEquals(6, lineCount())
    }

    @Test fun explicitNotebookExitResetCollapsesTheVisibleMessage() {
        render()
        expand()
        rule.runOnIdle { owner.resetUserMessages() }

        waitForToggle(S.userMessageExpand)
        rule.runOnIdle { assertFalse(owner.userExpanded(42)) }
        assertEquals(6, lineCount())
    }

    private fun render() {
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    if (notebookOpen.value) {
                        val visitOwner = remember { NotebookMessageUiState() }
                        SideEffect { owner = visitOwner }
                        CompositionLocalProvider(LocalNotebookMessageUiState provides visitOwner) {
                            if (showChat.value) {
                                LazyColumn(Modifier.width(180.dp).height(260.dp).testTag("messages")) {
                                    item(key = "message:42") {
                                        val messageOwner = checkNotNull(LocalNotebookMessageUiState.current)
                                        val itemIdentity = remember { Any() }
                                        DisposableEffect(itemIdentity) {
                                            itemCompositions += itemIdentity
                                            onDispose { itemDisposals++ }
                                        }
                                        CollapsibleUserText(
                                            text = text,
                                            expanded = messageOwner.userExpanded(42),
                                            onExpandedChange = { messageOwner.setUserExpanded(42, it) }
                                        )
                                    }
                                    items(count = 80, key = { "spacer:$it" }) {
                                        Spacer(Modifier.height(100.dp))
                                    }
                                }
                            } else {
                                Text("Sources")
                            }
                        }
                    }
                }
            }
        }
        waitForToggle(S.userMessageExpand)
    }

    private fun expand() {
        rule.onNodeWithText(S.userMessageExpand).performClick()
        waitForToggle(S.userMessageCollapse)
        rule.runOnIdle { assertTrue(owner.userExpanded(42)) }
    }

    private fun waitForToggle(label: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(label).fetchSemanticsNodes().size == 1
        }
    }

    private fun lineCount(): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(layouts))
            }
        return layouts.single().lineCount
    }
}
