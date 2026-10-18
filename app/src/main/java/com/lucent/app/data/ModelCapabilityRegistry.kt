package com.lucent.app.data

object ModelCapabilityRegistry {
    private val registry = mutableMapOf<String, ModelDefinition>()

    private val openAiFrontier = listOf("low", "medium", "high", "xhigh", "max")
    private val openAiLegacy = listOf("low", "medium", "high")
    private val claudeEffortLevels = listOf("low", "medium", "high", "max")
    private val claudeLegacyLevels = listOf("low", "medium", "high")
    private val geminiFull = listOf("MINIMAL", "LOW", "MEDIUM", "HIGH")
    private val geminiModern = listOf("LOW", "MEDIUM", "HIGH")
    private val geminiHighLow = listOf("LOW", "HIGH")
    private val deepSeekLevels = listOf("low", "high", "max")
    private val qwenLevels = listOf("low", "medium", "xhigh")
    private val zhipuLevels = listOf("low", "high", "max")
    private val zhipuWideLevels = listOf("low", "medium", "high", "xhigh", "max")

    init {
        registerOpenAiModels()
        registerAnthropicModels()
        registerGeminiModels()
        registerDeepSeekModels()
        registerQwenModels()
        registerZhipuModels()
    }

    @Synchronized
    fun register(definition: ModelDefinition) {
        registry[definition.id.lowercase()] = definition
    }

    @Synchronized
    fun getAll(): List<ModelDefinition> {
        return registry.values.toList()
    }

    @Synchronized
    fun find(modelId: String): ModelDefinition? {
        val clean = normalize(modelId)
        return registry[clean]
    }

    @Synchronized
    fun findOrRegister(modelId: String, provider: String): ModelDefinition {
        val clean = normalize(modelId)
        val prov = provider.trim().lowercase()
        val existing = registry[clean]
        if (existing != null) return existing

        val inferredControl = when {
            clean.contains("claude") -> claudeControl(clean)
            clean.contains("gemini") -> geminiControl(clean)
            clean.contains("deepseek") || clean.contains("r1") -> ReasoningControl.DEEPSEEK_EFFORT
            clean.contains("qwen") || clean.contains("qvq") -> ReasoningControl.QWEN_EFFORT
            clean.contains("glm") -> zhipuControl(clean)
            prov.contains("openai") || prov.contains("chatgpt") || clean.startsWith("o1") || clean.startsWith("o3") ||
                clean.startsWith("o4") || clean.contains("gpt-6") || clean.contains("gpt-5") -> ReasoningControl.OPENAI_EFFORT
            prov.contains("claude") || prov.contains("anthropic") -> claudeControl(clean)
            prov.contains("google") || prov.contains("gemini") -> geminiControl(clean)
            prov.contains("deepseek") -> ReasoningControl.DEEPSEEK_EFFORT
            prov.contains("qwen") -> ReasoningControl.QWEN_EFFORT
            clean.contains("think") || clean.contains("reason") -> ReasoningControl.OPENAI_EFFORT
            else -> ReasoningControl.NONE
        }

        val levels = when (inferredControl) {
            ReasoningControl.OPENAI_EFFORT -> openAiLevels(clean)
            ReasoningControl.ANTHROPIC_EFFORT -> claudeLevels(clean)
            ReasoningControl.DEEPSEEK_EFFORT -> deepSeekLevels
            ReasoningControl.QWEN_EFFORT -> qwenLevels
            ReasoningControl.ZHIPU_EFFORT -> zhipuLevelsFor(clean)
            ReasoningControl.GEMINI_THINKING_LEVEL -> geminiLevels(clean)
            else -> emptyList()
        }

        val capability = ReasoningCapability(
            supported = inferredControl != ReasoningControl.NONE,
            control = inferredControl,
            isAdaptive = inferredControl != ReasoningControl.ANTHROPIC_BUDGET && inferredControl != ReasoningControl.GEMINI_THINKING_BUDGET,
            levels = levels
        )
        val created = ModelDefinition(id = clean, reasoning = capability)
        registry[clean] = created
        return created
    }

    private fun normalize(modelId: String): String {
        return modelId.trim().lowercase().removePrefix("models/").substringAfterLast('/')
    }

    private fun version(clean: String, prefix: String): Double {
        val tail = clean.removePrefix(prefix)
        val rest = listOf("opus-", "sonnet-", "haiku-", "fable-", "mythos-")
            .fold(tail) { acc, family -> acc.removePrefix(family) }
        val match = Regex("^(\\d+)(?:[.-](\\d+))?").find(rest) ?: return 0.0
        val major = match.groupValues[1].toDoubleOrNull() ?: return 0.0
        val minor = match.groupValues[2].toDoubleOrNull() ?: 0.0
        return major + minor / 10
    }

