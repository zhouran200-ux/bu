package com.lucent.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucent.app.data.ApiProfile
import com.lucent.app.i18n.S
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedDrawerInteractionTest {
    @get:Rule
    val rule = createComposeRule()

    private enum class Picker { Profile, Api }

    private val dismissals = AtomicInteger(0)
    private val businessCallbacks = AtomicInteger(0)
    private val models = (1..60).map { "交互模型 $it" }
    private val handleTag = "model_picker_drawer_handle"

    @Test fun profileSystemBackDismissesOnceWithoutSelecting() = systemBack(Picker.Profile)
    @Test fun apiSystemBackDismissesOnceWithoutSaving() = systemBack(Picker.Api)
    @Test fun profileOutsideScrimDismissesOnceWithoutSelecting() = outsideScrim(Picker.Profile)
    @Test fun apiOutsideScrimDismissesOnceWithoutSaving() = outsideScrim(Picker.Api)
    @Test fun profileTitleDragBeyondThresholdDismisses() = titleDrag(Picker.Profile)
    @Test fun apiTitleDragBeyondThresholdDismisses() = titleDrag(Picker.Api)
    @Test fun profileSmallSlowHandleDragReboundsWithoutClosing() = smallSlowDrag(Picker.Profile)
    @Test fun apiSmallSlowHandleDragReboundsWithoutClosing() = smallSlowDrag(Picker.Api)
    @Test fun profileListScrollAndTopOverscrollNeverDismiss() = listScroll(Picker.Profile)
    @Test fun apiListScrollAndTopOverscrollNeverDismiss() = listScroll(Picker.Api)

    private fun show(picker: Picker) {
        val open = mutableStateOf(true)
        rule.setContent {
            Box(Modifier.fillMaxSize())
            if (open.value) {
                val onDismiss = {
                    dismissals.incrementAndGet()
                    open.value = false
                }
                when (picker) {
                    Picker.Profile -> ProfileModelPickerSheet(
                        profiles = listOf(ApiProfile(id = "interaction", name = "交互方案", selectedModels = models)),
                        currentProfileId = "interaction",
                        currentModel = models.first(),
                        onSelect = { _, _ -> businessCallbacks.incrementAndGet() },
                        onDismiss = onDismiss,
                        onManageApi = { businessCallbacks.incrementAndGet() }
                    )
                    Picker.Api -> ApiModelPickerDialog(
                        names = models,
                        selected = setOf(models.first()),
                        onDone = { businessCallbacks.incrementAndGet() },
                        onDismiss = onDismiss
                    )
                }
            }
        }
        handle().assertIsDisplayed()
        rule.waitForIdle()
    }

    private fun handle() = rule.onNodeWithTag(handleTag, useUnmergedTree = true)

    private fun title(picker: Picker) = rule.onNodeWithText(
        if (picker == Picker.Profile) S.pickerTitle else S.apiModelsTitle
    )

    private fun assertDismissed(picker: Picker) {
        rule.waitUntil(5_000) { dismissals.get() > 0 }
        rule.waitForIdle()
        title(picker).assertDoesNotExist()
        handle().assertDoesNotExist()
        assertEquals("A single interaction must dismiss exactly once", 1, dismissals.get())
        assertEquals("Dismissal must not select, save or navigate", 0, businessCallbacks.get())
    }

    private fun assertOpen(picker: Picker) {
        rule.waitForIdle()
        title(picker).assertIsDisplayed()
        handle().assertIsDisplayed()
        assertEquals(0, dismissals.get())
        assertEquals(0, businessCallbacks.get())
    }

    private fun systemBack(picker: Picker) {
        show(picker)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        assertDismissed(picker)
    }

    private fun outsideScrim(picker: Picker) {
        show(picker)
        val window = rule.onNode(
            isRoot() and hasAnyDescendant(hasTestTag(handleTag)),
            useUnmergedTree = true
        )
        val windowBounds = window.fetchSemanticsNode().boundsInRoot
        val sheetTop = handle().fetchSemanticsNode().boundsInRoot.top - windowBounds.top
        assertTrue("Fixture must leave an exposed scrim above the sheet", sheetTop > 2f)
        window.performTouchInput {
            down(Offset(center.x, sheetTop / 2f))
            up()
        }
        assertDismissed(picker)
    }

    private fun titleDrag(picker: Picker) {
        show(picker)
        val distance = with(rule.density) { 120.dp.toPx() }
        title(picker).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, distance / 20f), delayMillis = 40) }
            advanceEventTime(200)
            up()
        }
        assertDismissed(picker)
    }

    private fun smallSlowDrag(picker: Picker) {
        show(picker)
        val initialTop = handle().fetchSemanticsNode().boundsInRoot.top
        val distance = with(rule.density) { 40.dp.toPx() }
        handle().performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, distance / 20f), delayMillis = 40) }
        }
        rule.waitForIdle()
        val draggedTop = handle().fetchSemanticsNode().boundsInRoot.top
        assertTrue("The gesture must actually move the sheet, not merely miss it", draggedTop > initialTop + 1f)
        handle().performTouchInput {
            advanceEventTime(200)
            up()
        }
        assertOpen(picker)
        val reboundTop = handle().fetchSemanticsNode().boundsInRoot.top
        assertEquals("Sub-threshold drag must return to its starting position", initialTop, reboundTop, 1f)
    }

    private fun scrollPosition(list: SemanticsNodeInteraction): Float {
        val range = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        return rule.runOnIdle { range.value() }
    }

    private fun listScroll(picker: Picker) {
        show(picker)
        val list = rule.onNode(hasScrollAction())
        val initial = scrollPosition(list)
        list.performTouchInput {
            swipe(Offset(center.x, height * 0.8f), Offset(center.x, height * 0.2f), 600)
        }
        assertOpen(picker)
        val afterUp = scrollPosition(list)
        assertTrue("Upward gesture must scroll actual list content", afterUp > initial)
        list.performTouchInput {
            swipe(Offset(center.x, height * 0.2f), Offset(center.x, height * 0.8f), 600)
        }
        assertOpen(picker)
        assertTrue("Downward gesture must scroll back rather than move the drawer", scrollPosition(list) < afterUp)

        list.performScrollToIndex(0)
        rule.waitForIdle()
        val initialTop = handle().fetchSemanticsNode().boundsInRoot.top
        list.performTouchInput {
            swipe(Offset(center.x, height * 0.2f), Offset(center.x, height * 0.8f), 600)
        }
        assertOpen(picker)
        assertEquals(initialTop, handle().fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
