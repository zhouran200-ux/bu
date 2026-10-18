package com.lucent.app.data

object ModelCapabilityRegistry {
    private val registry = mutableMapOf<String, ModelDefinition>()

    private val openAiLevels = listOf("low", "medium", "high", "xhigh", "max")
    private val openAiLegacyLevels = listOf("low", "medium", "high")
    private val claudeLevels = listOf("low", "medium", "high", "xhigh", "max")
    private val claudeLegacyLevels = listOf("low", "medium", "high")
    private val geminiStandard = listOf("LOW", "MEDIUM", "HIGH")
    private val geminiWithMinimal = listOf("MINIMAL", "LOW", "MEDIUM", "HIGH")
    private val geminiHighLow = listOf("LOW", "HIGH")
    private val geminiMinimalHigh = listOf("MINIMAL", "HIGH")
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
        return registry[normalize(modelId)]
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
            clean.contains("glm") -> zhipuControl(clean)
            clean.contains("deepseek") || clean.contains("r1") -> deepSeekControl(clean)
            clean.contains("qwen") || clean.contains("qvq") -> ReasoningControl.QWEN_EFFORT
            prov.contains("openai") || prov.contains("chatgpt") || clean.startsWith("o1") || clean.startsWith("o3") ||
                clean.startsWith("o4") || clean.contains("gpt-6") || clean.contains("gpt-5") -> openAiControl(clean)
            prov.contains("claude") || prov.contains("anthropic") -> claudeControl(clean)
            prov.contains("google") || prov.contains("gemini") -> geminiControl(clean)
            prov.contains("zhipu") || prov.contains("bigmodel") -> zhipuControl(clean)
            prov.contains("deepseek") -> deepSeekControl(clean)
            prov.contains("qwen") -> ReasoningControl.QWEN_EFFORT
            clean.contains("think") || clean.contains("reason") -> ReasoningControl.OPENAI_EFFORT
            else -> ReasoningControl.NONE
        }

        val levels = when (inferredControl) {
            ReasoningControl.OPENAI_EFFORT -> openAiLevelsFor(clean)
            ReasoningControl.ANTHROPIC_EFFORT -> claudeLevelsFor(clean)
            ReasoningControl.DEEPSEEK_EFFORT -> deepSeekLevels
            ReasoningControl.QWEN_EFFORT -> qwenLevels
            ReasoningControl.ZHIPU_EFFORT -> zhipuLevelsFor(clean)
            ReasoningControl.GEMINI_THINKING_LEVEL -> geminiLevelsFor(clean)
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

    private fun openAiControl(clean: String): ReasoningControl {
        return when {
            clean.contains("gpt-4") || clean.contains("gpt-3") -> ReasoningControl.NONE
            else -> ReasoningControl.OPENAI_EFFORT
        }
    }

    private fun openAiLevelsFor(clean: String): List<String> {
        return when {
            clean.startsWith("o1") || clean.startsWith("o3") || clean.startsWith("o4") -> openAiLegacyLevels
            clean.contains("gpt-6") || clean.contains("gpt-5") -> openAiLevels
            else -> openAiLegacyLevels
        }
    }

    private fun claudeControl(clean: String): ReasoningControl {
        return if (version(clean, "claude-") >= 3.7) ReasoningControl.ANTHROPIC_EFFORT else ReasoningControl.ANTHROPIC_BUDGET
    }

    private fun claudeLevelsFor(clean: String): List<String> {
        val current = version(clean, "claude-")
        return when {
            current < 3.7 -> emptyList()
            current < 5.0 -> claudeLegacyLevels
            else -> claudeLevels
        }
    }

    private fun geminiControl(clean: String): ReasoningControl {
        val current = version(clean, "gemini-")
        return when {
            current < 2.0 -> ReasoningControl.NONE
            current < 3.0 -> ReasoningControl.GEMINI_THINKING_BUDGET
            else -> ReasoningControl.GEMINI_THINKING_LEVEL
        }
    }

    private fun geminiLevelsFor(clean: String): List<String> {
        return when {
            clean.contains("lite-image") -> geminiMinimalHigh
            clean.contains("3-pro") && !clean.contains("3.1-pro") -> geminiHighLow
            clean.contains("3.6-flash") || clean.contains("3-flash-preview") -> geminiWithMinimal
            else -> geminiStandard
        }
    }

    private fun deepSeekControl(clean: String): ReasoningControl {
        return if (clean.contains("v3") || clean.contains("chat")) ReasoningControl.NONE else ReasoningControl.DEEPSEEK_EFFORT
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
        listOf("gpt-6-astra", "gpt-6.1-sol", "gpt-6-luna").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.OPENAI_EFFORT, levels = openAiLevels)))
        }
    }

    private fun registerAnthropicModels() {
        listOf("claude-fable-5-1", "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ANTHROPIC_EFFORT, levels = claudeLevels)))
        }
    }

    private fun registerGeminiModels() {
        listOf("gemini-4-argon", "gemini-3.8-flash", "gemini-3.1-pro-preview").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL, levels = geminiStandard)))
        }
        listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3-flash-preview").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL, levels = geminiWithMinimal)))
        }
        listOf("gemini-3-pro-preview").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL, levels = geminiHighLow)))
        }
        listOf("gemini-3.1-flash-lite-image").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL, levels = geminiMinimalHigh)))
        }
        listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_BUDGET, isAdaptive = false)))
        }
    }

    private fun registerDeepSeekModels() {
        listOf("deepseek-v4-pro", "deepseek-v4-flash", "deepseek-flash").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.DEEPSEEK_EFFORT, levels = deepSeekLevels)))
        }
    }

    private fun registerQwenModels() {
        listOf("qwen3.8-max", "qwen3.8-flash", "qwen3.8-omni-flash").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.QWEN_EFFORT, levels = qwenLevels)))
        }
    }

    private fun registerZhipuModels() {
        listOf("glm-5.3", "glm-5.3-flash", "glm-5.3-flashx").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ZHIPU_EFFORT, levels = zhipuLevels)))
        }
        listOf("glm-5.2").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ZHIPU_EFFORT, levels = zhipuWideLevels)))
        }
    }
}
