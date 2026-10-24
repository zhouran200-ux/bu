package com.lucent.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucent.app.i18n.S
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThinkingLaneStabilityTest {
    @get:Rule val rule = createComposeRule()

    private class Host {
        val notebookId = -System.nanoTime()
        val readingKey = "chat:$notebookId:thinking-stability"
        val mounted = mutableStateOf(true)
        val owner = mutableStateOf(NotebookMessageUiState())
        val streaming = mutableStateOf(true)
        val thought = mutableStateOf((1..32).joinToString("\n") { "Reasoning line $it" })
        val storedThought = mutableStateOf("")
        var answerTop = Float.NaN
    }

    private var mountedHost: Host? = null

    @After fun discardReadingPosition() {
        val host = mountedHost ?: return
        rule.runOnUiThread { host.mounted.value = false }
        advanceFrame()
        rule.runOnUiThread {
            ReadingPositionMemory.clearNotebook(
                InstrumentationRegistry.getInstrumentation().targetContext,
                host.notebookId
            )
        }
    }

    @Test fun liveReasoningScrollsInsideItsViewportInsteadOfPushingTheAnswer() {
        val host = mount()
        val live = geometry(host)
        val expectedThought = host.thought.value
        assertEquals("Live reasoning must stay inside its viewport", historicalLimitPx(), live.textHeight.toFloat(), 1f)
        assertInnerScrollReachesTheNewestLine()
        complete(host)
        repeat(8) {
            advanceFrame()
            rule.onNodeWithContentDescription(S.thinkingCollapse).assertExists()
            assertEquals(expectedThought, thoughtNode().fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
            assertStableGeometry(live, geometry(host))
            assertInnerScrollReachesTheNewestLine()
        }
    }

    @Test fun explicitlyCollapsingAndReopeningRestoresTheSameViewportHeight() {
        val host = mount()
        val live = geometry(host)
        complete(host)
        repeat(4) { advanceFrame() }
        assertStableGeometry(live, geometry(host))
        rule.onNodeWithContentDescription(S.thinkingCollapse).performClick()
        advanceAnimation()
        thoughtNode().assertDoesNotExist()
        rule.onNodeWithContentDescription(S.thinkingExpand).assertExists()
        val collapsedLaneHeight = laneNode().fetchSemanticsNode().size.height
        assertTrue(collapsedLaneHeight < historicalLimitPx())
        rule.onNodeWithContentDescription(S.thinkingExpand).performClick()
        advanceAnimation()
        rule.onNodeWithContentDescription(S.thinkingCollapse).assertExists()
        val reopened = geometry(host)
        assertEquals("Reopened reasoning must share the live viewport height", historicalLimitPx(), reopened.textHeight.toFloat(), 1f)
        assertTrue("Reopening must not make the lane taller than the live lane", reopened.laneHeight <= live.laneHeight)
        assertEquals(
            "The answer must stay directly below the lane",
            (live.laneHeight - reopened.laneHeight).toFloat(),
            live.answerTop - reopened.answerTop,
            0.6f
        )
        assertEquals(host.thought.value, thoughtNode().fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
        assertInnerScrollReachesTheNewestLine()
        repeat(4) {
            advanceFrame()
            assertEquals(historicalLimitPx(), geometry(host).textHeight.toFloat(), 1f)
        }
    }

    @Test fun legacyExpandedPreferenceIsIgnoredAndOnlyANewVisitResetsManualChoice() {
        val host = mount(seedLegacyExpanded = true)
        complete(host)
        repeat(4) { advanceFrame() }
        val lane = host.owner.value.thinking(host.readingKey) { error("Existing visit lane") }
        val scrollBefore = lane.scroll.value
        rule.runOnUiThread { host.mounted.value = false }
        advanceFrame()
        rule.runOnUiThread { host.mounted.value = true }
        advanceAnimation()
        rule.onNodeWithContentDescription(S.thinkingCollapse).assertExists()
        rule.runOnUiThread {
            assertTrue(lane.expanded)
            assertEquals(scrollBefore, lane.scroll.value)
        }
        rule.runOnUiThread { host.mounted.value = false }
        advanceFrame()
        rule.runOnUiThread {
            host.owner.value = NotebookMessageUiState()
            host.mounted.value = true
        }
        advanceAnimation()
        thoughtNode().assertDoesNotExist()
        rule.onNodeWithContentDescription(S.thinkingExpand).assertExists()
        rule.runOnUiThread {
            val fresh = host.owner.value.thinking(host.readingKey) { error("New visit lane") }
            assertFalse(fresh.expanded)
            assertEquals(0, fresh.scroll.value)
            assertEquals(ReadingPosition("expanded", 0, 87), ReadingPositionMemory.read(
                InstrumentationRegistry.getInstrumentation().targetContext, host.readingKey
            ))
        }
    }

    @Test fun fullExpandButtonSwitchesBetweenFullHeightAndSmallWindowWhileHeaderClosesAll() {
        val host = mount()
        complete(host)
        repeat(4) { advanceFrame() }
        val smallWindowHeight = thoughtNode().fetchSemanticsNode().size.height
        assertEquals(historicalLimitPx(), smallWindowHeight.toFloat(), 1f)
        fullExpandNode().assertTextEquals(S.thinkingFullExpand)

        fullExpandNode().performClick()
        advanceAnimation()
        fullExpandNode().assertTextEquals(S.thinkingReturnWindow)
        val fullHeight = thoughtNode().fetchSemanticsNode().size.height
        assertTrue("Full expand must show more than the bounded inner window", fullHeight > smallWindowHeight)

        fullExpandNode().performClick()
        advanceAnimation()
        fullExpandNode().assertTextEquals(S.thinkingFullExpand)
        assertEquals(smallWindowHeight, thoughtNode().fetchSemanticsNode().size.height)

        fullExpandNode().performClick()
        advanceAnimation()
        assertTrue(thoughtNode().fetchSemanticsNode().size.height > smallWindowHeight)
        rule.onNodeWithContentDescription(S.thinkingCollapse).performClick()
        advanceAnimation()
        thoughtNode().assertDoesNotExist()
        fullExpandNode().assertDoesNotExist()

        rule.onNodeWithContentDescription(S.thinkingExpand).performClick()
        advanceAnimation()
        fullExpandNode().assertTextEquals(S.thinkingFullExpand)
        assertEquals(smallWindowHeight, thoughtNode().fetchSemanticsNode().size.height)
    }

    private fun mount(seedLegacyExpanded: Boolean = false): Host {
        val host = Host()
        if (seedLegacyExpanded) {
            ReadingPositionMemory.write(
                InstrumentationRegistry.getInstrumentation().targetContext,
                host.readingKey, ReadingPosition("expanded", 0, 87)
            )
        }
        mountedHost = host
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                if (host.mounted.value) {
                    LazyColumn(Modifier.width(300.dp).height(400.dp)) {
                        item(key = "message") {
                            Column {
                                ThinkingLane(
                                    readingKey = host.readingKey,
                                    liveThought = if (host.streaming.value) host.thought else null,
                                    storedThought = host.storedThought.value,
                                    tag = null,
                                    streaming = host.streaming.value,
                                    answerSeconds = if (host.streaming.value) 0 else 3,
                                    lane = rememberThinkingLaneState(host.readingKey, host.streaming.value, host.owner.value)
                                )
                                BasicText(
                                    "Answer body stays below the reasoning lane",
                                    modifier = Modifier.testTag("answer_body").onGloballyPositioned {
                                        host.answerTop = it.positionInRoot().y
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        repeat(4) { advanceFrame() }
        thoughtNode().assertDoesNotExist()
        rule.onNodeWithContentDescription(S.thinkingExpand).performClick()
        advanceAnimation()
        thoughtNode().assertExists()
        return host
    }

    private fun complete(host: Host) = rule.runOnUiThread {
        host.storedThought.value = host.thought.value
        host.streaming.value = false
    }

    private data class Geometry(val textHeight: Int, val laneHeight: Int, val answerTop: Float)

    private fun geometry(host: Host): Geometry {
        val textHeight = thoughtNode().fetchSemanticsNode().size.height
        val laneHeight = laneNode().fetchSemanticsNode().size.height
        val answerTop = rule.runOnUiThread { host.answerTop }
        assertFalse("Answer position must have been measured", answerTop.isNaN())
        return Geometry(textHeight, laneHeight, answerTop)
    }

    private fun assertStableGeometry(expected: Geometry, actual: Geometry) {
        assertEquals("Completion must not resize the reasoning viewport", expected.textHeight, actual.textHeight)
        assertEquals("Completion must not resize the lane", expected.laneHeight, actual.laneHeight)
        assertEquals("The answer must not jump when thinking completes", expected.answerTop, actual.answerTop, 1f)
    }

    private fun assertInnerScrollReachesTheNewestLine() {
        val range = thoughtNode().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        rule.runOnUiThread {
            assertTrue("Live reasoning must scroll inside its own viewport", range.maxValue() > 0f)
            assertEquals("The live lane must follow the newest line", range.maxValue(), range.value())
        }
    }

    private fun advanceAnimation() {
        repeat(90) { advanceFrame() }
    }

    private fun advanceFrame() {
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    private fun historicalLimitPx(): Float = with(rule.density) { THINKING_LANE_MAX_HEIGHT.toPx() }
    private fun thoughtNode() = rule.onNodeWithTag("thinking_text", useUnmergedTree = true)
    private fun laneNode() = rule.onNodeWithTag("thinking_lane", useUnmergedTree = true)
    private fun fullExpandNode() = rule.onNodeWithTag("thinking_full_expand_toggle")
}
