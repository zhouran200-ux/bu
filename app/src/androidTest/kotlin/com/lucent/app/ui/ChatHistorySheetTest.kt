package com.lucent.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.chat.UiChatMessage
import com.lucent.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHistorySheetTest {
    @get:Rule
    val rule = createComposeRule()

    private fun waitForPreview(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun emptyHistoryShowsExplanationWithoutJumpActionsOrCallbacks() {
        val events = mutableListOf<String>()
        rule.setContent {
            ChatHistorySheet(
                messages = emptyList(),
                currentMessageId = null,
                streamingAnswerId = null,
                onMessageClick = { events += "message:$it" },
                onTop = { events += "top" },
                onBottom = { events += "bottom" },
                onDismiss = { events += "dismiss" }
            )
        }

        rule.onNodeWithText(S.historyEmpty).assertIsDisplayed()
        rule.onNodeWithText(S.historyCount(0)).assertIsDisplayed()
        rule.onNodeWithText(S.historyJumpTop).assertDoesNotExist()
        rule.onNodeWithText(S.historyJumpBottom).assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), events) }
    }

    @Test
    fun jumpAndMessageClicksReportCorrectCallbacksWithoutDismissing() {
        val events = mutableListOf<String>()
        val currentId = mutableStateOf<Long?>(11L)
        rule.setContent {
            ChatHistorySheet(
                messages = listOf(
                    UiChatMessage(11L, "user", "历史问题"),
                    UiChatMessage(22L, "assistant", "历史回答")
                ),
                currentMessageId = currentId.value,
                streamingAnswerId = null,
                onMessageClick = { events += "message:$it"; currentId.value = it },
                onTop = { events += "top" },
                onBottom = { events += "bottom" },
                onDismiss = { events += "dismiss" }
            )
        }

        rule.onNodeWithText(S.historyJumpTopHint).assertIsDisplayed()
        rule.onNodeWithText(S.historyJumpBottomHint).assertIsDisplayed()
        rule.onNodeWithText(S.historyJumpTop).performClick()
        rule.onNodeWithText(S.historyJumpBottom).performClick()
        waitForPreview("历史问题")
        waitForPreview("历史回答")
        rule.onNodeWithText("历史问题").assertIsSelected()
        rule.onNodeWithText("历史回答").performScrollTo().assertIsNotSelected().performClick()
        rule.onNodeWithText("历史回答").assertIsSelected().performClick()
        rule.onNodeWithText("历史问题").performScrollTo().assertIsNotSelected().performClick()
        rule.onNodeWithText(S.historyTitle).assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(listOf("top", "bottom", "message:22", "message:22", "message:11"), events)
        }
    }

    @Test
    fun summariesExposeBodyReasoningStreamingAndNoBodyWithCorrectMessageIds() {
        val clicked = mutableListOf<Long>()
        rule.setContent {
            ChatHistorySheet(
                messages = listOf(
                    UiChatMessage(1L, "user", "正文优先", reasoningText = "不应显示的推理"),
                    UiChatMessage(2L, "assistant", "", reasoningText = "仅有推理"),
                    UiChatMessage(3L, "assistant", "", reasoningText = "流式时隐藏推理"),
                    UiChatMessage(4L, "assistant", "")
                ),
                currentMessageId = null,
                streamingAnswerId = 3L,
                onMessageClick = { clicked += it },
                onTop = {},
                onBottom = {},
                onDismiss = { error("Reading a history row must not dismiss the sheet") }
            )
        }

        waitForPreview("正文优先")
        rule.onNodeWithText("正文优先").performScrollTo().performClick()
        rule.onNodeWithText("不应显示的推理").assertDoesNotExist()
        waitForPreview("仅有推理")
        rule.onNodeWithText("仅有推理").performScrollTo().performClick()
        waitForPreview(S.historyStreaming)
        rule.onNodeWithText(S.historyStreaming).performScrollTo().performClick()
        rule.onNodeWithText("流式时隐藏推理").assertDoesNotExist()
        waitForPreview(S.historyNoBody)
        rule.onNodeWithText(S.historyNoBody).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(1L, 2L, 3L, 4L), clicked) }
    }

    @Test
    fun openingHistoryPositionsTheCurrentMessageInView() {
        rule.setContent {
            ChatHistorySheet(
                messages = (1L..25L).map { UiChatMessage(it, "user", "问题 $it") },
                currentMessageId = 20L,
                streamingAnswerId = null,
                onMessageClick = {},
                onTop = {},
                onBottom = {},
                onDismiss = {}
            )
        }

        waitForPreview("问题 20")
        rule.onNodeWithText("问题 20").assertIsDisplayed().assertIsSelected()
        rule.onNodeWithText("问题 1").assertDoesNotExist()
    }
}
