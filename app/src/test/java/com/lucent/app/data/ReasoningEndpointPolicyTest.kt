package com.lucent.app.data

import org.junit.Assert.*
import org.junit.Test

class ReasoningEndpointPolicyTest {
    @Test fun nativeProtocolsDoNotClaimIncompatibleVendorControls() {
        assertTrue(ReasoningResolver.resolve(ReasoningPreset.DEEP, "anthropic", "gemini-3.8-flash").isAuto)
        assertTrue(ReasoningResolver.resolve(ReasoningPreset.DEEP, "google", "claude-opus-5-5").isAuto)
    }

    @Test fun officialClaudeCompatibilityWarnsWhileNativeEffortRemainsAvailable() {
        val url = "https://api.anthropic.com/v1"
        val compatible = ReasoningPlanBuilder.build("openai", "claude-opus-5-5", url)
        assertFalse(compatible.supported)
        assertTrue(compatible.note.contains("原生"))
        assertTrue(compatible.entries.all { it.second == "-" })
        val native = ReasoningPlanBuilder.build("anthropic", "claude-opus-5-5", url)
        assertTrue(native.supported)
        assertEquals(listOf("low", "medium", "high", "max"), native.entries.map { it.second })
        val budgetOnly = ReasoningPlanBuilder.build("openai", "claude-opus-4-5", url)
        assertEquals("Claude thinking budget", budgetOnly.controlLabel)
        assertTrue(budgetOnly.levels.isEmpty())
        assertEquals(listOf("2048t", "8192t", "16384t", "32768t"), budgetOnly.entries.map { it.second })
        val budgetTag = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "claude-opus-4-5", url)
        assertEquals("Claude 16384t 预算", budgetTag.displayTag)
    }

    @Test fun unknownRelayIsExplicitlyMarkedUnverifiedAndAutoRemainsNoOverride() {
        val relay = ReasoningResolver.resolve(ReasoningPreset.DEEP, "custom", "claude-opus-5-5", "https://relay.example/v1")
        assertEquals("high", relay.claudeEffort)
        assertEquals("Claude high", relay.displayTag)
        val auto = ReasoningResolver.resolve(ReasoningPreset.AUTO, "google", "gemini-2.5-flash-lite")
        assertTrue(auto.isAuto)
        assertEquals("模型默认", auto.displayTag)
    }

    @Test fun everyBudgetPresetHonorsAnExplicitMaximum() {
        val claude = ModelDefinition("audit-capped-claude", ReasoningCapability(true,
            ReasoningControl.ANTHROPIC_BUDGET, thinkingBudgetMax = 4096))
        val gemini = ModelDefinition("audit-capped-gemini", ReasoningCapability(true,
            ReasoningControl.GEMINI_THINKING_BUDGET, thinkingBudgetMax = 512))
        for (preset in ReasoningPreset.entries.filter { it != ReasoningPreset.AUTO }) {
            assertTrue(AnthropicReasoningMapper().map(preset, claude).claudeBudgetTokens!! <= 4096)
            assertEquals(512, GeminiBudgetReasoningMapper().map(preset, gemini).geminiThinkingBudget)
        }
        assertTrue(AnthropicReasoningMapper().map(ReasoningPreset.DEEP,
            claude.copy(reasoning = claude.reasoning.copy(thinkingBudgetMax = 512))).isAuto)
    }

    @Test fun explicitlyUnsupportedMetadataNeverEnablesAControl() {
        ModelCapabilityRegistry.register(ModelDefinition("audit-explicit-disabled", ReasoningCapability(
            supported = false, control = ReasoningControl.OPENAI_EFFORT, levels = listOf("high"))))
        val resolved = ReasoningResolver.resolve(ReasoningPreset.DEEP, "openai", "audit-explicit-disabled")
        assertTrue(resolved.isAuto)
        assertNull(resolved.openAiEffort)
    }
}
