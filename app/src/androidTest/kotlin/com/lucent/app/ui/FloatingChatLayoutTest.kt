package com.lucent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingChatLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private class Host(messageCount: Int) {
        val count = mutableStateOf(messageCount)
        val viewportHeight = mutableStateOf(400.dp)
        val composerHeight = mutableStateOf(64.dp)
        val simulatedBottomInset = mutableStateOf(0.dp)
        val listState = LazyListState()
        var messageInset = 0.dp
        var controlInset = 0.dp
        var backgroundClicks = 0
        var composerClicks = 0
        var controlClicks = 0
    }

    @Test
    fun emptyConversationStillHasFullViewportBehindComposerAndControls() {
        val host = Host(0)
        mount(host)
        assertLayerGeometry(host)
        rule.runOnIdle {
            assertEquals(0, host.listState.layoutInfo.totalItemsCount)
            assertFalse(host.listState.canScrollForward)
            assertFalse(host.listState.canScrollBackward)
        }
    }

    @Test
    fun shortAndLongContentUseScrollPaddingRatherThanShrinkingViewport() {
        val host = Host(1)
        mount(host)
        assertLayerGeometry(host)
        assertTrue(bounds("message_0").bottom < bounds(COMPOSER).top)
        rule.runOnIdle { host.count.value = 80 }
        assertLayerGeometry(host)
        scrollToEndAndAssertClearance(host)
    }

    @Test
    fun composerGrowthReducedViewportAndBottomInsetsKeepLastMessageAndControlsClear() {
        val host = Host(80)
        mount(host)
        assertLayerGeometry(host)
        scrollToEndAndAssertClearance(host)
        val initialPadding = rule.runOnIdle { host.listState.layoutInfo.afterContentPadding }
        rule.runOnIdle { host.composerHeight.value = 112.dp }
        assertLayerGeometry(host)
        scrollToEndAndAssertClearance(host)
        rule.runOnIdle {
            assertTrue(host.listState.layoutInfo.afterContentPadding > initialPadding)
            host.viewportHeight.value = 280.dp
        }
        assertLayerGeometry(host)
        assertEquals(px(280.dp), bounds(LAYOUT).height, 2f)
        scrollToEndAndAssertClearance(host)
        val beforeInset = rule.runOnIdle { host.listState.layoutInfo.afterContentPadding }
        rule.runOnIdle { host.simulatedBottomInset.value = 28.dp }
        assertLayerGeometry(host)
        scrollToEndAndAssertClearance(host)
        rule.runOnIdle {
            assertEquals(px(28.dp), (host.listState.layoutInfo.afterContentPadding - beforeInset).toFloat(), 2f)
            host.composerHeight.value = 64.dp
            host.viewportHeight.value = 400.dp
            host.simulatedBottomInset.value = 0.dp
        }
        assertLayerGeometry(host)
        scrollToEndAndAssertClearance(host)
        rule.runOnIdle {
            assertEquals(initialPadding, host.listState.layoutInfo.afterContentPadding)
        }
    }

    @Test
    fun messageSwipeScrollsAndComposerAndControlTouchesDoNotReachBackground() {
        val host = Host(80)
        mount(host)
        val viewport = bounds(VIEWPORT)
        val composer = bounds(COMPOSER)
        rule.onNodeWithTag(VIEWPORT, useUnmergedTree = true).performTouchInput {
            swipe(
                start = Offset(viewport.width / 3f, composer.top - viewport.top - px(20.dp)),
                end = Offset(viewport.width / 3f, px(24.dp)),
                durationMillis = 350
            )
        }
        rule.runOnIdle {
            assertTrue(host.listState.firstVisibleItemIndex > 0 || host.listState.firstVisibleItemScrollOffset > 0)
            assertEquals(0, host.backgroundClicks)
        }
        touchAt(COMPOSER)
        rule.runOnIdle {
            assertEquals(1, host.composerClicks)
            assertEquals(0, host.backgroundClicks)
            assertEquals(0, host.controlClicks)
        }
        touchAt(CONTROL)
        rule.runOnIdle {
            assertEquals(1, host.controlClicks)
            assertEquals(1, host.composerClicks)
            assertEquals(0, host.backgroundClicks)
        }
        rule.runOnIdle { host.count.value = 0 }
        rule.onNodeWithTag(LAYOUT, useUnmergedTree = true).performTouchInput {
            click(Offset(px(32.dp), px(32.dp)))
        }
        rule.runOnIdle { assertEquals(1, host.backgroundClicks) }
        touchAt(COMPOSER)
        rule.runOnIdle {
            assertEquals(2, host.composerClicks)
            assertEquals(1, host.backgroundClicks)
        }
    }

    private fun mount(host: Host) {
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars)) {
                    FloatingChatLayout(
                        modifier = Modifier.fillMaxWidth().height(host.viewportHeight.value).testTag(LAYOUT),
                        messages = { bottomInset ->
                            SideEffect { host.messageInset = bottomInset }
                            Box(Modifier.fillMaxSize().clickable { host.backgroundClicks++ }) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().testTag(MESSAGES),
                                    state = host.listState,
                                    contentPadding = PaddingValues(bottom = bottomInset)
                                ) {
                                    items(host.count.value) { index ->
                                        Box(Modifier.fillMaxWidth().height(48.dp).testTag("message_$index"))
                                    }
                                }
                            }
                        },
                        composer = {
                            Box(Modifier.fillMaxWidth().padding(bottom = host.simulatedBottomInset.value)) {
                                Box(
                                    Modifier.fillMaxWidth().height(host.composerHeight.value)
                                        .testTag(COMPOSER).clickable { host.composerClicks++ }
                                )
                            }
                        },
                        controls = { bottomInset ->
                            SideEffect { host.controlInset = bottomInset }
                            Box(Modifier.align(Alignment.BottomEnd).padding(bottom = bottomInset + 8.dp)) {
                                Box(Modifier.size(48.dp).testTag(CONTROL).clickable { host.controlClicks++ })
                            }
                        }
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun assertLayerGeometry(host: Host) {
        rule.waitForIdle()
        val layout = bounds(LAYOUT)
        val viewport = bounds(VIEWPORT)
        val messages = bounds(MESSAGES)
        val composer = bounds(COMPOSER)
        val control = bounds(CONTROL)
        assertSameBounds(layout, viewport)
        assertSameBounds(viewport, messages)
        assertTrue("Composer must overlap the full message viewport", composer.top < viewport.bottom)
        assertTrue("Composer must stay inside the viewport", composer.top >= viewport.top && composer.bottom <= viewport.bottom)
        assertEquals(px(host.composerHeight.value), composer.height, 2f)
        val expectedInset = px(host.composerHeight.value + host.simulatedBottomInset.value + 12.dp)
        rule.runOnIdle {
            assertEquals("Messages receive the measured composer footprint", expectedInset, px(host.messageInset), 2f)
            assertEquals(host.messageInset, host.controlInset)
            assertEquals("Inset must be content padding, not list height reduction", expectedInset, host.listState.layoutInfo.afterContentPadding.toFloat(), 2f)
        }
        assertEquals(viewport.bottom - expectedInset + px(6.dp), composer.top, 2f)
        assertEquals(viewport.bottom - expectedInset - px(8.dp), control.bottom, 2f)
        assertTrue("Controls must not overlap the composer", control.bottom < composer.top)
        assertTrue("Controls must remain visible after viewport changes", control.top >= viewport.top)
        rule.onNodeWithTag(CONTROL, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun scrollToEndAndAssertClearance(host: Host) {
        val lastIndex = host.count.value - 1
        rule.onNodeWithTag(MESSAGES, useUnmergedTree = true).performScrollToIndex(lastIndex)
        rule.runOnIdle { host.listState.dispatchRawDelta(100_000f) }
        rule.waitForIdle()
        val last = bounds("message_$lastIndex")
        val viewport = bounds(VIEWPORT)
        val composer = bounds(COMPOSER)
        rule.runOnIdle { assertFalse("The list must actually be at its end", host.listState.canScrollForward) }
        assertEquals(viewport.bottom - px(host.messageInset), last.bottom, 2f)
        assertTrue("The entire last message must remain visible", last.top >= viewport.top)
        assertEquals(px(48.dp), last.height, 2f)
        assertTrue("The last message must end above the composer", last.bottom < composer.top)
        rule.onNodeWithTag("message_$lastIndex", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun touchAt(tag: String) {
        val layout = bounds(LAYOUT)
        val target = bounds(tag)
        rule.onNodeWithTag(LAYOUT, useUnmergedTree = true).performTouchInput {
            click(target.center - layout.topLeft)
        }
    }

    private fun bounds(tag: String): Rect =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertSameBounds(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1f)
        assertEquals(expected.top, actual.top, 1f)
        assertEquals(expected.right, actual.right, 1f)
        assertEquals(expected.bottom, actual.bottom, 1f)
    }

    private fun px(value: Dp): Float = with(rule.density) { value.toPx() }

    private companion object {
        const val LAYOUT = "floating_layout_test"
        const val VIEWPORT = "chat_message_viewport"
        const val MESSAGES = "test_message_list"
        const val COMPOSER = "test_composer"
        const val CONTROL = "test_control"
    }
}
