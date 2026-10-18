package com.lucent.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatTextDerivationsComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun unrelatedStreamingUpdatesReuseSettledTextDerivationsAndEditsInvalidateThem() {
        val streamingText = mutableStateOf("one")
        val body = mutableStateOf("# Stable body")
        lateinit var preview: State<ChatHistoryPreview?>
        lateinit var count: State<Int?>
        rule.setContent {
            val previewState = rememberChatHistoryPreview(ChatHistoryPreviewInput(body.value, null, false))
            val countState = rememberChatBodyCharCount(body.value)
            SideEffect {
                preview = previewState
                count = countState
            }
            Text(streamingText.value)
            Text((previewState.value as? ChatHistoryPreview.Excerpt)?.text.orEmpty())
            Text(countState.value?.let { "count:$it" }.orEmpty())
        }
        waitForText("Stable body")
        waitForText("count:11")
        val initialPreview = preview
        val initialCount = count
        rule.runOnIdle { streamingText.value = "two" }
        waitForText("two")
        rule.runOnIdle {
            assertSame(initialPreview, preview)
            assertSame(initialCount, count)
            body.value = "Edited"
        }
        waitForText("Edited")
        waitForText("count:6")
        rule.runOnIdle {
            assertNotSame(initialPreview, preview)
            assertNotSame(initialCount, count)
        }
    }

    @Test fun streamingTokensKeepPreviousExcerptUntilNextThrottledResult() {
        val body = mutableStateOf("first")
        lateinit var preview: State<ChatHistoryPreview?>
        rule.setContent {
            val state = rememberChatHistoryPreview(ChatHistoryPreviewInput(body.value, null, true))
            SideEffect { preview = state }
            Text((state.value as? ChatHistoryPreview.Excerpt)?.text.orEmpty())
        }
        waitForText("first")
        val initial = preview
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { body.value = "first second" }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle {
            assertSame(initial, preview)
            org.junit.Assert.assertNotNull(preview.value)
        }
        rule.mainClock.autoAdvance = true
        waitForText("first second")
        rule.runOnIdle { assertEquals(ChatHistoryPreview.Excerpt("first second"), preview.value) }
    }

    private fun waitForText(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
