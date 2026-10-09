package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningThreeLayerTest {

    @Test
    fun testPresetFromKeyCompatibility() {
        assertEquals(ReasoningPreset.AUTO, ReasoningPreset.fromKey("auto"))
        assertEquals(ReasoningPreset.AUTO, ReasoningPreset.fromKey("default"))
        assertEquals(ReasoningPreset.AUTO, ReasoningPreset.fromKey("none"))
        assertEquals(ReasoningPreset.AUTO, ReasoningPreset.fromKey(null))
        assertEquals(ReasoningPreset.AUTO, ReasoningPreset.fromKey(""))

        assertEquals(ReasoningPreset.FAST, ReasoningPreset.fromKey("fast"))
        assertEquals(ReasoningPreset.FAST, ReasoningPreset.fromKey("low"))
        assertEquals(ReasoningPreset.FAST, ReasoningPreset.fromKey("minimal"))

        assertEquals(ReasoningPreset.BALANCED, ReasoningPreset.fromKey("balanced"))
        assertEquals(ReasoningPreset.BALANCED, ReasoningPreset.fromKey("medium"))

        assertEquals(ReasoningPreset.DEEP, ReasoningPreset.fromKey("deep"))
        assertEquals(ReasoningPreset.DEEP, ReasoningPreset.fromKey("high"))

        assertEquals(ReasoningPreset.MAXIMUM, ReasoningPreset.fromKey("maximum"))
        assertEquals(ReasoningPreset.MAXIMUM, ReasoningPreset.fromKey("max"))
        assertEquals(ReasoningPreset.MAXIMUM, ReasoningPreset.fromKey("xhigh"))
    }

    @Test
    fun testOpenAiReasoningResolution() {
        val autoConfig = ReasoningResolver.resolve(ReasoningPreset.AUTO, "openai", "gpt-6.1-sol")
        assertTrue(autoConfig.isAuto)
        assertFalse(autoConfig.hasNativeParam)
        assertNull(autoConfig.openAiEffort)
        assertNull(autoConfig.openAiMode)

        val fastConfig = ReasoningResolver.resolve(ReasoningPreset.FAST, "openai", "gpt-6.1-sol")
        assertEquals("low", fastConfig.openAiEffort)
        assertNull(fastConfig.openAiMode)

        val deepConfig = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "gpt-6.1-sol")
        assertEquals("high", deepConfig.openAiEffort)
        assertEquals("pro", deepConfig.openAiMode)

        val maxConfig = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "openai", "gpt-6.1-sol")
        assertEquals("xhigh", maxConfig.openAiEffort)
        assertEquals("pro", maxConfig.openAiMode)
    }

    @Test
    fun testClaudeReasoningResolution() {
        val autoConfig = ReasoningResolver.resolve(ReasoningPreset.AUTO, "anthropic", "claude-3-7-sonnet")
        assertTrue(autoConfig.isAuto)
        assertNull(autoConfig.claudeEffort)

        val fastConfig = ReasoningResolver.resolve(ReasoningPreset.FAST, "anthropic", "claude-3-7-sonnet")
        assertEquals("low", fastConfig.claudeEffort)

        val deepConfig = ReasoningResolver.resolve(ReasoningPreset.DEEP, "anthropic", "claude-3-7-sonnet")
        assertEquals("high", deepConfig.claudeEffort)

        val maxConfig = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "anthropic", "claude-3-7-sonnet")
        assertEquals("max", maxConfig.claudeEffort)
    }

    @Test
    fun testGeminiReasoningResolution() {
        val autoConfig = ReasoningResolver.resolve(ReasoningPreset.AUTO, "google", "gemini-3.5-flash")
        assertTrue(autoConfig.isAuto)
        assertNull(autoConfig.geminiThinkingLevel)

        val fastConfig = ReasoningResolver.resolve(ReasoningPreset.FAST, "google", "gemini-3.5-flash")
        assertEquals("LOW", fastConfig.geminiThinkingLevel)

        val balancedConfig = ReasoningResolver.resolve(ReasoningPreset.BALANCED, "google", "gemini-3.5-flash")
        assertEquals("MEDIUM", balancedConfig.geminiThinkingLevel)

        val deepConfig = ReasoningResolver.resolve(ReasoningPreset.DEEP, "google", "gemini-3.5-flash")
        assertEquals("HIGH", deepConfig.geminiThinkingLevel)

        val maxConfig = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "google", "gemini-3.5-flash")
        assertEquals("HIGH", maxConfig.geminiThinkingLevel)
    }

    @Test
    fun testDeepSeekReasoningResolution() {
        val autoConfig = ReasoningResolver.resolve(ReasoningPreset.AUTO, "deepseek", "deepseek-r1")
        assertTrue(autoConfig.isAuto)

        val fastConfig = ReasoningResolver.resolve(ReasoningPreset.FAST, "deepseek", "deepseek-r1")
        assertEquals("low", fastConfig.openAiEffort)

        val balancedConfig = ReasoningResolver.resolve(ReasoningPreset.BALANCED, "deepseek", "deepseek-r1")
        assertEquals("high", balancedConfig.openAiEffort)

        val maxConfig = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "deepseek", "deepseek-r1")
        assertEquals("max", maxConfig.openAiEffort)
    }

    @Test
    fun testUnknownModelZeroParamFallback() {
        val config = ReasoningResolver.resolve(ReasoningPreset.BALANCED, "custom", "my-private-model")
        assertTrue(config.isAuto)
        assertFalse(config.hasNativeParam)
        assertEquals("模型默认", config.displayTag)
    }

    @Test
    fun testModelCapabilityRegistry() {
        val gpt6 = ModelCapabilityRegistry.find("gpt-6.1-sol")
        assertTrue(gpt6 != null && gpt6.reasoning.supported)
        assertEquals(ReasoningControl.OPENAI_EFFORT, gpt6?.reasoning?.control)

        val gemini35 = ModelCapabilityRegistry.find("gemini-3.5-flash")
        assertTrue(gemini35 != null && gemini35.reasoning.supported)
        assertEquals(ReasoningControl.GEMINI_THINKING_LEVEL, gemini35?.reasoning?.control)

        val r1 = ModelCapabilityRegistry.find("deepseek-r1")
        assertTrue(r1 != null && r1.reasoning.supported)
        assertEquals(ReasoningControl.DEEPSEEK_EFFORT, r1?.reasoning?.control)

        val gpt4o = ModelCapabilityRegistry.find("gpt-4o")
        assertTrue(gpt4o != null && !gpt4o.reasoning.supported)
        assertEquals(ReasoningControl.NONE, gpt4o?.reasoning?.control)

        val gemini15 = ModelCapabilityRegistry.find("gemini-1.5-flash")
        assertTrue(gemini15 != null && !gemini15.reasoning.supported)

        val deepseekV3 = ModelCapabilityRegistry.find("deepseek-v3")
        assertTrue(deepseekV3 != null && !deepseekV3.reasoning.supported)

        val dynamicModel = ModelCapabilityRegistry.findOrRegister("custom-reasoner-v1", "openai")
        assertTrue(dynamicModel.reasoning.supported)
        assertEquals(ReasoningControl.OPENAI_EFFORT, dynamicModel.reasoning.control)

        val all = ModelCapabilityRegistry.getAll()
        assertTrue(all.size >= 15)
    }

    @Test
    fun testCustomThinkingBudget() {
        val claudeCustom = ReasoningResolver.resolve(
            preset = ReasoningPreset.DEEP,
            provider = "anthropic",
            model = "claude-3-7-sonnet",
            customBudgetTokens = 8192
        )
        assertEquals(8192, claudeCustom.claudeBudgetTokens)
        assertTrue(claudeCustom.displayTag.contains("8192t"))

        val geminiCustom = ReasoningResolver.resolve(
            preset = ReasoningPreset.BALANCED,
            provider = "google",
            model = "gemini-3.5-flash",
            customBudgetTokens = 4096
        )
        assertEquals(4096, geminiCustom.geminiThinkingBudget)
        assertTrue(geminiCustom.displayTag.contains("4096t"))

        val autoCustom = ReasoningResolver.resolve(
            preset = ReasoningPreset.AUTO,
            provider = "anthropic",
            model = "claude-3-7-sonnet",
            customBudgetTokens = 16384
        )
        assertFalse(autoCustom.isAuto)
        assertEquals(16384, autoCustom.claudeBudgetTokens)
        assertTrue(autoCustom.displayTag.contains("16384t"))
    }
}
