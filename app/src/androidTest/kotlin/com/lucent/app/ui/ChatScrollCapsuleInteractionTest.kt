package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatScrollCapsuleInteractionTest {
    @get:Rule val rule = createComposeRule()

    private class Host {
        val state = LazyListState(8, 0)
        val height = mutableStateOf(400.dp)
        val itemHeight = mutableStateOf(100.dp)
        val enabled = mutableStateOf(true)
        val generation = mutableIntStateOf(0)
        val recompose = mutableIntStateOf(0)
        var presses = 0
        var lastCallbackVersion = -1
    }

    @Test fun pressPausesImmediatelyAndHoldingPastFadeTimeoutDoesNotSeekOrDisappear() {
        val host = mount()
        val before = position(host)
        val top = capsuleBounds().top
        val point = capsulePoint()
        root().performTouchInput { down(point) }
        rule.runOnIdle {
            assertEquals(1, host.presses)
            assertFalse(host.state.isScrollInProgress)
            assertEquals(before, position(host))
        }
        rule.mainClock.advanceTimeBy(1000)
        capsule().assertIsDisplayed()
        assertEquals(top, capsuleBounds().top, 1f)
        root().performTouchInput { up() }
        rule.runOnIdle { assertEquals(before, position(host)) }
        rule.mainClock.advanceTimeBy(900)
        capsule().assertDoesNotExist()
    }

    @Test fun movingThumbTracksRootPointerAcrossFramesAndUnrelatedRecomposition() {
        val host = mount()
        val point = capsulePoint()
        val slop = with(rule.density) { 40.dp.toPx() }
        root().performTouchInput { down(point); moveTo(point + Offset(0f, -slop)) }
        rule.mainClock.advanceTimeBy(48)
        val top = capsuleBounds().top
        rule.runOnIdle { host.recompose.intValue++ }
        root().performTouchInput { moveTo(point + Offset(0f, -slop - 30f)) }
        rule.mainClock.advanceTimeBy(48)
        assertEquals(top - 30f, capsuleBounds().top, 2f)
        val movedPosition = position(host)
        root().performTouchInput { moveTo(point + Offset(0f, -slop - 30f)) }
        rule.mainClock.advanceTimeBy(48)
        assertEquals(top - 30f, capsuleBounds().top, 2f)
        rule.runOnIdle {
            assertEquals(1, host.presses)
            assertEquals(0, host.lastCallbackVersion)
            assertEquals(movedPosition, position(host))
        }
        root().performTouchInput { up() }
    }

    @Test fun resizingWhileHeldDoesNotMultiplyTheFrozenFractionByNewTrackHeight() {
        val host = mount()
        val point = capsulePoint()
        val slop = with(rule.density) { 40.dp.toPx() }
        root().performTouchInput { down(point); moveTo(point + Offset(0f, -slop)) }
        rule.mainClock.advanceTimeBy(48)
        val top = capsuleBounds().top
        rule.runOnIdle { host.height.value = 340.dp }
        rule.mainClock.advanceTimeBy(48)
        assertEquals(top, capsuleBounds().top, 2f)
        root().performTouchInput { moveTo(point + Offset(0f, -slop - 30f)) }
        rule.mainClock.advanceTimeBy(48)
        assertEquals(top - 30f, capsuleBounds().top, 2f)
        root().performTouchInput { cancel() }
        rule.mainClock.advanceTimeBy(900)
        capsule().assertDoesNotExist()
    }

    @Test fun heldMeasurementsAreAvailableOnReleaseWithoutAnotherLayoutChange() {
        val host = mount()
        val point = capsulePoint()
        root().performTouchInput { down(point) }
        rule.mainClock.advanceTimeBy(48)
        val held = fraction()
        rule.runOnIdle { host.itemHeight.value = 240.dp }
        rule.mainClock.advanceTimeBy(48)
        assertEquals(held, fraction(), 0.0001f)
        root().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(48)
        assertTrue(fraction() < held)
    }

    @Test fun disablingDuringDragClearsSessionAndRestoresNormalFadeLifecycle() {
        val host = mount()
        val point = capsulePoint()
        val slop = with(rule.density) { 40.dp.toPx() }
        root().performTouchInput { down(point); moveTo(point + Offset(0f, -slop)) }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { host.enabled.value = false }
        rule.mainClock.advanceTimeBy(48)
        capsule().assertDoesNotExist()
        root().performTouchInput { cancel() }
        rule.runOnIdle { host.enabled.value = true }
        rule.mainClock.advanceTimeBy(900)
        capsule().assertDoesNotExist()
    }

    @Test fun replacingKeysCancelsFrozenIndexSeekInsteadOfTargetingNewItems() {
        val host = mount()
        val point = capsulePoint()
        val slop = with(rule.density) { 40.dp.toPx() }
        root().performTouchInput { down(point); moveTo(point + Offset(0f, -slop)) }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { host.generation.intValue++ }
        rule.mainClock.advanceTimeBy(48)
        val before = position(host)
        root().performTouchInput { moveTo(point + Offset(0f, -slop - 40f)); up() }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertEquals(before, position(host)) }
        rule.mainClock.advanceTimeBy(900)
        capsule().assertDoesNotExist()
    }

    @Test fun accessibilitySeekingStillReachesBothRealListEndpoints() {
        val host = mount()
        capsule().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(1f)) }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { assertFalse(host.state.canScrollForward) }
        capsule().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0f)) }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle {
            assertFalse(host.state.canScrollBackward)
            assertEquals(2, host.presses)
        }
    }

    @Test fun rightEdgeOutsideTheThumbStillScrollsTheUnderlyingList() {
        val host = mount()
        val before = position(host)
        root().performTouchInput {
            swipe(Offset(width - 4f, height * 0.9f), Offset(width - 4f, height * 0.6f), 300)
        }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle {
            assertEquals(0, host.presses)
            assertTrue(position(host) != before)
        }
    }

    private fun mount(): Host {
        val host = Host()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                val keys = List(30) { "${host.generation.intValue}:$it" }
                val callbackVersion = host.recompose.intValue
                Box(Modifier.width(300.dp).height(host.height.value).testTag("capsule_host")) {
                    LazyColumn(state = host.state, modifier = Modifier.fillMaxSize()) {
                        items(keys.size, key = { keys[it] }) {
                            Box(Modifier.fillMaxWidth().height(host.itemHeight.value))
                        }
                    }
                    FloatingScrollCapsule(
                        listState = host.state,
                        itemKeys = keys,
                        enabled = host.enabled.value,
                        onDragStart = {
                            host.presses++
                            host.lastCallbackVersion = callbackVersion
                        },
                        label = "test_capsule",
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
                LaunchedEffect(host.state) { host.state.scroll { awaitCancellation() } }
            }
        }
        rule.mainClock.advanceTimeBy(200)
        capsule().assertIsDisplayed()
        return host
    }

    private fun root() = rule.onNodeWithTag("capsule_host")
    private fun capsule() = rule.onNodeWithContentDescription("test_capsule")
    private fun capsuleBounds(): Rect = capsule().fetchSemanticsNode().boundsInRoot
    private fun capsulePoint(): Offset = capsuleBounds().center - root().fetchSemanticsNode().boundsInRoot.topLeft
    private fun position(host: Host) = ChatScrollTarget(host.state.firstVisibleItemIndex, host.state.firstVisibleItemScrollOffset)
    private fun fraction(): Float = capsule().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
}
