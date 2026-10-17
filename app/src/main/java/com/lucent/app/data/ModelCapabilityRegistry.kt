package com.lucent.app.data

object ModelCapabilityRegistry {
    private val registry = mutableMapOf<String, ModelDefinition>()

    init {
        registerOpenAiModels()
        registerAnthropicModels()
        registerGeminiModels()
        registerDeepSeekModels()
        registerQwenModels()
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
        val clean = modelId.trim().lowercase().removePrefix("models/").substringAfterLast('/')
        return registry[clean]
    }

    @Synchronized
    fun findOrRegister(modelId: String, provider: String): ModelDefinition {
        val clean = modelId.trim().lowercase().removePrefix("models/").substringAfterLast('/')
        val prov = provider.trim().lowercase()
        val existing = registry[clean]
        if (existing != null) return existing

        val inferredControl = when {
            clean.contains("claude") -> {
                if (clean.contains("3-7") || clean.contains("3.7") || clean.contains("4") || clean.contains("5")) {
                    ReasoningControl.ANTHROPIC_EFFORT
                } else {
                    ReasoningControl.ANTHROPIC_BUDGET
                }
            }
            clean.contains("gemini") -> {
                if (clean.contains("1.5") || clean.contains("1-5")) ReasoningControl.NONE else ReasoningControl.GEMINI_THINKING_LEVEL
            }
            clean.contains("deepseek") || clean.contains("r1") -> {
                ReasoningControl.DEEPSEEK_EFFORT
            }
            clean.contains("qwen") -> {
                ReasoningControl.QWEN_EFFORT
            }
            prov.contains("openai") || prov.contains("chatgpt") || clean.startsWith("o1") || clean.startsWith("o3") || clean.startsWith("o4") || clean.contains("gpt-6") || clean.contains("gpt6") -> {
                ReasoningControl.OPENAI_EFFORT
            }
            prov.contains("claude") || prov.contains("anthropic") -> {
                if (clean.contains("3-7") || clean.contains("3.7") || clean.contains("4") || clean.contains("5")) {
                    ReasoningControl.ANTHROPIC_EFFORT
                } else {
                    ReasoningControl.ANTHROPIC_BUDGET
                }
            }
            prov.contains("google") || prov.contains("gemini") -> {
                if (clean.contains("1.5") || clean.contains("1-5")) ReasoningControl.NONE else ReasoningControl.GEMINI_THINKING_LEVEL
            }
            prov.contains("deepseek") -> {
                ReasoningControl.DEEPSEEK_EFFORT
            }
            prov.contains("qwen") -> {
                ReasoningControl.QWEN_EFFORT
            }
            clean.contains("think") || clean.contains("reason") -> {
                ReasoningControl.OPENAI_EFFORT
            }
            else -> ReasoningControl.NONE
        }

        val capability = ReasoningCapability(
            supported = inferredControl != ReasoningControl.NONE,
            control = inferredControl,
            isAdaptive = true
        )
        val created = ModelDefinition(id = clean, reasoning = capability)
        registry[clean] = created
        return created
    }

    private fun registerOpenAiModels() {
        listOf("o1", "o1-preview", "o1-mini", "o3", "o3-mini", "o4", "gpt-6.1-sol", "gpt-6-astra", "gpt-6-luna").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.OPENAI_EFFORT)))
        }
        listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability.NONE))
        }
    }

    private fun registerAnthropicModels() {
        listOf("claude-3-7-sonnet", "claude-4-sonnet", "claude-5-sonnet", "claude-opus-5.5").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ANTHROPIC_EFFORT)))
        }
        listOf("claude-3-5-sonnet", "claude-3-opus").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.ANTHROPIC_BUDGET, isAdaptive = false)))
        }
        listOf("claude-3-5-haiku", "claude-3-haiku").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability.NONE))
        }
    }

    private fun registerGeminiModels() {
        listOf("gemini-2.0-flash-thinking", "gemini-2.5-pro", "gemini-2.5-flash", "gemini-3.5-flash", "gemini-3.8-flash").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL)))
        }
        listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability.NONE))
        }
    }

    private fun registerDeepSeekModels() {
        listOf("deepseek-r1", "deepseek-v4", "deepseek-reasoner").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.DEEPSEEK_EFFORT)))
        }
        listOf("deepseek-v3", "deepseek-chat").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability.NONE))
        }
    }

    private fun registerQwenModels() {
        listOf("qwen-max", "qwen-plus", "qwen3.8-omni-flash", "qvq-72b-preview").forEach { id ->
            register(ModelDefinition(id, ReasoningCapability(true, ReasoningControl.QWEN_EFFORT)))
        }
    }
}
