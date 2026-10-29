package com.lucent.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucent.app.data.ModelCapabilityRegistry
import com.lucent.app.data.ModelDefinition
import com.lucent.app.data.ReasoningCapability
import com.lucent.app.data.ReasoningControl
import com.lucent.app.i18n.S
import com.lucent.app.ui.settings.ApiReasoningSummarySection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiReasoningSummaryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun metadataRefreshUpdatesTheSummaryWithoutChangingModelOrEndpoint() {
        val endpoint = "https://summary-refresh.example/v1"
        val model = "audit-summary-refresh"
        rule.setContent {
            ApiReasoningSummarySection("openai", model, endpoint)
        }
        rule.onNodeWithText(S.apiReasoningUnsupported, substring = true).assertIsDisplayed()
        try {
            rule.runOnIdle {
                ModelCapabilityRegistry.replaceDiscovered(listOf(ModelDefinition(model,
                    ReasoningCapability(true, ReasoningControl.OPENAI_EFFORT, levels = listOf("low", "high")))),
                    "openai", endpoint)
            }
            rule.onNodeWithText(S.apiReasoningControl("OpenAI effort", "low / high")).assertIsDisplayed()
            rule.runOnIdle { ModelCapabilityRegistry.replaceDiscovered(emptyList(), "openai", endpoint) }
            rule.onNodeWithText(S.apiReasoningUnsupported, substring = true).assertIsDisplayed()
        } finally {
            rule.runOnIdle { ModelCapabilityRegistry.replaceDiscovered(emptyList(), "openai", endpoint) }
        }
    }
}
