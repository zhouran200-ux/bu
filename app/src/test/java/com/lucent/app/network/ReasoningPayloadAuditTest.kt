package com.lucent.app.network

import com.lucent.app.data.ReasoningPreset
import com.lucent.app.data.ReasoningResolver
import com.lucent.app.data.ResolvedReasoningConfig
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReasoningPayloadAuditTest {
    private fun payload(model: String, spec: ApiSpec, preset: ReasoningPreset = ReasoningPreset.DEEP,
                        url: String = "https://example.test/v1", config: ResolvedReasoningConfig? = null): JSONObject {
        val selected = config ?: ReasoningResolver.resolve(preset, spec.name, model, url)
        val request = StreamingRequestBuilder.build(url, spec, "key", model,
            listOf(ChatMessagePayload("user", "hello")), "", selected, false).build()
        return JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
    }

    @Test fun googleNativeAndCompatibilityKeepTheSameExactBudget() {
        val native = payload("gemini-2.5-pro", ApiSpec.GOOGLE, ReasoningPreset.MAXIMUM)
            .getJSONObject("generationConfig").getJSONObject("thinkingConfig")
        assertEquals(32768, native.getInt("thinkingBudget"))
        assertTrue(native.getBoolean("includeThoughts"))
        val compatible = payload("gemini-2.5-pro", ApiSpec.OPENAI, ReasoningPreset.MAXIMUM,
            "https://generativelanguage.googleapis.com/v1beta/openai")
        val thinking = compatible.getJSONObject("extra_body").getJSONObject("google").getJSONObject("thinking_config")
        assertEquals(32768, thinking.getInt("thinking_budget"))
        assertTrue(thinking.getBoolean("include_thoughts"))
        assertFalse(compatible.has("reasoning_effort"))
        assertFalse(thinking.has("thinking_level"))
    }

    @Test fun googleLevelIsSerializedOnceAndUnsupportedImageHasNoControl() {
        val compatible = payload("gemini-3.8-flash", ApiSpec.OPENAI)
        val thinking = compatible.getJSONObject("extra_body").getJSONObject("google").getJSONObject("thinking_config")
        assertEquals("high", thinking.getString("thinking_level"))
        assertFalse(thinking.has("thinking_budget"))
        assertFalse(payload("gemini-2.5-flash-image", ApiSpec.GOOGLE).has("generationConfig"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun googleRejectsMixedLevelAndBudget() {
        payload("gemini-3.8-flash", ApiSpec.GOOGLE, config = ResolvedReasoningConfig(
            ReasoningPreset.DEEP, geminiThinkingLevel = "HIGH", geminiThinkingBudget = 8192))
    }

    @Test fun oldQwenUsesEnabledAndBudgetWhileThreeEightUsesOnlyEffort() {
        val old = payload("qwen-plus", ApiSpec.OPENAI)
        assertTrue(old.getBoolean("enable_thinking"))
        assertEquals(16384, old.getInt("thinking_budget"))
        assertFalse(old.has("reasoning_effort"))
        val current = payload("qwen3.8-max", ApiSpec.OPENAI)
        assertEquals("xhigh", current.getString("reasoning_effort"))
        assertFalse(current.has("thinking_budget"))
    }

    @Test fun nativeClaudeAdaptiveAndLegacyEffortBudgetAreNotConfused() {
        val adaptive = payload("claude-sonnet-4-6", ApiSpec.ANTHROPIC, ReasoningPreset.MAXIMUM)
        assertEquals("max", adaptive.getJSONObject("output_config").getString("effort"))
        assertEquals("adaptive", adaptive.getJSONObject("thinking").getString("type"))
        assertEquals("summarized", adaptive.getJSONObject("thinking").getString("display"))
        val legacy = payload("claude-opus-4-5", ApiSpec.ANTHROPIC)
        assertEquals("high", legacy.getJSONObject("output_config").getString("effort"))
        assertEquals("enabled", legacy.getJSONObject("thinking").getString("type"))
        assertEquals(16384, legacy.getJSONObject("thinking").getInt("budget_tokens"))
        assertTrue(legacy.getInt("max_tokens") > 16384)
    }

    @Test fun officialClaudeCompatibilityDoesNotPretendIgnoredEffortIsApplied() {
        val url = "https://api.anthropic.com/v1"
        val config = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "claude-opus-5-5", url)
        assertTrue(config.isAuto)
        assertTrue(config.displayTag.contains("原生"))
        val body = payload("claude-opus-5-5", ApiSpec.OPENAI, url = url)
        assertFalse(body.has("reasoning_effort"))
        assertFalse(body.has("output_config"))
        val budget = payload("claude-sonnet-4-5", ApiSpec.OPENAI, url = url)
        assertEquals(16384, budget.getJSONObject("thinking").getInt("budget_tokens"))
    }

    @Test fun mimoSendsItsToggleInsteadOfAnEffortLevel() {
        val deep = payload("mimo-v2.6-flash", ApiSpec.OPENAI)
        assertEquals("enabled", deep.getJSONObject("thinking").getString("type"))
        assertFalse(deep.has("reasoning_effort"))
        val automatic = payload("mimo-v2.6-flash", ApiSpec.OPENAI, ReasoningPreset.AUTO)
        assertFalse(automatic.has("thinking"))
    }

    @Test fun automaticModeNeverAddsThinkingOverridesAcrossVendors() {
        for ((model, spec) in listOf("gpt-6-astra" to ApiSpec.OPENAI,
            "qwen-plus" to ApiSpec.OPENAI, "claude-opus-5-5" to ApiSpec.ANTHROPIC,
            "gemini-2.5-flash-lite" to ApiSpec.GOOGLE)) {
            val body = payload(model, spec, ReasoningPreset.AUTO)
            for (field in listOf("thinking", "output_config", "reasoning_effort", "thinking_budget",
                "enable_thinking", "generationConfig", "extra_body")) assertFalse("$model $field", body.has(field))
        }
    }
}
