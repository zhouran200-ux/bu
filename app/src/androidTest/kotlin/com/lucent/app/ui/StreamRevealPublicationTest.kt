package com.lucent.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test

class StreamRevealPublicationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun idleUpdatesAndRewritesPublishWithoutAnotherInput() {
        val text = mutableStateOf("initial")
        val streaming = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalBackgroundEnvironment provides BackgroundEnvironment(true, true)) {
                Text(rememberRevealedText(text.value, streaming.value), Modifier.testTag("revealed"))
            }
        }
        settle()
        rule.onNodeWithTag("revealed").assertTextEquals("initial")
        rule.runOnIdle { text.value = "initial plus one idle update" }
        settle()
        rule.onNodeWithTag("revealed").assertTextEquals(text.value)
        rule.runOnIdle { streaming.value = true; text.value = "stream starts once" }
        settle()
        rule.onNodeWithTag("revealed").assertTextEquals(text.value)
        rule.runOnIdle { text.value = "rewrite" }
        settle()
        rule.onNodeWithTag("revealed").assertTextEquals("rewrite")
        rule.runOnIdle { text.value = "rewrite with a final tail"; streaming.value = false }
        settle()
        rule.onNodeWithTag("revealed").assertTextEquals(text.value)
    }

    private fun settle() {
        repeat(80) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
    }
}
