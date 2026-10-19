package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.i18n.S
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomGlassComposerInteractionTest {
    @get:Rule
    val rule = createComposeRule()

    private class Host {
        val input = mutableStateOf("")
        val menu = mutableStateOf(false)
        val preset = mutableStateOf(ReasoningPreset.AUTO)
        val streaming = mutableStateOf(false)
        val profileName = mutableStateOf("")
        val menuChanges = mutableListOf<Boolean>()
        val selections = mutableListOf<ReasoningPreset>()
        val selectionMenuStates = mutableListOf<Boolean>()
        var history = 0
        var settings = 0
        var model = 0
        var sends = 0
        var stops = 0
        lateinit var scope: CoroutineScope
    }

    private fun mount(host: Host, width: Dp? = null, height: Dp? = null) {
        rule.setContent {
            host.scope = rememberCoroutineScope()
            MaterialTheme {
                val viewport = Modifier
                    .then(if (width != null) Modifier.width(width) else Modifier)
                    .then(if (height != null) Modifier.height(height) else Modifier)
                    .fillMaxSize()
                Box(viewport, contentAlignment = Alignment.BottomCenter) {
                    BottomGlassComposer(
                        inputText = host.input.value,
                        onInputChange = { host.input.value = it },
                        showReasoningMenu = host.menu.value,
                        onReasoningMenuChange = {
                            host.menuChanges += it
                            host.menu.value = it
                        },
                        onReasoningButtonBounds = {},
                        reasoningPreset = host.preset.value,
                        onSelectReasoning = {
                            host.selections += it
                            host.selectionMenuStates += host.menu.value
                            host.preset.value = it
                        },
                        onOpenHistory = { host.history++ },
                        onOpenDialogueSettings = { host.settings++ },
                        selectedProfileName = host.profileName.value,
                        onOpenModelPicker = { host.model++ },
                        streaming = host.streaming.value,
                        onSend = { host.sends++ },
                        onStop = { host.stops++ }
                    )
                }
            }
        }
    }

    private data class ComposerBounds(
        val composer: Rect,
        val input: Rect,
        val controls: Rect,
        val buttons: List<Rect>
    )

    private fun inputNode() =
        rule.onNodeWithTag("bottom_glass_composer_input", useUnmergedTree = true)

    private fun setDraft(host: Host, text: String) {
        inputNode().performSemanticsAction(SemanticsActions.SetText) { action ->
            assertTrue(action(androidx.compose.ui.text.AnnotatedString(text)))
        }
        rule.runOnIdle { assertEquals(text, host.input.value) }
        assertEquals(text, inputNode().fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }

    private fun textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        inputNode().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue(action(results))
        }
        return results.single()
    }

    private fun composerBounds(): ComposerBounds {
        fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val buttons = listOf(
            S.composerReasoning, S.composerHistory, S.composerSettings,
            S.composerExpandModels, S.composerSend
        ).map { description ->
            rule.onNodeWithContentDescription(description)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        return ComposerBounds(
            bounds("bottom_glass_composer"), bounds("bottom_glass_composer_input"),
            bounds("bottom_glass_composer_controls"), buttons
        ).also { measured ->
            assertTrue("The editor must have a visible viewport", measured.input.height > 0f)
            assertTrue("Controls must remain below the editor", measured.input.bottom <= measured.controls.top + 1f)
            assertTrue("Controls must remain inside the composer", measured.controls.bottom <= measured.composer.bottom + 1f)
            measured.buttons.forEach { button ->
                assertTrue("Each button must remain in the controls row", button.top >= measured.controls.top - 1f)
                assertTrue("No button may be clipped below the controls row", button.bottom <= measured.controls.bottom + 1f)
            }
        }
    }

    private fun assertSameRect(label: String, expected: Rect, actual: Rect) {
        assertEquals("$label left", expected.left, actual.left, 1f)
        assertEquals("$label top", expected.top, actual.top, 1f)
        assertEquals("$label right", expected.right, actual.right, 1f)
        assertEquals("$label bottom", expected.bottom, actual.bottom, 1f)
    }

    private fun assertControlsStationary(expected: ComposerBounds, actual: ComposerBounds) {
        assertSameRect("Controls", expected.controls, actual.controls)
        expected.buttons.zip(actual.buttons).forEachIndexed { index, (before, after) ->
            assertSameRect("Button $index", before, after)
        }
    }

    private fun assertSameGeometry(expected: ComposerBounds, actual: ComposerBounds) {
        assertSameRect("Composer", expected.composer, actual.composer)
        assertSameRect("Input", expected.input, actual.input)
        assertControlsStationary(expected, actual)
    }

    private fun lines(count: Int) = List(count) { "row" }.joinToString("\n")

    private fun scrollInput(host: Host, y: Float): Offset {
        val scroll = inputNode().fetchSemanticsNode().config[SemanticsActions.ScrollByOffset]
        val consumed = AtomicReference<Offset?>(null)
        rule.runOnIdle { host.scope.launch { consumed.set(scroll(Offset(0f, y))) } }
        rule.waitUntil(5_000) { consumed.get() != null }
        rule.waitForIdle()
        return requireNotNull(consumed.get())
    }

    @Test
    fun emptyBlankSingleAndTwoLinesReserveTheSameHeight() {
        val host = Host()
        mount(host)
        val empty = composerBounds()
        listOf("   ", "row", lines(2)).forEach { text ->
            setDraft(host, text)
            assertEquals(if (text.contains('\n')) 2 else 1, textLayout().lineCount)
            assertSameGeometry(empty, composerBounds())
        }
    }

    @Test
    fun thirdThroughFifthLinesGrowOneLineAtATimeAboveStationaryControls() {
        val host = Host()
        mount(host)
        setDraft(host, lines(2))
        var previous = composerBounds()
        val baseline = previous
        val lineHeight = with(rule.density) { 22.sp.toPx() }
        for (count in 3..5) {
            setDraft(host, lines(count))
            assertEquals(count, textLayout().lineCount)
            val current = composerBounds()
            assertEquals("Input grows by one line at $count lines", lineHeight, current.input.height - previous.input.height, 2f)
            assertEquals("Composer grows with its editor", lineHeight, current.composer.height - previous.composer.height, 2f)
            assertControlsStationary(baseline, current)
            previous = current
        }
    }

    @Test
    fun overflowCapsAtFiveLinesScrollsInternallyAndClearingRestoresDefaultHeight() {
        val host = Host()
        mount(host)
        val empty = composerBounds()
        setDraft(host, lines(5))
        val capped = composerBounds()
        assertTrue("Five lines must be taller than the default", capped.input.height > empty.input.height)
        listOf(6, 8, 12).forEach { count ->
            setDraft(host, lines(count))
            assertEquals("Overflow text must not be truncated", count, textLayout().lineCount)
            assertSameGeometry(capped, composerBounds())
        }
        assertTrue("Text must exceed the visible editor", textLayout().size.height > capped.input.height)
        val distance = textLayout().size.height.toFloat()
        scrollInput(host, -distance)
        val forward = scrollInput(host, distance)
        assertTrue("The editor must actually consume downward scroll", forward.y > 1f)
        assertEquals("Vertical input must not scroll horizontally", 0f, forward.x, 1f)
        assertSameGeometry(capped, composerBounds())
        val backward = scrollInput(host, -distance)
        assertTrue("The editor must actually consume upward scroll", backward.y < -1f)
        assertSameGeometry(capped, composerBounds())
        setDraft(host, "")
        assertSameGeometry(empty, composerBounds())
        rule.onNodeWithContentDescription(S.composerSend).assertIsNotEnabled()
        setDraft(host, "row")
        assertSameGeometry(empty, composerBounds())
    }

    @Test
    fun naturalWrappingUsesTheSameGrowthAndCapAsExplicitLineBreaks() {
        val host = Host()
        mount(host, width = 320.dp)
        setDraft(host, lines(3))
        val threeLines = composerBounds()
        setDraft(host, lines(5))
        val fiveLines = composerBounds()
        val paragraph = "word ".repeat(200)
        setDraft(host, paragraph)
        val wrapped = textLayout()
        assertTrue("The paragraph must naturally wrap beyond five lines", wrapped.lineCount > 5)
        assertTrue("The fixture must not contain explicit line breaks", !paragraph.contains('\n'))
        assertSameGeometry(fiveLines, composerBounds())
        val threeLineParagraph = paragraph.take(wrapped.getLineEnd(2, visibleEnd = true))
        val fiveLineParagraph = paragraph.take(wrapped.getLineEnd(4, visibleEnd = true))
        setDraft(host, threeLineParagraph)
        assertEquals(3, textLayout().lineCount)
        assertSameGeometry(threeLines, composerBounds())
        setDraft(host, fiveLineParagraph)
        assertEquals(5, textLayout().lineCount)
        assertSameGeometry(fiveLines, composerBounds())
    }

    @Test
    fun shortViewportKeepsControlsVisibleBelowOverflowingEditor() {
        val host = Host()
        mount(host, width = 320.dp, height = 120.dp)
        val empty = composerBounds()
        setDraft(host, lines(12))
        val overflowing = composerBounds()
        assertEquals(12, textLayout().lineCount)
        assertTrue("Composer must fit the short viewport", overflowing.composer.height <= with(rule.density) { 120.dp.toPx() } + 1f)
        assertTrue("Overflow remains inside the editor", textLayout().size.height > overflowing.input.height)
        assertControlsStationary(empty, overflowing)
        rule.onNodeWithContentDescription(S.composerSend).performClick()
        rule.runOnIdle { assertEquals(1, host.sends) }
    }

    @Test
    fun reasoningButtonTogglesMenuWithoutSelectingPreset() {
        val host = Host()
        mount(host)
        rule.onNodeWithText(S.composerReasoningTitle).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.composerReasoning).performClick()
        rule.onNodeWithText(S.composerReasoningTitle).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.composerReasoning).performClick()
        rule.onNodeWithText(S.composerReasoningTitle).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(listOf(true, false), host.menuChanges)
            assertEquals(emptyList<ReasoningPreset>(), host.selections)
        }
    }

    @Test
    fun everyPresetReportsTypedSelectionBeforeClosingPopup() {
        val host = Host()
        mount(host)
        ReasoningPreset.entries.forEach { preset ->
            rule.onNodeWithContentDescription(S.composerReasoning).performClick()
            rule.onNodeWithText(preset.label).performScrollTo().performClick()
            rule.onNodeWithText(S.composerReasoningTitle).assertDoesNotExist()
            rule.runOnIdle {
                assertEquals(preset, host.preset.value)
                assertEquals(false, host.menu.value)
            }
        }
        rule.runOnIdle {
            assertEquals(ReasoningPreset.entries.toList(), host.selections)
            assertEquals(List(ReasoningPreset.entries.size) { true }, host.selectionMenuStates)
            assertEquals(ReasoningPreset.entries.flatMap { listOf(true, false) }, host.menuChanges)
        }
    }

    @Test
    fun inputRetainsFocusAcrossNonFocusablePopupOpenAndTouchSelection() {
        val host = Host()
        mount(host)
        val input = rule.onNodeWithTag("bottom_glass_composer_input")
        input.performTouchInput { click() }
        input.performTextReplacement("draft before popup")
        input.assertIsFocused()
        rule.onNodeWithContentDescription(S.composerReasoning).performTouchInput { click() }
        rule.onNodeWithText(S.composerReasoningTitle).assertIsDisplayed()
        input.assertIsFocused()
        rule.onNodeWithText(ReasoningPreset.DEEP.label).performScrollTo()
            .performTouchInput { click() }
        rule.onNodeWithText(S.composerReasoningTitle).assertDoesNotExist()
        input.assertIsFocused().assertTextEquals("draft before popup")
        input.performTextReplacement("continue editing")
        rule.runOnIdle {
            assertEquals("continue editing", host.input.value)
            assertEquals(listOf(ReasoningPreset.DEEP), host.selections)
        }
    }

    @Test
    fun historySettingsAndModelEntrypointsDispatchOnlyTheirOwnCallbacks() {
        val host = Host()
        mount(host)
        rule.onNodeWithText(S.composerPickProfile).assertIsDisplayed()
        rule.onNodeWithContentDescription(S.composerHistory).performClick()
        rule.runOnIdle {
            assertEquals(1, host.history)
            assertEquals(0, host.settings)
            assertEquals(0, host.model)
        }
        rule.onNodeWithContentDescription(S.composerSettings).performClick()
        rule.runOnIdle {
            assertEquals(1, host.history)
            assertEquals(1, host.settings)
            assertEquals(0, host.model)
        }
        rule.onNodeWithContentDescription(S.composerExpandModels).performClick()
        rule.runOnIdle { host.profileName.value = "Selected profile" }
        rule.onNodeWithText("Selected profile").assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertEquals(1, host.history)
            assertEquals(1, host.settings)
            assertEquals(2, host.model)
            assertEquals(0, host.sends)
            assertEquals(0, host.stops)
            assertEquals(emptyList<Boolean>(), host.menuChanges)
        }
    }

    @Test
    fun blankDraftDisablesSendAndStreamingAlwaysDispatchesStopInstead() {
        val host = Host()
        mount(host)
        rule.onNodeWithContentDescription(S.composerSend).assertIsNotEnabled()
        rule.onNodeWithTag("bottom_glass_composer_input").performTextReplacement("   ")
        rule.onNodeWithContentDescription(S.composerSend).assertIsNotEnabled()
        rule.runOnIdle {
            assertEquals(0, host.sends)
            assertEquals(0, host.stops)
        }
        rule.onNodeWithTag("bottom_glass_composer_input").performTextReplacement("send this")
        rule.onNodeWithContentDescription(S.composerSend).performClick()
        rule.runOnIdle {
            assertEquals(1, host.sends)
            assertEquals(0, host.stops)
            host.streaming.value = true
        }
        rule.onNodeWithContentDescription(S.composerSend).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.composerStop).performClick()
        rule.runOnIdle {
            assertEquals(1, host.sends)
            assertEquals(1, host.stops)
            host.input.value = ""
        }
        rule.onNodeWithContentDescription(S.composerStop).performClick()
        rule.runOnIdle {
            assertEquals(1, host.sends)
            assertEquals(2, host.stops)
            host.streaming.value = false
        }
        rule.onNodeWithContentDescription(S.composerStop).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.composerSend).assertIsNotEnabled()
    }
}
