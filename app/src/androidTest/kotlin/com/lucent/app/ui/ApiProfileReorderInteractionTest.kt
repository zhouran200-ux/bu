package com.lucent.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.ApiProfile
import com.lucent.app.i18n.S
import com.lucent.app.ui.settings.ApiProfileListSection
import com.lucent.app.ui.settings.ApiProfileSaveButton
import com.lucent.app.ui.settings.ProfileDrag
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiProfileReorderInteractionTest {
    @get:Rule val rule = createComposeRule()
    private val original = listOf(ApiProfile(name = "Alpha", id = "a"), ApiProfile(name = "Beta", id = "b"))
    private val drag = mutableStateOf<ProfileDrag?>(null)
    private val active = mutableStateOf(true)
    private val profiles = mutableStateOf(original)
    private var selected = mutableStateOf(0)
    private lateinit var scroll: ScrollState
    private var completion: ((Result<Unit>) -> Unit)? = null
    private var submitted: List<ApiProfile>? = null
    private var submittedSelection: Int? = null
    private var saves = 0
    private var reorderCalls = 0

    private fun render() {
        rule.setContent {
            scroll = rememberScrollState()
            var viewport by androidx.compose.runtime.remember { mutableStateOf<Rect?>(null) }
            Column {
                ApiProfileSaveButton(drag) { saves++ }
                Column(Modifier.size(320.dp, 280.dp).testTag("viewport")
                    .onGloballyPositioned { viewport = it.boundsInRoot() }.verticalScroll(scroll)) {
                    ApiProfileListSection(
                        dragState = drag, profiles = profiles.value, selectedProfileIdx = selected.value,
                        models = emptyList(), active = active.value, scrollState = scroll,
                        viewportBounds = viewport, onRequestDeleteProfile = {}, onSelectProfile = {},
                        onReorderProfiles = { order, selection, done ->
                            reorderCalls++
                            submitted = order
                            submittedSelection = selection
                            completion = done
                        }, onAddProfile = {}
                    )
                    Spacer(Modifier.height(2000.dp))
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText(S.saveApi).assertIsEnabled()
    }

    private fun grabFirstHandle() {
        val point = rule.onAllNodesWithContentDescription(S.a11yDragToReorder)[0]
            .fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(point)
            advanceEventTime(700)
            moveBy(Offset.Zero)
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(0, drag.value!!.held) }
        rule.onNodeWithText(S.saveApi).assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, saves) }
    }

    private fun moveToBottomEdge() {
        val viewport = rule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { moveTo(Offset(viewport.left + 28f, viewport.bottom - 2f)) }
        rule.mainClock.advanceTimeByFrame()
        val before = rule.runOnIdle { scroll.value }
        rule.mainClock.advanceTimeBy(160)
        rule.runOnIdle {
            assertTrue("A stationary finger at the edge must actually scroll", scroll.value > before)
            assertTrue("Do not accidentally test the content boundary", scroll.value < scroll.maxValue)
            assertNotEquals(original, drag.value!!.order)
        }
    }

    private fun assertScrollStopped() {
        rule.mainClock.advanceTimeByFrame()
        val stoppedAt = rule.runOnIdle { scroll.value }
        rule.mainClock.advanceTimeBy(240)
        rule.runOnIdle { assertEquals("No late edge frames after termination", stoppedAt, scroll.value) }
    }

    @Test fun unchangedDropRestoresSaveWithoutPersisting() {
        render()
        grabFirstHandle()
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText(S.saveApi).assertIsEnabled().performClick()
        rule.runOnIdle {
            assertNull(drag.value)
            assertEquals(0, reorderCalls)
            assertEquals(1, saves)
        }
        assertScrollStopped()
    }

    @Test fun changedDropStopsEdgeScrollAndRestoresSaveAfterSuccess() = changedDrop(success = true)

    @Test fun failedReorderAlsoRestoresSaveAndStopsEdgeScroll() = changedDrop(success = false)

    private fun changedDrop(success: Boolean) {
        render()
        grabFirstHandle()
        moveToBottomEdge()
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText(S.saveApi).assertIsNotEnabled()
        rule.runOnIdle {
            assertEquals(1, reorderCalls)
            assertEquals(listOf(original[1], original[0]), submitted)
            assertEquals(1, submittedSelection)
            assertEquals(-1, drag.value!!.held)
            assertNotNull(completion)
        }
        assertScrollStopped()
        rule.runOnIdle {
            if (success) {
                profiles.value = submitted!!
                selected.value = submittedSelection!!
            }
            completion!!.invoke(if (success) Result.success(Unit) else Result.failure(IllegalStateException("rejected")))
        }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText(S.saveApi).assertIsEnabled().performClick()
        rule.runOnIdle {
            assertNull(drag.value)
            assertEquals(if (success) submitted else original, profiles.value)
            assertEquals(1, saves)
        }
        assertScrollStopped()
    }

    @Test fun pointerCancelDiscardsReorderRestoresSaveAndStopsEdgeScroll() {
        render()
        grabFirstHandle()
        moveToBottomEdge()
        rule.onRoot().performTouchInput { cancel() }
        rule.mainClock.advanceTimeByFrame()
        assertCancelled()
    }

    @Test fun deactivationCancelsHeldGestureRestoresSaveAndStopsEdgeScroll() {
        render()
        grabFirstHandle()
        moveToBottomEdge()
        rule.runOnIdle { active.value = false }
        rule.mainClock.advanceTimeByFrame()
        rule.onRoot().performTouchInput { cancel() }
        assertCancelled()
    }

    @Test fun lazyListEdgeEffectStopsAfterRealGestureFinish() = lazyListEdgeTermination(cancelGesture = false)

    @Test fun lazyListEdgeEffectStopsAfterRealGestureCancel() = lazyListEdgeTermination(cancelGesture = true)

    private fun lazyListEdgeTermination(cancelGesture: Boolean) {
        lateinit var list: androidx.compose.foundation.lazy.LazyListState
        lateinit var state: ReorderDragState
        var drops = 0
        rule.setContent {
            list = rememberLazyListState()
            state = rememberReorderDragState()
            ReorderAutoScrollEffect(list, state)
            LazyColumn(Modifier.size(320.dp, 280.dp).testTag("lazy")
                .reorderableList(list, state, onLongPress = {}, onDrop = { _, _ -> drops++ }),
                state = list) {
                items(50, key = { it.toLong() }) {
                    Text("Item $it", Modifier.height(56.dp))
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("lazy").performTouchInput {
            down(Offset(centerX, height - 10f))
            advanceEventTime(700)
            moveBy(Offset.Zero)
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertTrue(state.dragging) }
        val before = rule.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        rule.mainClock.advanceTimeBy(160)
        rule.runOnIdle {
            assertNotEquals("Production edge effect must scroll before testing its stop", before,
                list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
            assertTrue(list.canScrollForward)
            assertTrue(state.moved)
        }
        rule.onNodeWithTag("lazy").performTouchInput { if (cancelGesture) cancel() else up() }
        rule.mainClock.advanceTimeByFrame()
        val stopped = rule.runOnIdle {
            assertFalse(state.dragging)
            assertEquals(if (cancelGesture) 0 else 1, drops)
            list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        }
        rule.mainClock.advanceTimeBy(240)
        rule.runOnIdle {
            assertEquals("Edge effect must not consume scroll after gesture termination", stopped,
                list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
        }
    }

    private fun assertCancelled() {
        rule.onNodeWithText(S.saveApi).assertIsEnabled().performClick()
        rule.runOnIdle {
            assertNull(drag.value)
            assertEquals(original, profiles.value)
            assertEquals(0, reorderCalls)
            assertNull(completion)
            assertEquals(1, saves)
        }
        assertScrollStopped()
    }
}
