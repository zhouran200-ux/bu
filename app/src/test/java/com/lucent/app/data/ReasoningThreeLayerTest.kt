package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertNull(autoConfig.openAiEffort)

        val fastConfig = ReasoningResolver.resolve(ReasoningPreset.FAST, "openai", "gpt-6.1-sol")
        assertEquals("low", fastConfig.openAiEffort)

        val deepConfig = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "gpt-6.1-sol")
        assertEquals("high", deepConfig.openAiEffort)

        val maxConfig = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "openai", "gpt-6.1-sol")
        assertEquals("max", maxConfig.openAiEffort)
    }

    @Test
    fun testClaudeReasoningResolution() {
        val autoConfig = ReasoningResolver.resolve(ReasoningPreset.AUTO, "anthropic", "claude-opus-5-5")
        assertTrue(autoConfig.isAuto)
        assertNull(autoConfig.claudeEffort)

        val fastConfig = ReasoningResolver.resolve(ReasoningPreset.FAST, "anthropic", "claude-opus-5-5")
        assertEquals("low", fastConfig.claudeEffort)

        val balancedConfig = ReasoningResolver.resolve(ReasoningPreset.BALANCED, "anthropic", "claude-opus-5-5")
        assertEquals("medium", balancedConfig.claudeEffort)

        val deepConfig = ReasoningResolver.resolve(ReasoningPreset.DEEP, "anthropic", "claude-opus-5-5")
        assertEquals("high", deepConfig.claudeEffort)

        val maxConfig = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "anthropic", "claude-opus-5-5")
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
        assertEquals("模型默认", config.displayTag)
    }

    @Test
    fun openAiCompatibleProxyResolvesOtherVendorsByModelName() {
        val claude35 = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "claude-3-5-sonnet")
        assertTrue(claude35.claudeBudgetTokens != null)
        assertNull(claude35.openAiEffort)

        val claude37 = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "claude-3-7-sonnet")
        assertEquals("high", claude37.claudeEffort)
        assertNull(claude37.openAiEffort)

        val gemini = ReasoningResolver.resolve(ReasoningPreset.BALANCED, "openai", "gemini-2.5-flash")
        assertNull(gemini.geminiThinkingLevel)
        assertEquals(8192, gemini.geminiThinkingBudget)
        assertNull(gemini.openAiEffort)

        val gemini15 = ReasoningResolver.resolve(ReasoningPreset.BALANCED, "openai", "gemini-1.5-flash")
        assertTrue(gemini15.isAuto)

        val r1 = ReasoningResolver.resolve(ReasoningPreset.FAST, "openai", "deepseek-r1")
        assertEquals("low", r1.openAiEffort)
        assertNull(r1.claudeEffort)

        val qwen = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "qwen-max")
        assertEquals("xhigh", qwen.openAiEffort)
    }

    @Test
    fun genericOpenAiModelsStillUseEffortMapping() {
        val o3 = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "o3")
        assertEquals("high", o3.openAiEffort)

        val gpt6 = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "openai", "gpt-6.1-sol")
        assertEquals("max", gpt6.openAiEffort)

        val customReasoner = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "custom-reasoner-v1")
        assertEquals("high", customReasoner.openAiEffort)
    }

    @Test
    fun testModelCapabilityRegistry() {
        val gpt6 = ModelCapabilityRegistry.find("gpt-6.1-sol")
        assertTrue(gpt6 != null && gpt6.reasoning.supported)
        assertEquals(ReasoningControl.OPENAI_EFFORT, gpt6?.reasoning?.control)
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), gpt6?.reasoning?.levels)

        val gemini = ModelCapabilityRegistry.find("gemini-3.8-flash")
        assertTrue(gemini != null && gemini.reasoning.supported)
        assertEquals(ReasoningControl.GEMINI_THINKING_LEVEL, gemini?.reasoning?.control)

        val deepseek = ModelCapabilityRegistry.find("deepseek-v4-pro")
        assertTrue(deepseek != null && deepseek.reasoning.supported)
        assertEquals(ReasoningControl.DEEPSEEK_EFFORT, deepseek?.reasoning?.control)

        val claude = ModelCapabilityRegistry.find("claude-opus-5-5")
        assertEquals(ReasoningControl.ANTHROPIC_EFFORT, claude?.reasoning?.control)

        val glm = ModelCapabilityRegistry.find("glm-5.3")
        assertEquals(ReasoningControl.ZHIPU_EFFORT, glm?.reasoning?.control)
        assertEquals(listOf("low", "high", "max"), glm?.reasoning?.levels)

        val qwen = ModelCapabilityRegistry.find("qwen3.8-max")
        assertEquals(ReasoningControl.QWEN_EFFORT, qwen?.reasoning?.control)

        val gpt4o = ModelCapabilityRegistry.findOrRegister("gpt-4o", "openai")
        assertTrue(!gpt4o.reasoning.supported)
        assertEquals(ReasoningControl.NONE, gpt4o.reasoning.control)

        val gemini15 = ModelCapabilityRegistry.findOrRegister("gemini-1.5-flash", "google")
        assertTrue(!gemini15.reasoning.supported)

        val deepseekV3 = ModelCapabilityRegistry.findOrRegister("deepseek-v3", "deepseek")
        assertTrue(!deepseekV3.reasoning.supported)

        val dynamicModel = ModelCapabilityRegistry.findOrRegister("custom-reasoner-v1", "openai")
        assertTrue(dynamicModel.reasoning.supported)
        assertEquals(ReasoningControl.OPENAI_EFFORT, dynamicModel.reasoning.control)

        val all = ModelCapabilityRegistry.getAll()
        assertTrue(all.size >= 15)
    }

    @Test
    fun geminiUsesOnlyTheLevelsTheModelPublishes() {
        val proBalanced = ReasoningResolver.resolve(ReasoningPreset.BALANCED, "google", "gemini-3-pro-preview")
        assertEquals("LOW", proBalanced.geminiThinkingLevel)

        val proMaximum = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "google", "gemini-3-pro-preview")
        assertEquals("HIGH", proMaximum.geminiThinkingLevel)

        val flashDeep = ReasoningResolver.resolve(ReasoningPreset.DEEP, "google", "gemini-3.8-flash")
        assertEquals("HIGH", flashDeep.geminiThinkingLevel)

        val flashFast = ReasoningResolver.resolve(ReasoningPreset.FAST, "google", "gemini-3.8-flash")
        assertEquals("LOW", flashFast.geminiThinkingLevel)
    }

    @Test
    fun geminiTwoFiveFallsBackToAThinkingBudget() {
        val deep = ReasoningResolver.resolve(ReasoningPreset.DEEP, "google", "gemini-2.5-pro")
        assertNull(deep.geminiThinkingLevel)
        assertEquals(16384, deep.geminiThinkingBudget)

        val fast = ReasoningResolver.resolve(ReasoningPreset.FAST, "google", "gemini-2.5-flash")
        assertEquals(1024, fast.geminiThinkingBudget)

        val maximum = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "google", "gemini-2.5-flash")
        assertEquals(24576, maximum.geminiThinkingBudget)
    }

    @Test
    fun datedClaudeSnapshotsStayOnTheBudgetControl() {
        val model = ModelCapabilityRegistry.findOrRegister("claude-3-5-sonnet-20241022", "openai")
        assertEquals(ReasoningControl.ANTHROPIC_BUDGET, model.reasoning.control)

        val config = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "claude-3-5-sonnet-20241022")
        assertEquals(16384, config.claudeBudgetTokens)
        assertNull(config.claudeEffort)

        val newer = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "openai", "claude-sonnet-5-5-20260101")
        assertEquals("max", newer.claudeEffort)
    }

    @Test
    fun zhipuGlmStaysInsideTheValuesEachVersionAccepts() {
        val fast = ReasoningResolver.resolve(ReasoningPreset.FAST, "openai", "glm-5.3")
        assertEquals("low", fast.openAiEffort)

        val balanced = ReasoningResolver.resolve(ReasoningPreset.BALANCED, "openai", "glm-5.3")
        assertEquals("high", balanced.openAiEffort)

        val maximum = ReasoningResolver.resolve(ReasoningPreset.MAXIMUM, "openai", "glm-5.3")
        assertEquals("max", maximum.openAiEffort)

        val wide = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "glm-5.2")
        assertEquals("high", wide.openAiEffort)

        val legacy = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "glm-4.6")
        assertTrue(legacy.isAuto)
        assertEquals("模型默认", legacy.displayTag)
    }

    @Test
    fun qwenThirtyEightKeepsItsDocumentedEffortValues() {
        val deep = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "qwen3.8-max")
        assertEquals("xhigh", deep.openAiEffort)

        val balanced = ReasoningResolver.resolve(ReasoningPreset.BALANCED, "openai", "qwen3.8-max")
        assertEquals("medium", balanced.openAiEffort)

        val fast = ReasoningResolver.resolve(ReasoningPreset.FAST, "openai", "qwen3.8-omni-flash")
        assertEquals("low", fast.openAiEffort)
    }

    @Test
    fun relayAliasesResolveToTheSameCapability() {
        val dashed = ModelCapabilityRegistry.findOrRegister("gpt-6-astra", "custom")
        val compactName = ModelCapabilityRegistry.findOrRegister("gpt6astra", "custom")
        assertEquals(dashed.reasoning.control, compactName.reasoning.control)
        assertEquals(dashed.reasoning.levels, compactName.reasoning.levels)

        val prefixed = ModelCapabilityRegistry.findOrRegister("openai/gpt-6-astra", "custom")
        assertEquals(ReasoningControl.OPENAI_EFFORT, prefixed.reasoning.control)

        val compactClaude = ModelCapabilityRegistry.findOrRegister("claudeopus55", "custom")
        assertEquals(ReasoningControl.ANTHROPIC_EFFORT, compactClaude.reasoning.control)
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), compactClaude.reasoning.levels)

        val compactGlm = ModelCapabilityRegistry.findOrRegister("glm53", "custom")
        assertEquals(ReasoningControl.ZHIPU_EFFORT, compactGlm.reasoning.control)
        assertEquals(listOf("low", "high", "max"), compactGlm.reasoning.levels)
    }

    @Test
    fun reasoningPlanShowsWhatWillBeSent() {
        val plan = ReasoningPlanBuilder.build("openai", "gpt-6.1-sol")
        assertTrue(plan.supported)
        assertEquals("OpenAI effort", plan.controlLabel)
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), plan.levels)
        assertEquals(listOf("low", "medium", "high", "max"), plan.entries.map { it.second })

        val relayed = ReasoningPlanBuilder.build("custom", "gpt6astra")
        assertTrue(relayed.supported)
        assertEquals(plan.levels, relayed.levels)

        val unknown = ReasoningPlanBuilder.build("custom", "mystery-model")
        assertTrue(!unknown.supported)
    }

    @Test
    fun everyPresetStaysInsideTheDeclaredLevels() {
        ModelCapabilityRegistry.getAll().forEach { model ->
            val levels = model.reasoning.levels
            if (levels.isNotEmpty()) {
                ReasoningPreset.entries.forEach { preset ->
                    if (preset != ReasoningPreset.AUTO) {
                        val config = ReasoningResolver.resolve(preset, "openai", model.id)
                        val wire = config.openAiEffort ?: config.claudeEffort ?: config.geminiThinkingLevel
                        assertNotNull("${model.id} $preset", wire)
                        assertTrue(
                            "${model.id} $preset -> $wire",
                            levels.any { it.equals(wire, ignoreCase = true) }
                        )
                    }
                }
            }
        }
    }
}

