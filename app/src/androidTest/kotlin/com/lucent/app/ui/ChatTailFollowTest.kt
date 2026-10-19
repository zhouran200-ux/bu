package com.lucent.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ChatTailFollowTest {
    @get:Rule val rule = createComposeRule()

    private class Host(following: Boolean = true) {
        val list = LazyListState()
        val reference = LazyListState()
        val following = mutableStateOf(following)
        val thought = mutableStateOf(lines("thought", 24))
        val body = mutableStateOf(lines("body", 20))
        val layoutExtra = mutableStateOf(0.dp)
        val layoutRequest = mutableIntStateOf(0)
        var followChecks = 0
    }

    @Test fun growingThoughtAndBodyStayAtTailInsideOneLongItem() {
        val host = mount()
        awaitTail(host)
        repeat(12) { frame ->
            val oldSize = itemSize(host)
            val oldOffset = offset(host)
            rule.runOnUiThread {
                if (frame % 2 == 0) host.thought.value += "\nthought ${24 + frame}"
                else host.body.value += "\nbody ${20 + frame}"
            }
            advanceFrame()
            assertTrue("Text growth must produce a new measured height", itemSize(host) > oldSize)
            awaitTail(host, maxFrames = 3)
            assertTrue("Forward growth must not reanchor backwards", offset(host) > oldOffset)
        }
        rule.runOnUiThread {
            assertEquals(1, host.list.layoutInfo.totalItemsCount)
            assertEquals("message", host.list.layoutInfo.visibleItemsInfo.single().key)
            assertTrue(host.list.firstVisibleItemScrollOffset > host.list.layoutInfo.viewportSize.height)
        }
    }

    @Test fun consecutiveGrowingLayoutFramesNeverFallMoreThanOneFrameBehind() {
        val host = mount()
        awaitTail(host)
        val growthPx = with(rule.density) { 24.dp.roundToPx() }
        repeat(16) {
            val oldOffset = offset(host)
            rule.runOnUiThread { host.layoutExtra.value += 24.dp }
            advanceFrame()
            rule.runOnUiThread {
                val layout = host.list.layoutInfo
                val item = layout.visibleItemsInfo.single()
                val gap = item.offset + item.size + layout.afterContentPadding - layout.viewportEndOffset
                assertTrue("Continuous layout growth must not accumulate unfollowed frames", gap <= growthPx + 1)
                assertTrue(host.list.firstVisibleItemScrollOffset >= oldOffset)
            }
        }
        awaitTail(host, maxFrames = 3)
    }

    @Test fun delayedMeasureOnlyGrowthFollowsWithoutAnyTextOrItemChange() {
        val host = mount()
        awaitTail(host)
        repeat(8) { advanceFrame() }
        val oldSize = itemSize(host)
        val thought = host.thought.value
        val body = host.body.value
        rule.runOnUiThread { host.layoutRequest.intValue = 1 }
        repeat(3) {
            advanceFrame()
            assertEquals(oldSize, itemSize(host))
            assertAtTail(host)
        }
        advanceUntil("The delayed layout never arrived", 8) { itemSize(host) > oldSize }
        awaitTail(host, maxFrames = 3)
        rule.runOnUiThread {
            assertEquals(thought, host.thought.value)
            assertEquals(body, host.body.value)
            assertEquals(1, host.list.layoutInfo.totalItemsCount)
        }
    }

    @Test fun historicalContentDoesNotFollowWhileFollowingIsFalse() {
        val host = mount(Host(following = false))
        repeat(8) { advanceFrame() }
        assertEquals(0, offset(host))
        rule.runOnUiThread {
            host.thought.value += "\nmore history"
            host.layoutRequest.intValue = 1
        }
        repeat(12) { advanceFrame() }
        rule.runOnUiThread {
            assertEquals(0, host.list.firstVisibleItemIndex)
            assertEquals(0, host.list.firstVisibleItemScrollOffset)
            assertTrue(host.list.canScrollForward)
            assertEquals(0, host.followChecks)
        }
    }

    @Test fun cancellingFollowingWithAFramePendingDoesNotPullBackToTail() {
        val host = mount()
        awaitTail(host)
        val before = queueGrowthBeforeFollowFrame(host)
        rule.runOnUiThread { host.following.value = false }
        repeat(6) { advanceFrame() }
        assertEquals(before, offset(host))
        rule.runOnUiThread { host.layoutExtra.value += 80.dp }
        repeat(6) { advanceFrame() }
        assertEquals(before, offset(host))
        rule.runOnUiThread { assertTrue(host.list.canScrollForward) }
    }

    @Test fun userPressWithAFramePendingPreventsTailPullWithoutWaitingForRecomposition() {
        val host = mount()
        awaitTail(host)
        val before = queueGrowthBeforeFollowFrame(host)
        val list = rule.onNodeWithTag("follow_list")
        list.performTouchInput { down(center) }
        try {
            rule.runOnUiThread {
                assertFalse(host.following.value)
            }
            repeat(6) { advanceFrame() }
            assertEquals(before, offset(host))
            rule.runOnUiThread { host.layoutExtra.value += 80.dp }
            repeat(6) { advanceFrame() }
            assertEquals(before, offset(host))
            rule.runOnUiThread { assertTrue(host.list.canScrollForward) }
        } finally {
            rule.runOnUiThread { host.following.value = false }
            list.performTouchInput { cancel() }
        }
    }

    @Test fun shorteningContentDoesNotScrollBackBeyondTheNaturalLayoutClamp() {
        val host = Host().apply { layoutExtra.value = 180.dp }
        mount(host, withReference = true)
        awaitTail(host)
        val tailOffset = offset(host)
        rule.runOnUiThread { host.reference.dispatchRawDelta(tailOffset.toFloat()) }
        advanceFrame()
        rule.runOnUiThread {
            assertEquals(host.reference.firstVisibleItemScrollOffset, host.list.firstVisibleItemScrollOffset)
            assertFalse(host.reference.canScrollForward)
        }
        val before = offset(host)
        rule.runOnUiThread {
            host.layoutExtra.value = 0.dp
            host.thought.value = lines("thought", 16)
        }
        repeat(6) {
            advanceFrame()
            rule.runOnUiThread {
                assertEquals(host.reference.firstVisibleItemIndex, host.list.firstVisibleItemIndex)
                assertEquals(
                    "Following must not add reverse movement to layout clamping",
                    host.reference.firstVisibleItemScrollOffset,
                    host.list.firstVisibleItemScrollOffset
                )
            }
        }
        assertTrue("The fixture must actually shrink", offset(host) < before)
        assertAtTail(host)
    }

    private fun mount(host: Host = Host(), withReference: Boolean = false): Host {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FollowChatTail(
                listState = host.list,
                active = true,
                following = host.following.value,
                canFollow = {
                    host.followChecks++
                    host.following.value
                }
            )
            LaunchedEffect(host.layoutRequest.intValue) {
                val request = host.layoutRequest.intValue
                if (request > 0) {
                    repeat(6) { withFrameNanos { } }
                    host.layoutExtra.value = (request * 96).dp
                }
            }
            Row {
                TailList(
                    host,
                    host.list,
                    Modifier.testTag("follow_list").pointerInput(host) {
                        detectTapGestures(onPress = {
                            host.following.value = false
                            tryAwaitRelease()
                        })
                    }
                )
                if (withReference) TailList(host, host.reference, Modifier)
            }
        }
        advanceFrame()
        return host
    }

    @Composable private fun TailList(host: Host, state: LazyListState, modifier: Modifier) {
        LazyColumn(
            state = state,
            modifier = modifier.width(140.dp).height(280.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "message") {
                Column(Modifier.fillMaxWidth()) {
                    BasicText(
                        text = host.thought.value,
                        style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height + host.layoutExtra.value.roundToPx()) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                    )
                    BasicText(host.body.value, style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp))
                }
            }
        }
    }

    private fun queueGrowthBeforeFollowFrame(host: Host): Int {
        val before = offset(host)
        val checks = rule.runOnUiThread { host.followChecks }
        val oldSize = itemSize(host)
        rule.runOnUiThread { host.layoutExtra.value += 96.dp }
        advanceUntil("No follow request was queued for the measured growth", 4) {
            rule.runOnUiThread { host.followChecks > checks }
        }
        assertTrue(itemSize(host) > oldSize)
        assertEquals("Cancellation must happen before the queued scroll runs", before, offset(host))
        rule.runOnUiThread { assertTrue(host.list.canScrollForward) }
        return before
    }

    private fun awaitTail(host: Host, maxFrames: Int = 6) {
        advanceUntil("The real layout did not reach its tail in $maxFrames frames", maxFrames) {
            rule.runOnUiThread {
                host.list.layoutInfo.visibleItemsInfo.isNotEmpty() && !host.list.canScrollForward
            }
        }
        assertAtTail(host)
    }

    private fun assertAtTail(host: Host) = rule.runOnUiThread {
        val layout = host.list.layoutInfo
        val last = layout.visibleItemsInfo.single()
        assertEquals(1, layout.totalItemsCount)
        assertFalse(host.list.canScrollForward)
        assertTrue(
            "Measured item tail must meet the padded viewport",
            abs(last.offset + last.size + layout.afterContentPadding - layout.viewportEndOffset) <= 1
        )
    }

    private fun advanceUntil(message: String, maxFrames: Int, condition: () -> Boolean) {
        repeat(maxFrames) {
            rule.waitForIdle()
            if (condition()) return
            advanceFrame()
        }
        assertTrue(message, condition())
    }

    private fun advanceFrame() {
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
    }

    private fun offset(host: Host): Int = rule.runOnUiThread { host.list.firstVisibleItemScrollOffset }
    private fun itemSize(host: Host): Int = rule.runOnUiThread { host.list.layoutInfo.visibleItemsInfo.single().size }

    private companion object {
        fun lines(prefix: String, count: Int): String = (1..count).joinToString("\n") { "$prefix $it" }
    }
}