    private fun claudeControl(clean: String): ReasoningControl {
        return if (version(clean, "claude-") >= 3.7) ReasoningControl.ANTHROPIC_EFFORT else ReasoningControl.ANTHROPIC_BUDGET
    }

    private fun claudeLevels(clean: String): List<String> {
        return if (version(clean, "claude-") >= 3.7) claudeEffortLevels else claudeLegacyLevels
    }

    private fun geminiControl(clean: String): ReasoningControl {
        val current = version(clean, "gemini-")
        return when {
            current < 2.0 -> ReasoningControl.NONE
            current < 3.0 -> ReasoningControl.GEMINI_THINKING_BUDGET
            else -> ReasoningControl.GEMINI_THINKING_LEVEL
        }
    }

    private fun geminiLevels(clean: String): List<String> {
        return when {
            clean.contains("lite-image") -> listOf("MINIMAL", "HIGH")
            clean.contains("3.5-flash") -> geminiModern
            clean.contains("3-pro") && !clean.contains("3.1-pro") -> geminiHighLow
            clean.contains("1.0") -> geminiModern
            else -> geminiFull
        }
    }

    private fun openAiLevels(clean: String): List<String> {
        return when {
            clean.startsWith("o1") || clean.startsWith("o3") || clean.startsWith("o4") -> openAiLegacy
            clean.contains("gpt-5") || clean.contains("gpt-6") -> openAiFrontier
            else -> openAiLegacy
        }
    }

    private fun zhipuControl(clean: String): ReasoningControl {
        return if (version(clean, "glm-") >= 5.2) ReasoningControl.ZHIPU_EFFORT else ReasoningControl.NONE
    }

    private fun zhipuLevelsFor(clean: String): List<String> {
        return when {
            version(clean, "glm-") >= 5.3 -> zhipuLevels
            version(clean, "glm-") >= 5.2 -> zhipuWideLevels
            else -> emptyList()
        }
    }

    private fun registerOpenAiModels() {
        listOf("o1", "o1-preview", "o1-mini", "o3", "o3-mini", "o4", "o4-mini").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.OPENAI_EFFORT, levels = openAiLegacy)))
        }
        listOf("gpt-6.1-sol", "gpt-6-astra", "gpt-6-luna", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.5").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.OPENAI_EFFORT, levels = openAiFrontier)))
        }
        listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability.NONE))
        }
    }

    private fun registerAnthropicModels() {
        listOf("claude-3-7-sonnet").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ANTHROPIC_EFFORT, levels = claudeLegacyLevels)))
        }
        listOf("claude-4-sonnet", "claude-5-sonnet", "claude-opus-5.5", "claude-sonnet-5.5", "claude-haiku-5.5").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ANTHROPIC_EFFORT, levels = claudeEffortLevels)))
        }
        listOf("claude-3-5-sonnet", "claude-3-opus").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ANTHROPIC_BUDGET, isAdaptive = false)))
        }
        listOf("claude-3-5-haiku", "claude-3-haiku").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability.NONE))
        }
    }

    private fun registerGeminiModels() {
        listOf("gemini-3.8-flash", "gemini-3.5-flash").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL, levels = geminiModern)))
        }
        listOf("gemini-3.6-flash", "gemini-3-flash-preview", "gemini-4-argon").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL, levels = geminiFull)))
        }
        listOf("gemini-3-pro-preview").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL, levels = geminiHighLow)))
        }
        listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash-thinking").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_BUDGET, isAdaptive = false)))
        }
        listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability.NONE))
        }
    }

    private fun registerDeepSeekModels() {
        listOf("deepseek-r1", "deepseek-v4", "deepseek-v4-pro", "deepseek-flash", "deepseek-reasoner").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.DEEPSEEK_EFFORT, levels = deepSeekLevels)))
        }
        listOf("deepseek-v3", "deepseek-chat").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability.NONE))
        }
    }

    private fun registerQwenModels() {
        listOf("qwen3.8-max", "qwen3.8-flash", "qwen3.8-omni-flash").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.QWEN_EFFORT, levels = qwenLevels)))
        }
        listOf("qwen-max", "qwen-plus", "qvq-72b-preview").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.QWEN_EFFORT)))
        }
    }

    private fun registerZhipuModels() {
        listOf("glm-5.3", "glm-5.3-flash", "glm-5.3-flashx").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ZHIPU_EFFORT, levels = zhipuLevels)))
        }
        listOf("glm-5.2").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ZHIPU_EFFORT, levels = zhipuWideLevels)))
        }
        listOf("glm-5.1", "glm-5", "glm-5-turbo", "glm-4.7", "glm-4.6", "glm-4.5").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability.NONE))
        }
    }
}
