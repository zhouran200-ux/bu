package com.lucent.app.data



enum class ReasoningPreset(
    val key: String,
    val label: String
) {
    AUTO("auto", "自动"),
    FAST("fast", "极速"),
    BALANCED("balanced", "标准"),
    DEEP("deep", "深度"),
    MAXIMUM("maximum", "极致");

    companion object {
        val DEFAULT = AUTO

        fun fromKey(rawKey: String?): ReasoningPreset {
            if (rawKey.isNullOrBlank()) return DEFAULT
            val clean = rawKey.trim().lowercase()
            return when (clean) {
                "auto", "default" -> AUTO
                "fast", "minimal", "low" -> FAST
                "balanced", "medium" -> BALANCED
                "deep", "high" -> DEEP
                "maximum", "max", "xhigh" -> MAXIMUM
                "none" -> AUTO
                else -> DEFAULT
            }
        }
    }
}


enum class ReasoningControl {
    OPENAI_EFFORT,
    ANTHROPIC_EFFORT,
    ANTHROPIC_BUDGET,
    GEMINI_THINKING_LEVEL,
    DEEPSEEK_EFFORT,
    QWEN_EFFORT,
    NONE
}


data class ReasoningCapability(
    val supported: Boolean,
    val control: ReasoningControl,
    val isAdaptive: Boolean = true
) {
    companion object {
        val NONE = ReasoningCapability(
            supported = false,
            control = ReasoningControl.NONE
        )
    }
}


data class ModelDefinition(
    val id: String,
    val reasoning: ReasoningCapability
)


data class ResolvedReasoningConfig(
    val preset: ReasoningPreset,
    val isAuto: Boolean = false,
    val openAiEffort: String? = null,
    val openAiMode: String? = null,
    val geminiThinkingLevel: String? = null,
    val claudeEffort: String? = null,
    val claudeBudgetTokens: Int? = null,
    val displayTag: String = ""
) {
    companion object {
        val AUTO = ResolvedReasoningConfig(
            preset = ReasoningPreset.AUTO,
            isAuto = true,
            displayTag = "原厂自适应"
        )
    }
}


interface ReasoningMapper {
    fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig
}


class OpenAiReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val clean = model.id.lowercase()
        val isGpt6 = clean.contains("gpt-6") || clean.contains("gpt6")
        val effortStr = when (preset) {
            ReasoningPreset.AUTO -> null
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "medium"
            ReasoningPreset.DEEP -> "high"
            ReasoningPreset.MAXIMUM -> if (isGpt6) "xhigh" else "high"
        }
        val modeStr = if (isGpt6 && (preset == ReasoningPreset.DEEP || preset == ReasoningPreset.MAXIMUM)) "pro" else null

        return ResolvedReasoningConfig(
            preset = preset,
            openAiEffort = effortStr,
            openAiMode = modeStr,
            displayTag = if (modeStr != null) "OpenAI $effortStr (Pro模式)" else "OpenAI $effortStr"
        )
    }
}


class AnthropicReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        return if (model.reasoning.control == ReasoningControl.ANTHROPIC_EFFORT || model.reasoning.isAdaptive) {
            val effort = when (preset) {
                ReasoningPreset.AUTO -> null
                ReasoningPreset.FAST -> "low"
                ReasoningPreset.BALANCED -> "medium"
                ReasoningPreset.DEEP -> "high"
                ReasoningPreset.MAXIMUM -> "max"
            }
            ResolvedReasoningConfig(
                preset = preset,
                claudeEffort = effort,
                displayTag = "Claude $effort (自适应)"
            )
        } else {
            val budget = when (preset) {
                ReasoningPreset.AUTO -> null
                ReasoningPreset.FAST -> 2048
                ReasoningPreset.BALANCED -> 8192
                ReasoningPreset.DEEP -> 16384
                ReasoningPreset.MAXIMUM -> 32768
            }
            ResolvedReasoningConfig(
                preset = preset,
                claudeBudgetTokens = budget,
                displayTag = "Claude ${budget}t 预算"
            )
        }
    }
}


class GeminiReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val level = when (preset) {
            ReasoningPreset.AUTO -> null
            ReasoningPreset.FAST -> "LOW"
            ReasoningPreset.BALANCED -> "MEDIUM"
            ReasoningPreset.DEEP, ReasoningPreset.MAXIMUM -> "HIGH"
        }
        return ResolvedReasoningConfig(
            preset = preset,
            geminiThinkingLevel = level,
            displayTag = "Gemini $level"
        )
    }
}


class DeepSeekReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val effort = when (preset) {
            ReasoningPreset.AUTO -> null
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "high"
            ReasoningPreset.DEEP -> "high"
            ReasoningPreset.MAXIMUM -> "max"
        }
        return ResolvedReasoningConfig(
            preset = preset,
            openAiEffort = effort,
            displayTag = "DeepSeek $effort"
        )
    }
}


class QwenReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val effort = when (preset) {
            ReasoningPreset.AUTO -> null
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "medium"
            ReasoningPreset.DEEP, ReasoningPreset.MAXIMUM -> "xhigh"
        }
        return ResolvedReasoningConfig(
            preset = preset,
            openAiEffort = effort,
            displayTag = "Qwen $effort"
        )
    }
}


class FallbackReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        return ResolvedReasoningConfig(
            preset = preset,
            isAuto = true,
            displayTag = "模型默认"
        )
    }
}


object ReasoningResolver {
    private val openAiMapper = OpenAiReasoningMapper()
    private val anthropicMapper = AnthropicReasoningMapper()
    private val geminiMapper = GeminiReasoningMapper()
    private val deepSeekMapper = DeepSeekReasoningMapper()
    private val qwenMapper = QwenReasoningMapper()
    private val fallbackMapper = FallbackReasoningMapper()

    fun resolve(
        preset: ReasoningPreset,
        provider: String,
        model: String
    ): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) {
            return ResolvedReasoningConfig.AUTO
        }

        val modelDef = ModelCapabilityRegistry.findOrRegister(model, provider)
        val mapper: ReasoningMapper = when (modelDef.reasoning.control) {
            ReasoningControl.OPENAI_EFFORT -> openAiMapper
            ReasoningControl.ANTHROPIC_EFFORT, ReasoningControl.ANTHROPIC_BUDGET -> anthropicMapper
            ReasoningControl.GEMINI_THINKING_LEVEL -> geminiMapper
            ReasoningControl.DEEPSEEK_EFFORT -> deepSeekMapper
            ReasoningControl.QWEN_EFFORT -> qwenMapper
            ReasoningControl.NONE -> fallbackMapper
        }

        return mapper.map(preset, modelDef)
    }
}
