package com.lucent.app.network

import com.lucent.app.data.AnthropicReasoningMapper
import com.lucent.app.data.ModelCapabilityRegistry
import com.lucent.app.data.ModelDefinition
import com.lucent.app.data.ReasoningCapability
import com.lucent.app.data.ReasoningControl
import com.lucent.app.data.ReasoningLevels
import com.lucent.app.data.ReasoningPlanBuilder
import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.ReasoningResolver
import com.lucent.app.data.anthropicOutputCap
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReasoningBoundaryRegressionTest {
    private fun payload(model: String, spec: ApiSpec, config: com.lucent.app.data.ResolvedReasoningConfig): JSONObject {
        val request = StreamingRequestBuilder.build("https://relay.example/v1", spec, "", model,
            listOf(ChatMessagePayload("user", "hello")), "", config, false).build()
        return JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
    }

    @Test fun bracketPrefixAndReasoningSuffixComposeWithoutChangingTheSentModel() {
        val model = "[RP]gpt-5.5-reasoning"
        val config = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "openai", model)
        assertEquals("xhigh", config.openAiEffort)
        val body = payload(model, ApiSpec.OPENAI, config)
        assertEquals(model, body.getString("model"))
        assertEquals("xhigh", body.getString("reasoning_effort"))
        assertTrue(ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "openai", "[RP]unknown-99-reasoning").isAuto)
    }

    @Test fun tieBreakingDoesNotDependOnMetadataOrderAndNoneIsARealLevel() {
        assertEquals("LOW", ReasoningLevels.supported("MEDIUM", listOf("HIGH", "LOW")))
        assertEquals("LOW", ReasoningLevels.supported("MEDIUM", listOf("LOW", "HIGH")))
        assertEquals("none", ReasoningLevels.supported("low", listOf("high", "none")))
        assertEquals("none", ReasoningLevels.supported("none", listOf("low", "none")))
    }

    @Test fun incompleteMetadataCannotInventMaximumEffortForUnknownModels() {
        assertNull(discoveredReasoning("audit-unknown-effort", ApiSpec.OPENAI, JSONObject("""{"control":"openai_effort"}""")))
        assertNotNull(discoveredReasoning("gpt-5.5", ApiSpec.OPENAI, JSONObject("""{"control":"openai_effort"}""")))
        assertNotNull(discoveredReasoning("audit-explicit-effort", ApiSpec.OPENAI,
            JSONObject("""{"control":"openai_effort","levels":["low","high"]}""")))
        assertEquals(ReasoningControl.NONE, discoveredReasoning("gpt-5.5", ApiSpec.OPENAI,
            JSONObject("""{"supported":false}"""))!!.reasoning.control)
    }

    @Test fun claudeLimitsMatchRegistryAliasesCaseAndSnapshots() {
        for (model in listOf("claude-opus-3", "CLAUDE-3-OPUS", "vendor/Claude-3-Opus-20240229")) {
            assertEquals(model, 4096, anthropicOutputCap(model))
            assertEquals(model, 4096, payload(model, ApiSpec.ANTHROPIC,
                ReasoningResolver.resolve(ReasoningPreset.AUTO, "anthropic", model)).getInt("max_tokens"))
        }
        for (model in listOf("claude45sonnet", "claude-4-5-sonnet", "[RP]CLAUDE-SONNET-4-5")) {
            assertEquals(model, 65536, anthropicOutputCap(model))
        }
    }

    @Test fun displayedBudgetIsTheBudgetThatSurvivesOutputCapClamping() {
        val url = "https://budget-audit.example/v1"
        val model = "claude-3-5-sonnet"
        val definition = ModelDefinition(model, ReasoningCapability(true, ReasoningControl.ANTHROPIC_BUDGET,
            thinkingType = "enabled", thinkingBudgetMax = 32768))
        ModelCapabilityRegistry.replaceDiscovered(listOf(definition), "anthropic", url)
        try {
            val config = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "anthropic", model, url)
            assertEquals(7168, config.claudeBudgetTokens)
            val body = payload(model, ApiSpec.ANTHROPIC, config)
            assertEquals(8192, body.getInt("max_tokens"))
            assertEquals(config.claudeBudgetTokens, body.getJSONObject("thinking").getInt("budget_tokens"))
            assertEquals("7168t", ReasoningPlanBuilder.build("anthropic", model, url).entries.last().second)
            assertTrue(AnthropicReasoningMapper().map(ReasoningPreset.MAXIMUM, definition).claudeBudgetTokens!! < 8192)
        } finally {
            ModelCapabilityRegistry.replaceDiscovered(emptyList(), "anthropic", url)
        }
    }

    @Test fun staleCrossProtocolConfigIsFilteredAtTheActualRequestBoundary() {
        val google = ReasoningResolver.resolve(ReasoningPreset.DEEP, "google", "gemini-2.5-pro")
        val claude = payload("gemini-2.5-pro", ApiSpec.ANTHROPIC, google)
        assertFalse(claude.has("thinking"))
        assertFalse(claude.has("output_config"))
        assertTrue(claude.has("max_tokens"))
        val nativeClaude = ReasoningResolver.resolve(ReasoningPreset.DEEP, "anthropic", "claude-opus-4-5")
        assertFalse(payload("claude-opus-4-5", ApiSpec.GOOGLE, nativeClaude).has("generationConfig"))
        assertTrue(ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "claude-opus-5-5",
            " https://api.anthropic.com/v1 ").isAuto)
    }
}
