package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.Note
import com.lucent.app.data.NovelSourceIndex
import com.lucent.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceIndexHeaderLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun floatingInfoHasNoButtonsAndChapterStaysAboveOnNarrowLargeTextLayout() {
        val note = Note(
            id = 81L,
            title = "A very long source title that should not push the actions away",
            body = "# Chapter\n\nA paragraph for the model source preview."
        )
        val source = NovelSourceIndex.get(note)
        val rows = sourcePreviewRows(source.body, source.tagged)
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, 2f),
                    LocalBackgroundEnvironment provides BackgroundEnvironment(active = true, motionEnabled = false)
                ) {
                    Box(Modifier.width(260.dp).height(300.dp)) {
                        SourceIndexPreview(
                            source = source,
                            rows = rows,
                            listState = rememberLazyListState(),
                            active = true
                        )
                    }
                }
            }
        }
        rule.onNodeWithText(S.sourceIndexPreviewTitle).assertIsDisplayed()
        rule.onNodeWithText(source.title, substring = true).assertExists()
        rule.onNodeWithTag("source_model_chapter").assertIsDisplayed()
        rule.onAllNodes(hasClickAction()).assertCountEquals(0)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val info = rule.onNodeWithTag("source_model_info").fetchSemanticsNode().boundsInRoot
        val chapter = rule.onNodeWithTag("source_model_chapter").fetchSemanticsNode().boundsInRoot
        assertTrue(info.center.y > root.center.y)
        assertTrue(chapter.center.y < root.center.y)
        assertTrue(info.right <= root.right + 1f)
        assertTrue(info.bottom <= root.bottom + 1f)
        assertTrue(kotlin.math.abs(info.left - chapter.left) <= 1f)
    }
}
