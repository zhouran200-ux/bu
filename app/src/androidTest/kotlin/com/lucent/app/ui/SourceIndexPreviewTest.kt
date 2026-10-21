package com.lucent.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.Note
import com.lucent.app.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceIndexPreviewTest {
    @get:Rule val rule = createComposeRule()

    private val documentBody = listOf("# Chapter", "", "First paragraph.", "", "Second paragraph.")
        .joinToString(System.lineSeparator())

    private fun render() {
        rule.setContent {
            MaterialTheme {
                SourceReader(
                    notebookId = 1L,
                    note = Note(id = 7L, title = "Document", body = documentBody),
                    focus = null,
                    active = false,
                    emphasizedFocus = null,
                    onEmphasize = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription(S.sourceIndexPreviewOpen).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun entryOpensReadOnlyPreviewWithTaggedDocument() {
        render()
        rule.onNodeWithContentDescription(S.sourceIndexPreviewOpen).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(S.sourceIndexPreviewTitle).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            rule.onAllNodesWithText("〔S：1-1〕", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        rule.onNodeWithContentDescription(S.sourceIndexPreviewClose).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(S.sourceIndexPreviewTitle).fetchSemanticsNodes().isEmpty()
        }
    }
}
