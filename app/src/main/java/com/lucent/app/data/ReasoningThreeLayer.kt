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
    val provider: String,
    val reasoning: ReasoningCapability
)


data class ResolvedReasoningConfig(
    val preset: ReasoningPreset,
    val isAuto: Boolean = false,
    val openAiEffort: String? = null,
    val openAiMode: String? = null,
    val geminiThinkingLevel: String? = null,
    val geminiThinkingBudget: Int? = null,
    val claudeEffort: String? = null,
    val claudeBudgetTokens: Int? = null,
    val displayTag: String = ""
) {
    val hasNativeParam: Boolean
        get() = !isAuto && (openAiEffort != null || openAiMode != null ||
                geminiThinkingLevel != null || geminiThinkingBudget != null ||
                claudeEffort != null || claudeBudgetTokens != null)

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


object ModelCapabilityRegistry {
    private val registry = mutableMapOf<String, ModelDefinition>()

    init {
        registerOpenAiModels()
        registerAnthropicModels()
        registerGeminiModels()
        registerDeepSeekModels()
        registerQwenModels()
    }

    fun register(definition: ModelDefinition) {
        registry[definition.id.lowercase()] = definition
    }

    fun getAll(): List<ModelDefinition> {
        return registry.values.toList()
    }

    fun find(modelId: String): ModelDefinition? {
        val clean = modelId.trim().lowercase().removePrefix("models/").substringAfterLast('/')
        return registry[clean]
    }

    fun findOrRegister(modelId: String, provider: String): ModelDefinition {
        val clean = modelId.trim().lowercase().removePrefix("models/").substringAfterLast('/')
        val prov = provider.trim().lowercase()
        val existing = registry[clean]
        if (existing != null) return existing

        val inferredControl = when {
            prov.contains("openai") || prov.contains("chatgpt") || clean.startsWith("o1") || clean.startsWith("o3") || clean.startsWith("o4") || clean.contains("gpt-6") || clean.contains("gpt6") -> {
                ReasoningControl.OPENAI_EFFORT
            }
            prov.contains("claude") || prov.contains("anthropic") || clean.contains("claude") -> {
                if (clean.contains("3-7") || clean.contains("3.7") || clean.contains("4") || clean.contains("5")) {
                    ReasoningControl.ANTHROPIC_EFFORT
                } else {
                    ReasoningControl.ANTHROPIC_BUDGET
                }
            }
            prov.contains("google") || prov.contains("gemini") || clean.contains("gemini") -> {
                if (clean.contains("1.5") || clean.contains("1-5")) ReasoningControl.NONE else ReasoningControl.GEMINI_THINKING_LEVEL
            }
            prov.contains("deepseek") || clean.contains("deepseek") || clean.contains("r1") -> {
                ReasoningControl.DEEPSEEK_EFFORT
            }
            prov.contains("qwen") || clean.contains("qwen") -> {
                ReasoningControl.QWEN_EFFORT
            }
            clean.contains("r1") || clean.contains("think") || clean.contains("reason") -> {
                ReasoningControl.OPENAI_EFFORT
            }
            else -> ReasoningControl.NONE
        }

        val capability = ReasoningCapability(
            supported = inferredControl != ReasoningControl.NONE,
            control = inferredControl,
            isAdaptive = true
        )
        val created = ModelDefinition(id = clean, provider = provider, reasoning = capability)
        registry[clean] = created
        return created
    }

    private fun registerOpenAiModels() {
        listOf("o1", "o1-preview", "o1-mini", "o3", "o3-mini", "o4", "gpt-6.1-sol", "gpt-6-astra", "gpt-6-luna").forEach { id ->
            register(ModelDefinition(id, "openai", ReasoningCapability(true, ReasoningControl.OPENAI_EFFORT)))
        }
        listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo").forEach { id ->
            register(ModelDefinition(id, "openai", ReasoningCapability.NONE))
        }
    }

    private fun registerAnthropicModels() {
        listOf("claude-3-7-sonnet", "claude-4-sonnet", "claude-5-sonnet", "claude-opus-5.5").forEach { id ->
            register(ModelDefinition(id, "anthropic", ReasoningCapability(true, ReasoningControl.ANTHROPIC_EFFORT)))
        }
        listOf("claude-3-5-sonnet", "claude-3-opus").forEach { id ->
            register(ModelDefinition(id, "anthropic", ReasoningCapability(true, ReasoningControl.ANTHROPIC_BUDGET, isAdaptive = false)))
        }
        listOf("claude-3-5-haiku", "claude-3-haiku").forEach { id ->
            register(ModelDefinition(id, "anthropic", ReasoningCapability.NONE))
        }
    }

    private fun registerGeminiModels() {
        listOf("gemini-2.0-flash-thinking", "gemini-2.5-pro", "gemini-2.5-flash", "gemini-3.5-flash", "gemini-3.8-flash").forEach { id ->
            register(ModelDefinition(id, "google", ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL)))
        }
        listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b").forEach { id ->
            register(ModelDefinition(id, "google", ReasoningCapability.NONE))
        }
    }

    private fun registerDeepSeekModels() {
        listOf("deepseek-r1", "deepseek-v4", "deepseek-reasoner").forEach { id ->
            register(ModelDefinition(id, "deepseek", ReasoningCapability(true, ReasoningControl.DEEPSEEK_EFFORT)))
        }
        listOf("deepseek-v3", "deepseek-chat").forEach { id ->
            register(ModelDefinition(id, "deepseek", ReasoningCapability.NONE))
        }
    }

    private fun registerQwenModels() {
        listOf("qwen-max", "qwen-plus", "qwen3.8-omni-flash", "qvq-72b-preview").forEach { id ->
            register(ModelDefinition(id, "qwen", ReasoningCapability(true, ReasoningControl.QWEN_EFFORT)))
        }
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
