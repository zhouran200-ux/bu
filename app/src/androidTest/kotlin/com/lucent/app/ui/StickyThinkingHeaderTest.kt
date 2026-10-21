package com.lucent.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucent.app.chat.UiChatMessage
import com.lucent.app.i18n.S
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StickyThinkingHeaderTest {
    @get:Rule val rule = createComposeRule()

    private class Host {
        val notebookId = -System.nanoTime()
        val owner = NotebookMessageUiState()
        val list = LazyListState()
        val mounted = mutableStateOf(true)
        val active = mutableStateOf(true)
        val streamingAnswerId = mutableStateOf<Long?>(null)
        val liveThought = mutableStateOf("")
        val messages = listOf(
            UiChatMessage(11, "assistant", "First answer", "First reasoning", answerSeconds = 3),
            UiChatMessage(12, "user", "User message"),
            UiChatMessage(13, "assistant", "Second answer", "Second reasoning", answerSeconds = 7),
            UiChatMessage(14, "assistant", "Answer without reasoning")
        )
        val bodyTops = mutableMapOf<Long, Float>()
        var following = true
        var toggleCalls = 0
        var expandedWhenPaused = false

        init { messages.filter { it.role == "assistant" }.forEach { lane(it.id) } }

        fun readingKey(id: Long) = "chat:$notebookId:thought:$id"

        fun lane(id: Long): ThinkingLaneState = owner.thinking(readingKey(id)) {
            ThinkingLaneState(ReadingPosition("expanded", 0, 0), streaming = false)
        }

        fun thinkingHeight(id: Long): Long = lane(id).measuredHeightPx.toLong()

        fun visibleItem(key: String) = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    }

    private var mountedHost: Host? = null

    @After fun discardReadingPositions() {
        val host = mountedHost ?: return
        rule.runOnUiThread { host.mounted.value = false }
        advanceFrames()
        rule.runOnUiThread {
            ReadingPositionMemory.clearNotebook(
                InstrumentationRegistry.getInstrumentation().targetContext, host.notebookId
            )
        }
    }

    @Test fun expandedAnswerPinsAtItsTopUntilTheAnswerBodyReachesTheViewportTop() {
        val host = mount()
        sticky().assertDoesNotExist()
        scrollTo(host, 1)
        assertPinned(S.thinkingDoneSeconds(3))
        val pinnedTop = sticky().fetchSemanticsNode().boundsInRoot.top

        scrollBy(host, 40)

        assertPinned(S.thinkingDoneSeconds(3))
        assertEquals(pinnedTop, sticky().fetchSemanticsNode().boundsInRoot.top, 1f)
        rule.runOnUiThread {
            val item = host.visibleItem("message:11")
            assertNotNull(item)
            assertTrue("thinking region still covers the viewport top",
                item!!.offset.toLong() + host.thinkingHeight(11) > host.list.layoutInfo.viewportStartOffset.toLong())
            assertTrue("answer body is still below the viewport top", checkNotNull(host.bodyTops[11]) > pinnedTop)
        }
    }

    @Test fun headerLeavesOnceTheBodyOwnsTheTopWithoutMovingTheList() {
        val host = mount()
        scrollTo(host, 1)
        assertPinned(S.thinkingDoneSeconds(3))

        val exit = host.thinkingHeight(11)
        assertTrue(exit > 1)
        scrollBy(host, (exit - 1).toInt())
        val bodyTopBefore = rule.runOnUiThread { checkNotNull(host.bodyTops[11]) }
        val scrollBefore = rule.runOnUiThread { host.list.firstVisibleItemIndex to host.list.firstVisibleItemScrollOffset }
        rule.runOnUiThread {
            val layout = host.list.layoutInfo
            val item = checkNotNull(layout.visibleItemsInfo.firstOrNull { it.key == "message:11" })
            assertTrue("thinking region must still cover the top", item.offset.toLong() + exit > layout.viewportStartOffset.toLong())
        }

        scrollBy(host, 1)

        sticky().assertDoesNotExist()
        rule.runOnUiThread {
            val layout = host.list.layoutInfo
            val item = checkNotNull(layout.visibleItemsInfo.firstOrNull { it.key == "message:11" })
            assertTrue("body must now own the top", item.offset.toLong() + exit <= layout.viewportStartOffset.toLong())
            assertEquals(scrollBefore.first, host.list.firstVisibleItemIndex)
            assertEquals(scrollBefore.second + 1, host.list.firstVisibleItemScrollOffset)
            assertEquals(bodyTopBefore - 1f, checkNotNull(host.bodyTops[11]), 1f)
        }
    }

    @Test fun userAndSpacingNeverPinAndAnswerOwnershipSwitchesInBothDirections() {
        val host = mount()
        scrollTo(host, 1)
        assertPinned(S.thinkingDoneSeconds(3))
        val thinking11 = host.thinkingHeight(11).toInt()
        assertTrue(thinking11 + 100 < MessageHeightPx)

        scrollBy(host, thinking11 - 1)
        assertPinned(S.thinkingDoneSeconds(3))
        scrollBy(host, 1)
        sticky().assertDoesNotExist()

        scrollBy(host, 100)
        assertTopKey(host, "message:11")
        sticky().assertDoesNotExist()

        scrollBy(host, MessageHeightPx - thinking11 - 100 + MessageSpacingPx / 2)
        assertGapHasNoOwner(host)
        sticky().assertDoesNotExist()
        scrollBy(host, MessageSpacingPx / 2)
        assertTopKey(host, "message:12")
        sticky().assertDoesNotExist()

        scrollTo(host, 3)
        assertPinned(S.thinkingDoneSeconds(7))
        val thinking13 = host.thinkingHeight(13).toInt()
        assertTrue(thinking13 > MessageSpacingPx && thinking13 < MessageHeightPx)

        scrollBy(host, -1)
        assertPinned(S.thinkingDoneSeconds(7))
        scrollBy(host, -(thinking13 - 1))
        sticky().assertDoesNotExist()

        scrollBy(host, -MessageSpacingPx / 2)
        assertTopKey(host, "message:12")
        sticky().assertDoesNotExist()

        scrollTo(host, 1)
        assertPinned(S.thinkingDoneSeconds(3))
    }

    @Test fun collapsedAndUnsupportedAnswersDoNotPinEvenWithAdjacentExpandedAnswers() {
        val host = Host()
        host.lane(11).toggle()
        mount(host)
        scrollTo(host, 1)
        sticky().assertDoesNotExist()
        laneToggle(11, S.thinkingExpand).assertExists()
        scrollTo(host, 3)
        assertPinned(S.thinkingDoneSeconds(7))
        scrollTo(host, 4)
        sticky().assertDoesNotExist()
        rule.runOnUiThread { assertTrue(host.lane(14).expanded) }
        scrollTo(host, 3)
        assertPinned(S.thinkingDoneSeconds(7))
        scrollTo(host, 1)
        sticky().assertDoesNotExist()
    }

    @Test fun stickyClickPausesFollowBeforeTogglingTheSameStateAsTheOriginalLane() {
        val host = mount()
        scrollTo(host, 1)
        assertPinned(S.thinkingDoneSeconds(3))
        sticky().performClick()
        settleAnimation()

        sticky().assertDoesNotExist()
        laneToggle(11, S.thinkingExpand).assertExists()
        rule.runOnUiThread {
            assertFalse(host.lane(11).expanded)
            assertFalse(host.following)
            assertTrue(host.expandedWhenPaused)
            assertEquals(1, host.toggleCalls)
        }

        laneToggle(11, S.thinkingExpand).performClick()
        settleAnimation()
        assertPinned(S.thinkingDoneSeconds(3))
        laneToggle(11, S.thinkingCollapse).assertExists()
        rule.runOnUiThread {
            assertTrue(host.lane(11).expanded)
            assertEquals(1, host.toggleCalls)
        }
    }

    @Test fun inactiveHeaderHidesWithoutChangingTheExpandedLane() {
        val host = mount()
        scrollTo(host, 3)
        scrollBy(host, (host.thinkingHeight(13) - 1).toInt())
        assertPinned(S.thinkingDoneSeconds(7))
        rule.runOnUiThread { host.active.value = false }
        advanceFrames()
        sticky().assertDoesNotExist()
        rule.runOnUiThread {
            assertTrue(host.lane(13).expanded)
            assertEquals(0, host.toggleCalls)
            host.active.value = true
        }
        advanceFrames()
        assertPinned(S.thinkingDoneSeconds(7))
    }

    @Test fun streamingAnswerCanPinBeforeReasoningArrivesButUnsupportedCompletionCannot() {
        val host = Host().apply { streamingAnswerId.value = 14 }
        mount(host)
        scrollTo(host, 4)
        assertPinned(S.thinkingStreaming)
        rule.runOnUiThread { host.streamingAnswerId.value = null }
        settleAnimation()
        sticky().assertDoesNotExist()
        rule.runOnUiThread { assertTrue(host.lane(14).expanded) }
    }

    private fun mount(host: Host = Host()): Host {
        mountedHost = host
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    if (host.mounted.value) {
                        Box(Modifier.width(300.dp).height(240.dp)) {
                            LazyColumn(
                                state = host.list,
                                modifier = Modifier.matchParentSize().testTag("messages"),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item(key = "header") { Spacer(Modifier.height(24.dp)) }
                                items(host.messages, key = { "message:${it.id}" }) { message ->
                                    MessageItem(host, message)
                                }
                                item(key = "footer") { Spacer(Modifier.height(240.dp)) }
                            }
                            StickyThinkingHeader(
                                listState = host.list,
                                messages = host.messages,
                                notebookId = host.notebookId,
                                owner = host.owner,
                                streamingAnswerId = host.streamingAnswerId.value,
                                liveThought = host.liveThought,
                                active = host.active.value,
                                onToggle = {
                                    host.following = false
                                    host.expandedWhenPaused = host.lane(11).expanded
                                    host.toggleCalls++
                                }
                            )
                        }
                    }
                }
            }
        }
        advanceFrames()
        return host
    }

    @Composable private fun MessageItem(host: Host, message: UiChatMessage) {
        Column(Modifier.fillMaxWidth().height(420.dp).testTag("message:${message.id}")) {
            if (message.role == "assistant") {
                val streaming = message.id == host.streamingAnswerId.value
                ThinkingLane(
                    readingKey = host.readingKey(message.id),
                    liveThought = if (streaming) host.liveThought else null,
                    storedThought = message.reasoningText.orEmpty(),
                    tag = null,
                    streaming = streaming,
                    answerSeconds = message.answerSeconds,
                    lane = rememberThinkingLaneState(host.readingKey(message.id), streaming, host.owner)
                )
            }
            BasicText(
                message.content,
                Modifier.fillMaxWidth().weight(1f).onGloballyPositioned {
                    host.bodyTops[message.id] = it.positionInRoot().y
                }
            )
        }
    }

    private fun scrollTo(host: Host, index: Int, offsetDp: Int = 0) {
        rule.onNodeWithTag("messages").performScrollToIndex(index)
        advanceFrames()
        if (offsetDp != 0) scrollBy(host, offsetDp)
    }

    private fun scrollBy(host: Host, distanceDp: Int) {
        val distance = distanceDp.toFloat()
        rule.runOnUiThread { assertEquals(distance, host.list.dispatchRawDelta(distance), 1f) }
        advanceFrames()
    }

    private fun assertTopKey(host: Host, expected: String) = rule.runOnUiThread {
        val layout = host.list.layoutInfo
        val top = layout.visibleItemsInfo.single {
            it.offset <= layout.viewportStartOffset && it.offset + it.size > layout.viewportStartOffset
        }
        assertEquals(expected, top.key)
    }

    private fun assertGapHasNoOwner(host: Host) = rule.runOnUiThread {
        val layout = host.list.layoutInfo
        assertTrue(layout.visibleItemsInfo.isNotEmpty())
        assertTrue(layout.visibleItemsInfo.none {
            it.offset <= layout.viewportStartOffset && it.offset + it.size > layout.viewportStartOffset
        })
    }

    private fun assertPinned(title: String) {
        sticky().assertIsDisplayed().assertTextEquals(title)
    }

    private fun laneToggle(id: Long, label: String) = rule.onNode(
        hasContentDescription(label) and hasAnyAncestor(hasTestTag("message:$id"))
    )

    private fun sticky() = rule.onNodeWithTag("sticky_thinking_header")

    private companion object {
        const val MessageHeightPx = 420
        const val MessageSpacingPx = 16
    }

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
