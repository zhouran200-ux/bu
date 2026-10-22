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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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
class DocumentScrollCapsuleInteractionTest {
    @get:Rule val rule = createComposeRule()

    private class Host {
        val state = LazyListState(2, 0)
        val enabled = mutableStateOf(true)
    }

    @Test fun accessibilitySeeksChapterAndMeasuredItemInterior() {
        val host = mount()
        seek(0.5f)
        rule.runOnIdle {
            assertEquals(10, host.state.firstVisibleItemIndex)
            assertEquals(0, host.state.firstVisibleItemScrollOffset)
        }
        seek(0.525f)
        rule.runOnIdle {
            assertEquals(10, host.state.firstVisibleItemIndex)
            val size = host.state.layoutInfo.visibleItemsInfo.first { it.index == 10 }.size
            assertEquals(size / 2f, host.state.firstVisibleItemScrollOffset.toFloat(), 2f)
        }
        seek(1f)
        rule.runOnIdle { assertFalse(host.state.canScrollForward) }
        seek(0f)
        rule.runOnIdle { assertFalse(host.state.canScrollBackward) }
    }

    @Test fun disablingHeldCapsuleCancelsFurtherPointerSeeks() {
        val host = mount()
        val root = rule.onNodeWithTag("document_host")
        val point = capsule().fetchSemanticsNode().boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performTouchInput { down(point); moveTo(point + Offset(0f, 80f)) }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle { host.enabled.value = false }
        rule.mainClock.advanceTimeBy(48)
        capsule().assertDoesNotExist()
        val index = host.state.firstVisibleItemIndex
        val offset = host.state.firstVisibleItemScrollOffset
        root.performTouchInput { moveTo(point + Offset(0f, 160f)); up() }
        rule.mainClock.advanceTimeBy(48)
        rule.runOnIdle {
            assertEquals(index, host.state.firstVisibleItemIndex)
            assertEquals(offset, host.state.firstVisibleItemScrollOffset)
        }
    }

    private fun mount(): Host {
        val host = Host()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                val keys = List(21) { "item:$it" }
                Box(Modifier.width(300.dp).height(400.dp).testTag("document_host")) {
                    LazyColumn(state = host.state, modifier = Modifier.fillMaxSize()) {
                        items(keys.size, key = { keys[it] }) { index ->
                            Box(Modifier.fillMaxWidth().height(if (index == 10) 800.dp else 200.dp))
                        }
                    }
                    DocumentScrollCapsule(host.state, keys, listOf(0, 10), host.enabled.value,
                        "document_capsule", Modifier.align(Alignment.CenterEnd))
                }
                LaunchedEffect(host.state) { host.state.scroll { awaitCancellation() } }
            }
        }
        rule.mainClock.advanceTimeBy(200)
        capsule().assertIsDisplayed()
        return host
    }

    private fun capsule() = rule.onNodeWithContentDescription("document_capsule")

    private fun seek(fraction: Float) {
        capsule().performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(fraction)) }
        rule.mainClock.advanceTimeBy(64)
    }
}
