package com.lucent.app.data

object ModelCapabilityRegistry {
    private val registry = mutableMapOf<String, ModelDefinition>()
    private val packedRegistry = mutableMapOf<String, ModelDefinition>()

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
        val key = definition.id.lowercase()
        registry[key] = definition
        packedRegistry[packed(key)] = definition
    }

    @Synchronized
    fun getAll(): List<ModelDefinition> {
        return registry.values.toList()
    }

    @Synchronized
    fun find(modelId: String): ModelDefinition? {
        val clean = normalize(modelId)
        return registry[clean] ?: packedRegistry[packed(clean)]
    }

    @Synchronized
    fun findOrRegister(modelId: String, provider: String): ModelDefinition {
        val clean = normalize(modelId)
        val prov = provider.trim().lowercase()
        val existing = registry[clean] ?: packedRegistry[packed(clean)]
        if (existing != null) return existing

        val key = packed(clean)
        val inferredControl = when {
            key.contains("claude") -> claudeControl(clean)
            key.contains("gemini") -> geminiControl(clean)
            key.contains("glm") -> zhipuControl(clean)
            key.contains("deepseek") || key.contains("r1") -> deepSeekControl(key)
            key.contains("qwen") || key.contains("qvq") -> ReasoningControl.QWEN_EFFORT
            prov.contains("openai") || prov.contains("chatgpt") || key.startsWith("o1") || key.startsWith("o3") ||
                key.startsWith("o4") || key.contains("gpt6") || key.contains("gpt5") -> openAiControl(key)
            prov.contains("claude") || prov.contains("anthropic") -> claudeControl(clean)
            prov.contains("google") || prov.contains("gemini") -> geminiControl(clean)
            prov.contains("zhipu") || prov.contains("bigmodel") -> zhipuControl(clean)
            prov.contains("deepseek") -> deepSeekControl(key)
            prov.contains("qwen") -> ReasoningControl.QWEN_EFFORT
            key.contains("think") || key.contains("reason") -> ReasoningControl.OPENAI_EFFORT
            else -> ReasoningControl.NONE
        }

        val levels = when (inferredControl) {
            ReasoningControl.OPENAI_EFFORT -> openAiLevelsFor(key)
            ReasoningControl.ANTHROPIC_EFFORT -> claudeLevelsFor(clean)
            ReasoningControl.DEEPSEEK_EFFORT -> deepSeekLevels
            ReasoningControl.QWEN_EFFORT -> qwenLevels
            ReasoningControl.ZHIPU_EFFORT -> zhipuLevelsFor(clean)
            ReasoningControl.GEMINI_THINKING_LEVEL -> geminiLevelsFor(key)
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
        packedRegistry[packed(clean)] = created
        return created
    }

    private fun normalize(modelId: String): String {
        return modelId.trim().lowercase().removePrefix("models/").substringAfterLast('/')
    }

    private fun packed(value: String): String {
        return value.filter { it.isLetterOrDigit() }
    }

    private fun version(clean: String, marker: String): Double {
        val raw = clean.substringAfter(marker, "")
        if (raw.isEmpty()) return 0.0
        val digits = raw.dropWhile { !it.isDigit() }
        val match = Regex("^(\\d+)(?:[.-](\\d+))?").find(digits) ?: return 0.0
        val major = match.groupValues[1]
        val minor = match.groupValues[2]
        if (minor.isNotEmpty()) return major.toDouble() + minor.toDouble() / 10
        return when (major.length) {
            1 -> major.toDouble()
            2 -> major[0].digitToInt().toDouble() + major[1].digitToInt().toDouble() / 10
            else -> 0.0
        }
    }

    private fun openAiControl(key: String): ReasoningControl {
        return when {
            key.contains("gpt4") || key.contains("gpt3") || key.contains("gptoss") -> ReasoningControl.NONE
            key.startsWith("o1") || key.startsWith("o3") || key.startsWith("o4") -> ReasoningControl.OPENAI_EFFORT
            key.contains("gpt5") || key.contains("gpt6") -> ReasoningControl.OPENAI_EFFORT
            key.contains("think") || key.contains("reason") -> ReasoningControl.OPENAI_EFFORT
            else -> ReasoningControl.NONE
        }
    }

    private fun openAiLevelsFor(key: String): List<String> {
        return when {
            key.startsWith("o1") || key.startsWith("o3") || key.startsWith("o4") -> openAiLegacyLevels
            key.contains("gpt6") || key.contains("gpt5") -> openAiLevels
            else -> openAiLegacyLevels
        }
    }

    private fun claudeControl(clean: String): ReasoningControl {
        val current = version(clean, "claude")
        return if (current == 0.0 || current >= 3.7) ReasoningControl.ANTHROPIC_EFFORT else ReasoningControl.ANTHROPIC_BUDGET
    }

    private fun claudeLevelsFor(clean: String): List<String> {
        val current = version(clean, "claude")
        return when {
            current == 0.0 -> claudeLevels
            current < 3.7 -> emptyList()
            current < 5.0 -> claudeLegacyLevels
            else -> claudeLevels
        }
    }

    private fun geminiControl(clean: String): ReasoningControl {
        val current = version(clean, "gemini")
        return when {
            current == 0.0 -> ReasoningControl.NONE
            current < 2.0 -> ReasoningControl.NONE
            current < 3.0 -> ReasoningControl.GEMINI_THINKING_BUDGET
            else -> ReasoningControl.GEMINI_THINKING_LEVEL
        }
    }

    private fun geminiLevelsFor(key: String): List<String> {
        return when {
            key.contains("liteimage") -> geminiMinimalHigh
            key.contains("3pro") && !key.contains("31pro") -> geminiHighLow
            key.contains("36flash") || key.contains("3flashpreview") -> geminiWithMinimal
            else -> geminiStandard
        }
    }

    private fun deepSeekControl(key: String): ReasoningControl {
        return if (key.contains("v3") || key.contains("chat")) ReasoningControl.NONE else ReasoningControl.DEEPSEEK_EFFORT
    }

    private fun zhipuControl(clean: String): ReasoningControl {
        return if (version(clean, "glm") >= 5.2) ReasoningControl.ZHIPU_EFFORT else ReasoningControl.NONE
    }

    private fun zhipuLevelsFor(clean: String): List<String> {
        return when {
            version(clean, "glm") >= 5.3 -> zhipuLevels
            version(clean, "glm") >= 5.2 -> zhipuWideLevels
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
