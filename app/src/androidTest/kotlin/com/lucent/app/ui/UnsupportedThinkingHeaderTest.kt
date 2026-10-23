package com.lucent.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucent.app.chat.UiChatMessage
import com.lucent.app.i18n.S
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnsupportedThinkingHeaderTest {
    @get:Rule val rule = createComposeRule()

    private class Host(reasoning: String? = null, savedExpanded: Boolean? = null) {
        val notebookId = -System.nanoTime()
        val readingKey = "chat:$notebookId:thought:31"
        val mounted = mutableStateOf(true)
        val streaming = mutableStateOf(false)
        val liveThought = mutableStateOf("")
        val message = mutableStateOf(
            UiChatMessage(31, "assistant", "", reasoning, answerSeconds = 4)
        )
        val owner = NotebookMessageUiState()
        val lane = owner.thinking(readingKey) {
            val saved = savedExpanded?.let {
                ReadingPosition(if (it) "expanded" else "collapsed", 0, 0)
            }
            ThinkingLaneState(saved, streaming = false)
        }
        var following = true
        val expandedBeforeToggle = mutableListOf<Boolean>()
    }

    private var mountedHost: Host? = null

    @After fun discardReadingPosition() {
        val host = mountedHost ?: return
        rule.runOnUiThread { host.mounted.value = false }
        advanceFrames()
        rule.runOnUiThread {
            ReadingPositionMemory.clearNotebook(
                InstrumentationRegistry.getInstrumentation().targetContext, host.notebookId
            )
        }
    }

    @Test fun emptySlotWithoutReasoningKeepsItsHeaderThroughStreamingAndCompletion() {
        val host = mount()
        assertUnsupported()
        rule.runOnUiThread { assertFalse(host.lane.expanded) }

        rule.runOnUiThread {
            host.streaming.value = true
            host.message.value = host.message.value.copy(content = "Answer is arriving")
        }
        settleAnimation()
        laneNode().assertIsDisplayed()
        rule.onNodeWithText(S.thinkingStreaming).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.thinkingExpand).assertExists()
        thoughtNode().assertDoesNotExist()
        rule.runOnUiThread { assertFalse(host.lane.expanded) }

        rule.runOnUiThread {
            host.message.value = host.message.value.copy(content = "Completed answer", reasoningText = "")
            host.streaming.value = false
        }
        advanceFrames()
        laneNode().assertIsDisplayed()
        rule.onNodeWithText(S.thinkingUnsupported).assertIsDisplayed()
        settleAnimation()
        repeat(3) {
            assertUnsupported()
            advanceFrames()
        }
        rule.runOnUiThread {
            assertFalse(host.lane.expanded)
            assertTrue(host.expandedBeforeToggle.isEmpty())
        }
    }

    @Test fun historicalNullEmptyAndWhitespaceReasoningShowOnlyTheUnsupportedTitle() {
        val host = mount(Host(savedExpanded = true).apply {
            message.value = message.value.copy(content = "Historical answer")
        })
        for (reasoning in listOf(null, "", "  \n\t ")) {
            rule.runOnUiThread { host.message.value = host.message.value.copy(reasoningText = reasoning) }
            advanceFrames()
            assertUnsupported()
        }
        rule.runOnUiThread {
            assertTrue(host.lane.expanded)
            assertTrue(host.following)
            assertTrue(host.expandedBeforeToggle.isEmpty())
        }
    }

    @Test fun historicalReasoningKeepsItsTitleAndCanExpandAndCollapseWithTheCardCallback() {
        val reasoning = "Historical reasoning remains available"
        val host = mount(Host(reasoning).apply {
            message.value = message.value.copy(content = "Historical answer")
        })
        rule.onNodeWithText(S.thinkingDoneSeconds(4)).assertIsDisplayed()
        rule.onNodeWithText(S.thinkingUnsupported).assertDoesNotExist()
        thoughtNode().assertDoesNotExist()
        rule.onNodeWithContentDescription(S.thinkingExpand).performClick()
        settleAnimation()
        thoughtNode().assertTextEquals(reasoning)
        rule.onNodeWithContentDescription(S.thinkingCollapse).assertExists()
        rule.runOnUiThread {
            assertTrue(host.lane.expanded)
            assertFalse(host.following)
            assertEquals(listOf(false), host.expandedBeforeToggle)
        }

        rule.onNodeWithContentDescription(S.thinkingCollapse).performClick()
        settleAnimation()
        thoughtNode().assertDoesNotExist()
        rule.onNodeWithText(S.thinkingDoneSeconds(4)).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.thinkingExpand).assertExists()
        rule.runOnUiThread {
            assertFalse(host.lane.expanded)
            assertEquals(listOf(false, true), host.expandedBeforeToggle)
        }
    }

    @Test fun liveReasoningExpandsOnlyAfterTheHeaderIsTapped() {
        val host = mount()
        assertUnsupported()
        val reasoning = "A short live reasoning trace"
        rule.runOnUiThread {
            host.streaming.value = true
            host.liveThought.value = reasoning
            host.message.value = host.message.value.copy(content = "Live answer")
        }
        settleAnimation()
        rule.onNodeWithText(S.thinkingStreaming).assertIsDisplayed()
        thoughtNode().assertDoesNotExist()
        rule.runOnUiThread { assertFalse(host.lane.expanded) }

        rule.onNodeWithContentDescription(S.thinkingExpand).performClick()
        settleAnimation()
        thoughtNode().assertTextEquals(reasoning)
        rule.onNodeWithText(S.thinkingWaiting).assertDoesNotExist()
        rule.runOnUiThread {
            assertTrue(host.lane.expanded)
            assertFalse(host.following)
            assertEquals(listOf(false), host.expandedBeforeToggle)
        }

        rule.runOnUiThread {
            host.message.value = host.message.value.copy(content = "Final answer", reasoningText = reasoning)
            host.streaming.value = false
        }
        advanceFrames()
        repeat(3) {
            laneNode().assertIsDisplayed()
            thoughtNode().assertTextEquals(reasoning)
            rule.onNodeWithText(S.thinkingDoneSeconds(4)).assertIsDisplayed()
            rule.onNodeWithContentDescription(S.thinkingCollapse).assertExists()
            rule.onNodeWithText(S.thinkingUnsupported).assertDoesNotExist()
            advanceFrames()
        }
    }

    private fun mount(host: Host = Host()): Host {
        mountedHost = host
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    if (host.mounted.value) {
                        LazyColumn(Modifier.width(300.dp).height(360.dp)) {
                            item(key = "header") { Spacer(Modifier.height(8.dp)) }
                            item(key = "message:31") {
                                AiMessageCard(
                                    message = host.message.value,
                                    readingKey = host.readingKey,
                                    streaming = host.streaming.value,
                                    liveThought = if (host.streaming.value) host.liveThought else null,
                                    thinkingState = rememberThinkingLaneState(
                                        host.readingKey, host.streaming.value, host.owner
                                    ),
                                    onThinkingToggle = {
                                        host.following = false
                                        host.expandedBeforeToggle += host.lane.expanded
                                    }
                                )
                            }
                            item(key = "footer") { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }
        advanceFrames()
        return host
    }

    private fun assertUnsupported() {
        laneNode().assertIsDisplayed()
        val title = rule.onNodeWithText(S.thinkingUnsupported, useUnmergedTree = true)
        title.assertIsDisplayed().assertHasNoClickAction()
        rule.onAllNodes(
            hasClickAction() and hasAnyAncestor(hasTestTag("thinking_lane")),
            useUnmergedTree = true
        ).assertCountEquals(0)
        thoughtNode().assertDoesNotExist()
        rule.onNodeWithText(S.thinkingWaiting).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.thinkingExpand).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.thinkingCollapse).assertDoesNotExist()
        assertEquals(title.fetchSemanticsNode().size.height + 12, laneNode().fetchSemanticsNode().size.height)
    }

    private fun laneNode() = rule.onNodeWithTag("thinking_lane", useUnmergedTree = true)
    private fun thoughtNode() = rule.onNodeWithTag("thinking_text", useUnmergedTree = true)

    private fun settleAnimation() {
        advanceFrames()
        rule.mainClock.advanceTimeBy(2_000)
        advanceFrames()
    }

    private fun advanceFrames() {
        repeat(3) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
    }
}
