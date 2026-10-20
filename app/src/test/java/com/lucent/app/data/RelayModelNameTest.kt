package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayModelNameTest {
    private fun max(model: String, provider: String = "openai"): ResolvedReasoningConfig =
        ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, provider, model, "https://relay.example/v1")

    @Test
    fun gatewayVendorPrefixesResolveToTheAuditedEntries() {
        val glm = max("zai-org/GLM-5.3-Flash")
        assertEquals("max", glm.openAiEffort)
        assertEquals(ReasoningControl.ZHIPU_EFFORT,
            ModelCapabilityRegistry.find("zai-org/GLM-5.3-Flash")?.reasoning?.control)

        val deepSeek = max("deepseek-ai/DeepSeek-V4.1-Flash")
        assertEquals("max", deepSeek.openAiEffort)
        assertEquals(ReasoningControl.DEEPSEEK_EFFORT,
            ModelCapabilityRegistry.find("deepseek-ai/DeepSeek-V4.1-Flash")?.reasoning?.control)

        val unslashed = max("zai-org-GLM-5.3-Flash")
        assertEquals("max", unslashed.openAiEffort)
    }

    @Test
    fun bracketTagsAndShortPrefixesDoNotHideTheModel() {
        val claude = ReasoningResolver.resolve(
            ReasoningPreset.MAXIMUM, "anthropic", "[RP]claude-opus-4.6", "https://relay.example/v1"
        )
        assertEquals("max", claude.claudeEffort)
        assertEquals("adaptive", claude.claudeThinkingType)
        assertTrue(claude.claudeBudgetTokens == null)

        val glm52 = max("zhiqu-Glm-5.2")
        assertEquals("max", glm52.openAiEffort)
        assertEquals(listOf("high", "max"),
            ModelCapabilityRegistry.find("zhiqu-Glm-5.2")?.reasoning?.levels)

        val gpt = max("[RP]GPT-6.1-Sol")
        assertEquals("max", gpt.openAiEffort)
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"),
            ModelCapabilityRegistry.find("[RP]GPT-6.1-Sol")?.reasoning?.levels)
    }

    @Test
    fun relayAffixesNeverInventCapabilitiesForUnknownModels() {
        for (name in listOf("my-own-glm-5.3", "[RP]totally-unknown", "relay-zzz-9", "[X]custom-thinker")) {
            val definition = ModelCapabilityRegistry.findOrRegister(name, "openai")
            assertEquals(name, ReasoningControl.NONE, definition.reasoning.control)
            assertTrue(name, max(name).isAuto)
            assertNull(name, max(name).openAiEffort)
        }
    }

    @Test
    fun strippingStillHonoursRetiredAndUnsupportedModels() {
        assertEquals(ReasoningControl.NONE,
            ModelCapabilityRegistry.findOrRegister("[RP]claude-3-5-sonnet", "openai").reasoning.control)
        assertTrue(max("[RP]gemini-2.5-flash-image").isAuto ||
            ModelCapabilityRegistry.findOrRegister("[RP]gemini-2.5-flash-image", "google").reasoning.control == ReasoningControl.NONE)
    }
}
