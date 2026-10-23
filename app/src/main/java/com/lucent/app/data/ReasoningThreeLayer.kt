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
    GEMINI_THINKING_BUDGET,
    DEEPSEEK_EFFORT,
    QWEN_EFFORT,
    QWEN_BUDGET,
    ZHIPU_EFFORT,
    MIMO_THINKING_TOGGLE,
    NONE
}


data class ReasoningCapability(
    val supported: Boolean,
    val control: ReasoningControl,
    val isAdaptive: Boolean = true,
    val levels: List<String> = emptyList(),
    val thinkingType: String? = null,
    val thinkingBudgetMax: Int? = null
) {
    companion object {
        val NONE = ReasoningCapability(
            supported = false,
            control = ReasoningControl.NONE
        )
    }
}


object ReasoningLevels {
    private val ladder = listOf("minimal", "low", "medium", "high", "xhigh", "max")

    fun supported(desired: String, levels: List<String>): String {
        if (levels.isEmpty()) return desired
        val target = ladder.indexOf(desired.trim().lowercase())
        if (target < 0) return desired
        var chosen = levels.first()
        var distance = Int.MAX_VALUE
        levels.forEach { level ->
            val index = ladder.indexOf(level.trim().lowercase())
            if (index >= 0) {
                val candidate = kotlin.math.abs(index - target)
                if (candidate < distance) {
                    distance = candidate
                    chosen = level
                }
            }
        }
        return chosen
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
    val geminiThinkingLevel: String? = null,
    val geminiThinkingBudget: Int? = null,
    val claudeEffort: String? = null,
    val claudeBudgetTokens: Int? = null,
    val claudeThinkingType: String? = null,
    val qwenBudgetTokens: Int? = null,
    val openAiThinkingType: String? = null,
    val displayTag: String = ""
) {
    companion object {
        val AUTO = ResolvedReasoningConfig(
            preset = ReasoningPreset.AUTO,
            isAuto = true,
            displayTag = "模型默认"
        )
    }
}


interface ReasoningMapper {
    fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig
}


class OpenAiReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val desired = when (preset) {
            ReasoningPreset.AUTO -> "low"
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "medium"
            ReasoningPreset.DEEP -> "high"
            ReasoningPreset.MAXIMUM -> "max"
        }
        val effort = ReasoningLevels.supported(desired, model.reasoning.levels)

        return ResolvedReasoningConfig(
            preset = preset,
            openAiEffort = effort,
            displayTag = "OpenAI $effort"
        )
    }
}


class AnthropicReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO
        val usesBudget = model.reasoning.control == ReasoningControl.ANTHROPIC_BUDGET || model.reasoning.thinkingType == "enabled"
        if (usesBudget && (model.reasoning.thinkingBudgetMax ?: Int.MAX_VALUE) < 1024) return FallbackReasoningMapper().map(preset, model)

        return if (model.reasoning.control == ReasoningControl.ANTHROPIC_EFFORT) {
            val desired = when (preset) {
                ReasoningPreset.AUTO -> "low"
                ReasoningPreset.FAST -> "low"
                ReasoningPreset.BALANCED -> "medium"
                ReasoningPreset.DEEP -> "high"
                ReasoningPreset.MAXIMUM -> "max"
            }
            val effort = ReasoningLevels.supported(desired, model.reasoning.levels)
            ResolvedReasoningConfig(
                preset = preset,
                claudeEffort = effort,
                claudeThinkingType = model.reasoning.thinkingType,
                claudeBudgetTokens = if (model.reasoning.thinkingType == "enabled") budget(preset).coerceAtMost(model.reasoning.thinkingBudgetMax ?: Int.MAX_VALUE) else null,
                displayTag = "Claude $effort"
            )
        } else {
            val budget = budget(preset).coerceAtMost(model.reasoning.thinkingBudgetMax ?: Int.MAX_VALUE)
            ResolvedReasoningConfig(
                preset = preset,
                claudeBudgetTokens = budget,
                claudeThinkingType = "enabled",
                displayTag = "Claude ${budget}t 预算"
            )
        }
    }
    private fun budget(preset: ReasoningPreset): Int = when (preset) {
        ReasoningPreset.AUTO, ReasoningPreset.FAST -> 2048
        ReasoningPreset.BALANCED -> 8192
        ReasoningPreset.DEEP -> 16384
        ReasoningPreset.MAXIMUM -> 32768
    }
}


class GeminiReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val desired = when (preset) {
            ReasoningPreset.AUTO -> "LOW"
            ReasoningPreset.FAST -> "LOW"
            ReasoningPreset.BALANCED -> "MEDIUM"
            ReasoningPreset.DEEP, ReasoningPreset.MAXIMUM -> "HIGH"
        }
        val level = ReasoningLevels.supported(desired, model.reasoning.levels)
        return ResolvedReasoningConfig(
            preset = preset,
            geminiThinkingLevel = level,
            displayTag = "Gemini $level"
        )
    }
}


class GeminiBudgetReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val budget = when (preset) {
            ReasoningPreset.AUTO -> 1024
            ReasoningPreset.FAST -> 1024
            ReasoningPreset.BALANCED -> 8192
            ReasoningPreset.DEEP -> 16384
            ReasoningPreset.MAXIMUM -> model.reasoning.thinkingBudgetMax ?: 24576
        }.coerceAtMost(model.reasoning.thinkingBudgetMax ?: 24576)
        return ResolvedReasoningConfig(
            preset = preset,
            geminiThinkingBudget = budget,
            displayTag = "Gemini ${budget}t 预算"
        )
    }
}


class DeepSeekReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val desired = when (preset) {
            ReasoningPreset.AUTO -> "low"
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "high"
            ReasoningPreset.DEEP -> "high"
            ReasoningPreset.MAXIMUM -> "max"
        }
        val effort = ReasoningLevels.supported(desired, model.reasoning.levels)
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
        if (model.reasoning.control == ReasoningControl.QWEN_BUDGET) {
            val budget = when (preset) {
                ReasoningPreset.AUTO, ReasoningPreset.FAST -> 2048
                ReasoningPreset.BALANCED -> 8192
                ReasoningPreset.DEEP -> 16384
                ReasoningPreset.MAXIMUM -> 32768
            }.coerceAtMost(model.reasoning.thinkingBudgetMax ?: 32768)
            return ResolvedReasoningConfig(preset, qwenBudgetTokens = budget, displayTag = "Qwen ${budget}t 预算")
        }

        val desired = when (preset) {
            ReasoningPreset.AUTO -> "low"
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "medium"
            ReasoningPreset.DEEP, ReasoningPreset.MAXIMUM -> "xhigh"
        }
        val effort = ReasoningLevels.supported(desired, model.reasoning.levels)
        return ResolvedReasoningConfig(
            preset = preset,
            openAiEffort = effort,
            displayTag = "Qwen $effort"
        )
    }
}


class ZhipuReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val desired = when (preset) {
            ReasoningPreset.AUTO -> "low"
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "high"
            ReasoningPreset.DEEP -> "high"
            ReasoningPreset.MAXIMUM -> "max"
        }
        val effort = ReasoningLevels.supported(desired, model.reasoning.levels)
        return ResolvedReasoningConfig(
            preset = preset,
            openAiEffort = effort,
            displayTag = "GLM $effort"
        )
    }
}


class MimoReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO
        return ResolvedReasoningConfig(
            preset = preset,
            openAiThinkingType = "enabled",
            displayTag = "MiMo 思考开启"
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
    private val geminiBudgetMapper = GeminiBudgetReasoningMapper()
    private val deepSeekMapper = DeepSeekReasoningMapper()
    private val qwenMapper = QwenReasoningMapper()
    private val zhipuMapper = ZhipuReasoningMapper()
    private val mimoMapper = MimoReasoningMapper()
    private val fallbackMapper = FallbackReasoningMapper()

    fun resolve(
        preset: ReasoningPreset,
        provider: String,
        model: String,
        baseUrl: String = ""
    ): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) {
            return ResolvedReasoningConfig.AUTO
        }

        val modelDef = ModelCapabilityRegistry.findOrRegister(model, provider, baseUrl)
        if (!modelDef.reasoning.supported) return fallbackMapper.map(preset, modelDef)
        val mapper: ReasoningMapper = when (modelDef.reasoning.control) {
            ReasoningControl.OPENAI_EFFORT -> openAiMapper
            ReasoningControl.ANTHROPIC_EFFORT, ReasoningControl.ANTHROPIC_BUDGET -> anthropicMapper
            ReasoningControl.GEMINI_THINKING_LEVEL -> geminiMapper
            ReasoningControl.GEMINI_THINKING_BUDGET -> geminiBudgetMapper
            ReasoningControl.DEEPSEEK_EFFORT -> deepSeekMapper
            ReasoningControl.QWEN_EFFORT, ReasoningControl.QWEN_BUDGET -> qwenMapper
            ReasoningControl.ZHIPU_EFFORT -> zhipuMapper
            ReasoningControl.MIMO_THINKING_TOGGLE -> mimoMapper
            ReasoningControl.NONE -> fallbackMapper
        }

        return reasoningForEndpoint(mapper.map(preset, modelDef), provider, baseUrl)
    }
}

