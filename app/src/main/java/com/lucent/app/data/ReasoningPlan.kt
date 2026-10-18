package com.lucent.app.data

data class ReasoningPlan(
    val controlLabel: String,
    val levels: List<String>,
    val entries: List<Pair<String, String>>,
    val supported: Boolean
)


object ReasoningPlanBuilder {
    fun build(provider: String, model: String): ReasoningPlan {
        val clean = model.trim()
        if (clean.isEmpty()) return ReasoningPlan("", emptyList(), emptyList(), false)

        val definition = ModelCapabilityRegistry.findOrRegister(clean, provider)
        val entries = ReasoningPreset.entries
            .filter { it != ReasoningPreset.AUTO }
            .map { preset -> preset.label to wireValue(ReasoningResolver.resolve(preset, provider, clean)) }
        return ReasoningPlan(
            controlLabel = controlLabel(definition.reasoning.control),
            levels = definition.reasoning.levels,
            entries = entries,
            supported = definition.reasoning.supported
        )
    }

    private fun wireValue(config: ResolvedReasoningConfig): String {
        return config.openAiEffort
            ?: config.claudeEffort
            ?: config.geminiThinkingLevel
            ?: config.claudeBudgetTokens?.let { "${it}t" }
            ?: config.geminiThinkingBudget?.let { "${it}t" }
            ?: "-"
    }

    private fun controlLabel(control: ReasoningControl): String {
        return when (control) {
            ReasoningControl.OPENAI_EFFORT -> "OpenAI effort"
            ReasoningControl.ANTHROPIC_EFFORT -> "Claude effort"
            ReasoningControl.ANTHROPIC_BUDGET -> "Claude thinking budget"
            ReasoningControl.GEMINI_THINKING_LEVEL -> "Gemini thinking level"
            ReasoningControl.GEMINI_THINKING_BUDGET -> "Gemini thinking budget"
            ReasoningControl.DEEPSEEK_EFFORT -> "DeepSeek effort"
            ReasoningControl.QWEN_EFFORT -> "Qwen effort"
            ReasoningControl.ZHIPU_EFFORT -> "GLM effort"
            ReasoningControl.NONE -> "none"
        }
    }
}
