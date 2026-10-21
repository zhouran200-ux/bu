package com.lucent.app.data

data class ReasoningPlan(
    val controlLabel: String,
    val levels: List<String>,
    val entries: List<Pair<String, String>>,
    val supported: Boolean,
    val note: String = ""
)


object ReasoningPlanBuilder {
    fun build(provider: String, model: String, baseUrl: String = ""): ReasoningPlan {
        val clean = model.trim()
        if (clean.isEmpty()) return ReasoningPlan("", emptyList(), emptyList(), false)

        val definition = ModelCapabilityRegistry.findOrRegister(clean, provider)
        val representative = ReasoningResolver.resolve(ReasoningPreset.DEEP, provider, clean, baseUrl)
        val effectiveControl = if (definition.reasoning.control == ReasoningControl.ANTHROPIC_EFFORT &&
            representative.claudeEffort == null && representative.claudeBudgetTokens != null) ReasoningControl.ANTHROPIC_BUDGET
            else definition.reasoning.control
        val entries = ReasoningPreset.entries
            .filter { it != ReasoningPreset.AUTO }
            .map { preset -> preset.label to wireValue(ReasoningResolver.resolve(preset, provider, clean, baseUrl)) }
        return ReasoningPlan(
            controlLabel = controlLabel(effectiveControl),
            levels = if (effectiveControl == ReasoningControl.ANTHROPIC_BUDGET) emptyList() else definition.reasoning.levels,
            entries = entries,
            supported = definition.reasoning.supported && entries.any { it.second != "-" },
            note = representative.displayTag
        )
    }

    private fun wireValue(config: ResolvedReasoningConfig): String {
        if (config.isAuto) return "-"
        if (config.claudeEffort != null && config.claudeBudgetTokens != null) return "${config.claudeEffort} + ${config.claudeBudgetTokens}t"
        return config.openAiEffort
            ?: config.claudeEffort
            ?: config.geminiThinkingLevel
            ?: config.claudeBudgetTokens?.let { "${it}t" }
            ?: config.geminiThinkingBudget?.let { "${it}t" }
            ?: config.qwenBudgetTokens?.let { "${it}t" }
            ?: config.openAiThinkingType?.let { "thinking $it" }
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
            ReasoningControl.QWEN_BUDGET -> "Qwen thinking budget"
            ReasoningControl.ZHIPU_EFFORT -> "GLM effort"
            ReasoningControl.MIMO_THINKING_TOGGLE -> "MiMo thinking toggle"
            ReasoningControl.NONE -> "none"
        }
    }
}
