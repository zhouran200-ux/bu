package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCapabilityRegistryAuditTest {
    private val standard = listOf("low", "medium", "high")
    private val extended = standard + listOf("xhigh", "max")
    private val geminiStandard = listOf("LOW", "MEDIUM", "HIGH")
    private val geminiMinimal = listOf("MINIMAL") + geminiStandard

    @Test
    fun openAiGenerationsKeepTheirOwnDocumentedLevels() {
        assertLevels(ReasoningControl.OPENAI_EFFORT, listOf("minimal") + standard, "gpt-5", "gpt-5-2025-08-07")
        assertLevels(ReasoningControl.OPENAI_EFFORT, listOf("none") + standard, "gpt-5.1", "gpt-5.1-2025-11-13")
        assertLevels(
            ReasoningControl.OPENAI_EFFORT, listOf("none") + standard + "xhigh",
            "gpt-5.2", "gpt-5.4", "gpt-5.5", "gpt-5.4-2026-03-05"
        )
        assertLevels(
            ReasoningControl.OPENAI_EFFORT, listOf("none") + extended,
            "gpt-5.6", "gpt-5.6-sol", "gpt-6-sol", "gpt-6-luna"
        )
        assertLevels(ReasoningControl.OPENAI_EFFORT, extended, "gpt-6-astra", "gpt-6.1-sol")
        assertLevels(ReasoningControl.OPENAI_EFFORT, standard, "o1", "o3", "o3-mini", "o4-mini", "o3-2025-04-16")
    }

    @Test
    fun openAiUnverifiedVariantsDoNotInheritGenericEffort() {
        assertNoOverride(
            "o1-mini", "o1-preview", "o1-mini-2024-09-12", "o1-preview-2024-09-12",
            "gpt-5-pro", "gpt-5.2-pro", "gpt-5.4-pro", "gpt-5.5-pro",
            "gpt-5-chat-latest", "gpt-5.1-chat-latest", "gpt-5.2-codex",
            "gpt-5.3", "gpt-5.10", "gpt-6.2-sol", "gpt-6-astra-experimental",
            "gpt-5.4-unknown-2026-10-20", "gpt-5.4-pro-2026-10-20"
        )
    }

    @Test
    fun claudeLegacyModelsAreNotMistakenForExtendedThinkingModels() {
        assertNoOverride(
            "claude-2", "claude-2.1", "claude-3-opus", "claude-3-sonnet", "claude-3-haiku",
            "claude-3-5-sonnet", "claude-3-5-haiku", "claude-3-5-sonnet-20241022"
        )
        listOf(
            "claude-3-7-sonnet", "claude-3-7-sonnet-20250219", "claude-sonnet-4",
            "claude-opus-4", "claude-opus-4-1", "claude-sonnet-4-5", "claude-haiku-4-5"
        ).forEach { id ->
            val capability = capability(id)
            assertTrue(id, capability.supported)
            assertEquals(id, ReasoningControl.ANTHROPIC_BUDGET, capability.control)
            assertEquals(id, "enabled", capability.thinkingType)
            assertFalse(id, capability.isAdaptive)
            assertTrue(id, capability.levels.isEmpty())
        }
    }

    @Test
    fun claudeEffortSeparatesEnabledBudgetAndAdaptiveThinking() {
        listOf("claude-opus-4-5", "claude-opus-4-5-20251101").forEach { id ->
            assertLevels(ReasoningControl.ANTHROPIC_EFFORT, standard, id)
            assertEquals("enabled", capability(id).thinkingType)
            assertFalse(capability(id).isAdaptive)
        }
        listOf("claude-opus-4-6", "claude-sonnet-4-6").forEach { id ->
            assertLevels(ReasoningControl.ANTHROPIC_EFFORT, standard + "max", id)
            assertEquals("adaptive", capability(id).thinkingType)
            assertTrue(capability(id).isAdaptive)
        }
        listOf(
            "claude-opus-4-7", "claude-opus-4-8", "claude-fable-5", "claude-fable-5-1",
            "claude-mythos-5", "claude-mythos-5-1", "claude-opus-5", "claude-opus-5-5",
            "claude-sonnet-5", "claude-sonnet-5-5", "claude-haiku-5-5", "claude-fable-5-1-20261020"
        ).forEach { id ->
            assertLevels(ReasoningControl.ANTHROPIC_EFFORT, extended, id)
            assertEquals("adaptive", capability(id).thinkingType)
        }
        assertNoOverride("claude-new-model", "claude-opus-5-10", "claude-haiku-5", "claude-sonnet-4-7")
    }

    @Test
    fun geminiExactModelsAndSnapshotsKeepTheirPublishedLevelSets() {
        assertLevels(
            ReasoningControl.GEMINI_THINKING_LEVEL, geminiStandard,
            "gemini-4-argon", "gemini-3.8-flash", "gemini-3.1-pro-preview"
        )
        assertLevels(
            ReasoningControl.GEMINI_THINKING_LEVEL, geminiMinimal,
            "gemini-3.6-flash", "gemini-3-flash-preview", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite-2026-10-20", "gemini-3.5-flash-lite-20261020",
            "gemini-3.1-flash-lite-preview-2026-10-20", "gemini-3.5-flash-lite-preview-20261020"
        )
        assertLevels(ReasoningControl.GEMINI_THINKING_LEVEL, listOf("LOW", "HIGH"), "gemini-3-pro-preview")
        assertLevels(ReasoningControl.GEMINI_THINKING_LEVEL, listOf("MINIMAL", "HIGH"), "gemini-3.1-flash-lite-image")
    }

    @Test
    fun geminiBudgetsAreLimitedToVerifiedTextModels() {
        assertBudget(ReasoningControl.GEMINI_THINKING_BUDGET, 32768, "gemini-2.5-pro", "gemini-2.5-pro-2026-10-20")
        assertBudget(ReasoningControl.GEMINI_THINKING_BUDGET, 24576, "gemini-2.5-flash", "gemini-2.5-flash-lite")
        assertNoOverride(
            "gemini-2.0-flash", "gemini-2.0-flash-thinking-exp", "gemini-2.5-flash-image",
            "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts", "gemini-2.5-flash-live",
            "gemini-live-2.5-flash", "gemini-2.5-flash-native-audio-preview", "gemini-3.5-flash",
            "gemini-3.10-pro", "gemini-9-pro", "gemini-3.1-flash-lite-experimental-20261020"
        )
    }

    @Test
    fun deepSeekEffortRequiresAnAuditedFamilyIdentity() {
        assertLevels(
            ReasoningControl.DEEPSEEK_EFFORT, listOf("low", "high", "max"),
            "deepseek-v4-pro", "deepseek-v4-flash", "deepseek-v4.1-flash", "deepseek-flash",
            "deepseek-v4.1-flash-2026-10-20", "deepseek-v4.1-flash-1017",
            "deepseek-v4-pro-0813", "deepseek-v4-flash-0731"
        )
        assertNoOverride(
            "deepseek-r1-20261020", "deepseek-v3", "deepseek-v3.2", "deepseek-reasoner", "deepseek-chat",
            "deepseek-v4.10", "deepseek-v5", "deepseek-v4-unknown", "vendor-r1-thinker", "r1",
            "deepseek-v4", "deepseek-v4.1", "deepseek-v4.1-pro", "deepseek-v4.1-flash-9999"
        )
    }

    @Test
    fun qwenEffortIsRestrictedToVerifiedThirtyEightModels() {
        assertLevels(
            ReasoningControl.QWEN_EFFORT, listOf("low", "medium", "xhigh"),
            "qwen3.8-max", "qwen3.8-flash", "qwen3.8-omni-flash", "qwen3.8-27b", "qwen3.8-2.4t-a95b",
            "qwen3.8-max-2026-10-20", "qwen3.8-omni-flash-20261020", "qwen3.8-max-0902"
        )
        assertNoOverride(
            "qwen-max", "qwen3.8-coder", "qwen3.8-instruct", "qwen3.8-omni", "qwen3-omni-flash",
            "qwen3.7-omni-flash", "qwen3.5-27b", "qwen3-32b", "qvq-max", "qwen3.10-max",
            "qwen3.8-max-9999", "qwen3.8-max-0902-20261020"
        )
    }

    @Test
    fun qwenBudgetSnapshotThresholdsAndConservativeCapsAreExplicit() {
        assertBudget(
            ReasoningControl.QWEN_BUDGET, 32768,
            "qwen-plus", "qwen-plus-latest", "qwen-plus-2025-04-28", "qwen-plus-20250429",
            "qwen-flash", "qwen-flash-latest", "qwen-flash-2025-07-28", "qwen-flash-20250729",
            "qwen3.5-plus", "qwen3.5-flash", "qwen3.6-plus", "qwen3.6-flash",
            "qwen3.7-max", "qwen3.7-plus", "qwen3.7-flash", "qwen3.7-max-preview",
            "qwen3.7-max-20261020", "qwen3.6-plus-2026-10-20"
        )
        assertNull(capability("qwen3.7-max-preview").thinkingType)
        assertNoOverride(
            "qwen-plus-2025-04-27", "qwen-flash-2025-07-27", "qwen-plus-2024-12-20",
            "qwen-plus-2025-02-30", "qwen-flash-2025-13-01", "qwen3.7-coder", "qwen3.6-plus-instruct",
            "qwen-plus-latest-2026-10-20", "qwen3.5-max", "qwen3.8-plus"
        )
    }

    @Test
    fun glmReportsEffectiveLevelsRatherThanAcceptedAliases() {
        assertLevels(ReasoningControl.ZHIPU_EFFORT, listOf("high", "max"), "glm-5.2", "glm-5.2-20261020")
        assertLevels(
            ReasoningControl.ZHIPU_EFFORT, listOf("low", "high", "max"),
            "glm-5.3", "glm-5.3-flash", "glm-5.3-flashx", "glm-5.3-flash-20261020"
        )
        assertNoOverride("glm-4.7", "glm-5.1", "glm-5.4", "glm-5.10", "glm-5.2-flash", "glm-5.3-unknown")
    }

    @Test
    fun reportedRelayNamesResolveToTheirAuditedFamily() {
        assertLevels(
            ReasoningControl.GEMINI_THINKING_LEVEL, geminiMinimal,
            "gemini-3.6-flash-nx", "gemini-3.7-flash"
        )
        assertLevels(ReasoningControl.GEMINI_THINKING_LEVEL, geminiStandard, "gemini-3.8-flash-high")
        assertLevels(ReasoningControl.OPENAI_EFFORT, extended, "minimax-m3.1-flash")
        assertLevels(ReasoningControl.OPENAI_EFFORT, listOf("low", "high", "max"), "kimi-k3")
        assertLevels(ReasoningControl.OPENAI_EFFORT, listOf("none") + extended, "gpt-5.6-luna")
        assertLevels(
            ReasoningControl.OPENAI_EFFORT, listOf("none") + standard + "xhigh",
            "gpt-5.5-reasoning"
        )
        assertLevels(
            ReasoningControl.ZHIPU_EFFORT, listOf("low", "high", "max"),
            "s-glm-5.3", "glm5.3-flash-pro"
        )
        assertLevels(
            ReasoningControl.OPENAI_EFFORT, listOf("low", "medium", "high", "xhigh"),
            "grok-4.6", "grok-4.7", "muse-spark-1.3"
        )
        assertLevels(ReasoningControl.OPENAI_EFFORT, standard, "grok-4.5")
        assertLevels(ReasoningControl.OPENAI_EFFORT, listOf("low", "high"), "grok-3-mini")
        assertNoOverride("grok-4.20-reasoning", "grok-build", "grok-3", "muse-spark-9.9")
    }

    @Test
    fun mimoExposesAThinkingToggleWithoutIntensityLevels() {
        listOf("mimo-v2.6-flash", "mimo-v2.6-pro").forEach { id ->
            val capability = capability(id)
            assertTrue(id, capability.supported)
            assertEquals(id, ReasoningControl.MIMO_THINKING_TOGGLE, capability.control)
            assertTrue(id, capability.levels.isEmpty())
        }
        assertNoOverride("mimo-v2.5-pro", "mimo-v2.6-ultra")
    }

    @Test
    fun capabilityNeutralDecorationsReuseAuditedSiblingsOnly() {
        assertEquals(capability("gemini-3.6-flash"), capability("gemini-3.6-flash-nx"))
        assertEquals(capability("gemini-3.8-flash"), capability("gemini-3.8-flash-high"))
        assertEquals(capability("gpt-5.5"), capability("gpt-5.5-reasoning"))
        assertEquals(capability("glm-5.3"), capability("s-glm-5.3"))
        assertNoOverride(
            "gemini-3.6-flash-experimental", "gemini-3.6-flash-ultra",
            "gpt-5.5-unknown", "gpt-5.5-luna-max", "glm-5.3-flash-ultra"
        )
    }

    @Test
    fun normalizedAliasesKeepExactModelBoundaries() {
        mapOf(
            " OPENAI/GPT_6_ASTRA " to "gpt-6-astra",
            "models/google/gemini3.5flashlite" to "gemini-3.5-flash-lite",
            "anthropic/claudeopus55" to "claude-opus-5-5",
            "anthropic/claude-4.6-sonnet" to "claude-sonnet-4-6",
            "zhipu/GLM53" to "glm-5.3",
            "alibaba/QWEN3_8_2_4T_A95B" to "qwen3.8-2.4t-a95b",
            "models/gpt6astra20261020" to "gpt-6-astra",
            "claudeopus5520261020" to "claude-opus-5-5"
        ).forEach { (alias, canonical) -> assertEquals(alias, capability(canonical), capability(alias)) }
        assertNoOverride(
            "wrapper-gpt-6-astra", "gpt-5.10-2026-10-20", "claude-opus-5-10-20261020",
            "glm-5.10-20261020", "gemini-3.10-flash-lite-20261020", "qwen3.10-max-20261020",
            "gpt-5.4-2026-02-30", "gpt-5.4-20261020-extra", "gpt-5.4-2026-10", "gpt-5.4-99999999"
        )
    }

    @Test
    fun unknownNamesAndProvidersCannotInventReasoningControls() {
        listOf("custom", "openai", "chatgpt", "anthropic", "google", "deepseek", "qwen", "zhipu").forEach { provider ->
            listOf("audit-private-thinker", "audit-custom-reasoner", "audit-r1-secret", "audit-plain-model").forEach { id ->
                assertEquals("$provider/$id", ReasoningCapability.NONE, capability(id, provider))
            }
        }
    }

    @Test
    fun explicitMetadataSurvivesNormalizationAndCanDisableAnExistingEntry() {
        val id = "audit-explicit-metadata-model"
        val previous = ModelCapabilityRegistry.find(id)
        val enabled = ModelDefinition(
            " models/vendor/Audit_Explicit_Metadata_Model ",
            ReasoningCapability(
                true, ReasoningControl.ANTHROPIC_EFFORT, isAdaptive = false,
                levels = standard, thinkingType = "enabled", thinkingBudgetMax = 4096
            )
        )
        try {
            capability(id)
            ModelCapabilityRegistry.register(enabled)
            assertSame(enabled, ModelCapabilityRegistry.findOrRegister(id, "custom"))
            assertSame(enabled, ModelCapabilityRegistry.find("auditexplicitmetadatamodel"))
            val disabled = enabled.copy(
                id = "auditexplicitmetadatamodel",
                reasoning = enabled.reasoning.copy(supported = false)
            )
            ModelCapabilityRegistry.register(disabled)
            assertSame(disabled, ModelCapabilityRegistry.findOrRegister("models/$id", "anthropic"))
            assertEquals(1, ModelCapabilityRegistry.getAll().count { it == disabled })
            assertFalse(ModelCapabilityRegistry.getAll().contains(enabled))
        } finally {
            ModelCapabilityRegistry.register(previous ?: ModelDefinition(id, ReasoningCapability.NONE))
        }
    }

    private fun capability(id: String, provider: String = "openai"): ReasoningCapability =
        ModelCapabilityRegistry.findOrRegister(id, provider).reasoning

    private fun assertLevels(control: ReasoningControl, levels: List<String>, vararg ids: String) {
        ids.forEach { id ->
            val capability = capability(id)
            assertTrue(id, capability.supported)
            assertEquals(id, control, capability.control)
            assertEquals(id, levels, capability.levels)
        }
    }

    private fun assertBudget(control: ReasoningControl, maximum: Int, vararg ids: String) {
        ids.forEach { id ->
            val capability = capability(id)
            assertTrue(id, capability.supported)
            assertEquals(id, control, capability.control)
            assertEquals(id, maximum, capability.thinkingBudgetMax)
            assertFalse(id, capability.isAdaptive)
            assertTrue(id, capability.levels.isEmpty())
        }
    }

    private fun assertNoOverride(vararg ids: String) {
        ids.forEach { id -> assertEquals(id, ReasoningCapability.NONE, capability(id)) }
    }
}
