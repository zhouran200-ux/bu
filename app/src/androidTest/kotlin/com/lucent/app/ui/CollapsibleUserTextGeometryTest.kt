package com.lucent.app.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollapsibleUserTextGeometryTest {
    @get:Rule val rule = createComposeRule()

    private val mounted = mutableStateOf(true)
    private val measuredSizes = mutableListOf<IntSize>()
    private val text = (1..7).joinToString("\n") { "Line $it" }

    @Test fun collapsedFirstMeasurementAlreadyIncludesToggleOnInitialMountAndRemount() {
        assertMountAndRemountHaveStableGeometry(expanded = false)
    }

    @Test fun expandedFirstMeasurementAlreadyIncludesToggleOnInitialMountAndRemount() {
        assertMountAndRemountHaveStableGeometry(expanded = true)
    }

    private fun assertMountAndRemountHaveStableGeometry(expanded: Boolean) {
        val label = if (expanded) S.userMessageCollapse else S.userMessageExpand
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    if (mounted.value) {
                        CollapsibleUserText(
                            text = text,
                            expanded = expanded,
                            onExpandedChange = {},
                            modifier = Modifier.width(180.dp).onSizeChanged { measuredSizes += it }
                        )
                    }
                }
            }
        }
        val initialSize = assertSinglePublishedSizeIncludesToggle(label, expanded)

        rule.runOnIdle { mounted.value = false }
        rule.waitForIdle()
        rule.onNodeWithText(text).assertDoesNotExist()
        rule.runOnIdle {
            measuredSizes.clear()
            mounted.value = true
        }
        val remountedSize = assertSinglePublishedSizeIncludesToggle(label, expanded)
        assertEquals(initialSize, remountedSize)
    }

    private fun assertSinglePublishedSizeIncludesToggle(label: String, expanded: Boolean): IntSize {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(label).fetchSemanticsNodes().size == 1
        }
        rule.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(layouts))
            }
        val textLayout = layouts.single()
        assertEquals(if (expanded) 7 else 6, textLayout.lineCount)
        return rule.runOnIdle {
            assertTrue(measuredSizes.isNotEmpty())
            val first = measuredSizes.first()
            assertEquals(listOf(first), measuredSizes.distinct())
            assertTrue(first.height >= textLayout.size.height + 40)
            first
        }
    }
}
