package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceReaderActionsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun manageMenuRetainsBothCallbacksAndClosesAfterSelection() {
        var replacements = 0
        var removals = 0
        var previews = 0
        rule.setContent {
            MaterialTheme {
                SourceReaderActions(
                    enabled = true,
                    previewEnabled = true,
                    onReplace = { replacements++ },
                    onRemove = { removals++ },
                    onOpenPreview = { previews++ }
                )
            }
        }
        rule.onNodeWithContentDescription(S.sourcesManage).performClick()
        rule.onNodeWithText(S.sourcesReplace).assertIsDisplayed().performClick()
        rule.onNodeWithText(S.sourcesRemove).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.sourcesManage).performClick()
        rule.onNodeWithText(S.sourcesRemove).assertIsDisplayed().performClick()
        rule.onNodeWithText(S.sourcesReplace).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.runOnIdle {
            assertEquals(1, replacements)
            assertEquals(1, removals)
            assertEquals(1, previews)
        }
    }

    @Test fun disablingReaderDismissesMenuAndDisablesBothActions() {
        val active = mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                SourceReaderActions(active.value, true, {}, {}, {})
            }
        }
        rule.onNodeWithContentDescription(S.sourcesManage).performClick()
        rule.onNodeWithText(S.sourcesReplace).assertIsDisplayed()
        rule.runOnIdle { active.value = false }
        rule.onNodeWithText(S.sourcesReplace).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.sourcesManage).assertIsNotEnabled()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).assertIsNotEnabled()
        rule.runOnIdle { active.value = true }
        rule.onNodeWithText(S.sourcesReplace).assertDoesNotExist()
    }

    @Test fun bothActionsAndMenuRemainAccessibleOnNarrowLargeTextLayout() {
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(220.dp).height(300.dp)) {
                        SourceReaderActions(true, true, {}, {}, {})
                    }
                }
            }
        }
        rule.onNodeWithContentDescription(S.sourcesManage).assertIsDisplayed().performClick()
        rule.onNodeWithText(S.sourcesReplace).assertIsDisplayed()
        rule.onNodeWithText(S.sourcesRemove).assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).assertIsDisplayed()
    }

    @Test fun unavailableActionsStayHiddenAndPreviewRemainsAccessibleOnNarrowLayout() {
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(220.dp).height(160.dp)) {
                        SourceReaderActions(true, false, null, null, {})
                    }
                }
            }
        }
        rule.onNodeWithContentDescription(S.sourcesManage).assertDoesNotExist()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).assertIsDisplayed().assertIsNotEnabled()
    }
}
