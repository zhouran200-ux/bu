package com.lucent.app.data

import java.time.DateTimeException
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ModelCapabilityRegistry {
    private data class SnapshotRule(
        val capability: ReasoningCapability,
        val earliestDate: Int = 20000101
    )

    private val changes = MutableStateFlow(0L)
    internal val revision = changes.asStateFlow()
    private val registry = mutableMapOf<String, ModelDefinition>()
    private val packedRegistry = mutableMapOf<String, ModelDefinition>()
    private val snapshotRules = mutableMapOf<String, SnapshotRule>()
    private val snapshotSuffix = Regex("(20[0-9]{2})[-_]?([0-9]{2})[-_]?([0-9]{2})$")
    private val standardLevels = listOf("low", "medium", "high")
    private val extendedLevels = standardLevels + listOf("xhigh", "max")
    private val geminiStandard = listOf("LOW", "MEDIUM", "HIGH")
    private val geminiWithMinimal = listOf("MINIMAL") + geminiStandard

    init {
        registerOpenAiModels()
        registerAnthropicModels()
        registerGeminiModels()
        registerDeepSeekModels()
        registerQwenModels()
        registerZhipuModels()
        registerMiniMaxModels()
        registerKimiModels()
        registerXaiModels()
        registerMuseModels()
        registerMimoModels()
    }

    @Synchronized
    fun register(definition: ModelDefinition) {
        val key = normalize(definition.id)
        val compact = packed(key)
        registry.keys.removeAll { packed(it) == compact }
        registry[key] = definition
        packedRegistry[compact] = definition
        changes.value += 1
    }

    @Synchronized
    fun getAll(): List<ModelDefinition> = registry.values.toList()

    @Synchronized
    fun find(modelId: String): ModelDefinition? = registered(normalize(modelId))

    private data class DiscoveryKey(val endpoint: String, val protocol: String, val id: String)
    private val discovered = mutableMapOf<DiscoveryKey, ModelDefinition>()

    private fun discoveryKey(modelId: String, provider: String, baseUrl: String): DiscoveryKey? {
        if (baseUrl.isBlank()) return null
        val protocol = when {
            provider.contains("anthropic", true) || provider.contains("claude", true) -> "ANTHROPIC"
            provider.contains("google", true) || provider.contains("gemini", true) -> "GOOGLE"
            else -> "OPENAI"
        }
        return DiscoveryKey(baseUrl.trim(), protocol, modelId.trim())
    }

    @Synchronized
    internal fun replaceDiscovered(definitions: Collection<ModelDefinition>, provider: String, baseUrl: String) {
        val scope = discoveryKey("", provider, baseUrl) ?: return
        val previous = discovered.filterKeys { it.endpoint == scope.endpoint && it.protocol == scope.protocol }
        val next = definitions.associateBy { scope.copy(id = it.id.trim()) }
        if (previous == next) return
        discovered.keys.removeAll { it.endpoint == scope.endpoint && it.protocol == scope.protocol }
        discovered.putAll(next)
        changes.value += 1
    }

    @Synchronized
    fun findOrRegister(modelId: String, provider: String, baseUrl: String = ""): ModelDefinition {
        discoveryKey(modelId, provider, baseUrl)?.let { discovered[it] }?.let { return it }
        val clean = normalize(modelId)
        registered(clean)?.let { return it }
        val capability = modelCandidates(clean).firstNotNullOfOrNull { snapshotCapability(it) }
            ?: ReasoningCapability.NONE
        return ModelDefinition(clean, capability)
    }

    private fun registered(clean: String): ModelDefinition? {
        registry[clean]?.let { return it }
        packedRegistry[packed(clean)]?.let { return it }
        modelCandidates(clean).forEach { candidate ->
            registry[candidate]?.let { return it }
            packedRegistry[packed(candidate)]?.let { return it }
        }
        return null
    }

    private fun modelCandidates(clean: String): List<String> {
        val candidates = listOf(clean) + relayCandidates(clean)
        return (candidates + candidates.flatMap { decoratedCandidates(it) }).distinct()
    }

    private fun decoratedCandidates(clean: String): List<String> =
        aliasSuffixes.mapNotNull { suffix ->
            clean.takeIf { it.endsWith(suffix) && it.length > suffix.length }?.dropLast(suffix.length)
        }

    private val aliasSuffixes = listOf("-nx", "-high", "-reasoning")

    private val singleLetterPrefix = Regex("^[a-z]{1,2}-(?=[a-z0-9])")

    private fun relayCandidates(clean: String): List<String> {
        val candidates = mutableListOf<String>()
        var current = clean
        var guard = 0
        while (guard++ < 4) {
            stripRelaySuffix(current)?.let { if (it !in candidates) candidates += it }
            val stripped = stripRelayAffix(current) ?: break
            if (stripped !in candidates) candidates += stripped
            current = stripped
        }
        return candidates
    }

    private fun stripRelaySuffix(value: String): String? {
        var candidate = value
        val colon = candidate.lastIndexOf(':')
        if (colon in 1 until candidate.lastIndex) candidate = candidate.substring(0, colon)
        candidate = candidate.trimEnd {
            it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '-' && it != '.' && it != '_'
        }
        return candidate.takeIf { it.isNotEmpty() && it != value }
    }

    private fun stripRelayAffix(value: String): String? {
        val tag = relayTag.find(value)
        if (tag != null) {
            val rest = value.substring(tag.range.last + 1).trimStart('-', '_', '.', ':', ' ')
            if (rest.isNotEmpty()) return rest
        }
        relayPrefixes.forEach { prefix ->
            if (value.startsWith(prefix)) {
                val rest = value.substring(prefix.length).trimStart('-', '_', '.', ':', ' ')
                if (rest.isNotEmpty() && rest != value) return rest
            }
        }
        singleLetterPrefix.find(value)?.let { match ->
            val rest = value.substring(match.range.last + 1)
            if (rest.isNotEmpty() && rest != value) return rest
        }
        return null
    }

    private fun normalize(modelId: String): String =
        modelId.trim().lowercase().substringAfterLast('/').trim()

    private fun packed(value: String): String = value.filter { it.isLetterOrDigit() }

    private val relayTag = Regex("^\\[[^\\]]{1,24}]")

    private val relayPrefixes = listOf(
        "zhiqu", "zhipu", "bigmodel", "zai-org", "zai", "z-ai", "deepseek-ai",
        "openai", "azure-openai", "anthropic", "google", "vertex", "openrouter",
        "siliconflow", "moonshot", "dashscope", "aliyun", "volcengine", "ark"
    )

    private fun snapshotCapability(clean: String): ReasoningCapability? {
        val match = snapshotSuffix.find(clean) ?: return null
        val prefix = clean.substring(0, match.range.first)
        if (prefix.endsWith('.')) return null
        val rule = snapshotRules[packed(prefix)] ?: return null
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            return null
        }
        val date = year * 10000 + month * 100 + day
        return rule.capability.takeIf { date >= rule.earliestDate }
    }

    private fun addModels(
        ids: List<String>,
        capability: ReasoningCapability,
        snapshots: Boolean = true,
        earliestDate: Int = 20000101
    ) {
        ids.forEach { id ->
            register(ModelDefinition(id, capability))
            if (snapshots) snapshotRules[packed(id)] = SnapshotRule(capability, earliestDate)
        }
    }

    private fun effort(
        control: ReasoningControl,
        levels: List<String>,
        thinkingType: String? = null
    ) = ReasoningCapability(
        supported = true,
        control = control,
        isAdaptive = thinkingType != "enabled",
        levels = levels,
        thinkingType = thinkingType
    )

    private fun budget(
        control: ReasoningControl,
        maximum: Int? = null,
        thinkingType: String? = null
    ) = ReasoningCapability(
        supported = true,
        control = control,
        isAdaptive = false,
        thinkingType = thinkingType,
        thinkingBudgetMax = maximum
    )

    private fun registerOpenAiModels() {
        val control = ReasoningControl.OPENAI_EFFORT
        addModels(
            listOf("gpt-5.5"),
            effort(control, listOf("none") + standardLevels + "xhigh")
        )
        addModels(
            listOf("gpt-5.6", "gpt-5.6-sol", "gpt-6-sol", "gpt-6-luna"),
            effort(control, listOf("none") + extendedLevels)
        )
        addModels(listOf("gpt-6-astra", "gpt-6.1-sol"), effort(control, extendedLevels))
        addModels(listOf("gpt-5.6-luna"), effort(control, listOf("none") + extendedLevels))
    }

    private fun claudeIds(vararg models: Pair<String, String>): List<String> =
        models.flatMap { (tier, version) ->
            listOf("claude-$tier-$version", "claude-$version-$tier")
        }

    private fun registerAnthropicModels() {
        addModels(
            claudeIds("sonnet" to "4-5", "haiku" to "4-5"),
            budget(ReasoningControl.ANTHROPIC_BUDGET, thinkingType = "enabled")
        )
        addModels(
            claudeIds("opus" to "4-5"),
            effort(ReasoningControl.ANTHROPIC_EFFORT, standardLevels, "enabled")
        )
        addModels(
            claudeIds("opus" to "4-6", "sonnet" to "4-6"),
            effort(ReasoningControl.ANTHROPIC_EFFORT, standardLevels + "max", "adaptive")
        )
        addModels(
            claudeIds(
                "opus" to "4-7", "opus" to "4-8", "fable" to "5", "fable" to "5-1",
                "mythos" to "5", "mythos" to "5-1", "opus" to "5", "opus" to "5-5",
                "sonnet" to "5", "sonnet" to "5-5", "haiku" to "5-5"
            ),
            effort(ReasoningControl.ANTHROPIC_EFFORT, extendedLevels, "adaptive")
        )
    }

    private fun registerGeminiModels() {
        val level = ReasoningControl.GEMINI_THINKING_LEVEL
        addModels(
            listOf("gemini-4-argon", "gemini-3.8-flash", "gemini-3.1-pro-preview"),
            effort(level, geminiStandard)
        )
        addModels(
            listOf("gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3-flash-preview"),
            effort(level, geminiWithMinimal)
        )
        listOf("gemini-3.5-flash-lite-preview", "gemini-3.1-flash-lite-preview").forEach { id ->
            snapshotRules[packed(id)] = SnapshotRule(effort(level, geminiWithMinimal))
        }
        addModels(listOf("gemini-3-pro-preview"), effort(level, listOf("LOW", "HIGH")))
        addModels(listOf("gemini-3.1-flash-lite-image"), effort(level, listOf("MINIMAL", "HIGH")))
        addModels(listOf("gemini-3.7-flash"), effort(level, geminiWithMinimal))
        addModels(
            listOf("gemini-2.5-pro"),
            budget(ReasoningControl.GEMINI_THINKING_BUDGET, 32768)
        )
        addModels(
            listOf("gemini-2.5-flash", "gemini-2.5-flash-lite"),
            budget(ReasoningControl.GEMINI_THINKING_BUDGET, 24576)
        )
    }

    private fun registerDeepSeekModels() {
        val capability = effort(ReasoningControl.DEEPSEEK_EFFORT, listOf("low", "high", "max"))
        addModels(
            listOf("deepseek-v4-pro", "deepseek-v4-flash", "deepseek-v4.1-flash", "deepseek-flash"),
            capability
        )
        addModels(
            listOf("deepseek-v4.1-flash-1017", "deepseek-v4-pro-0813", "deepseek-v4-flash-0731"),
            capability,
            snapshots = false
        )
    }

    private fun registerQwenModels() {
        addModels(
            listOf("qwen3.8-max", "qwen3.8-flash", "qwen3.8-omni-flash", "qwen3.8-27b", "qwen3.8-2.4t-a95b"),
            effort(ReasoningControl.QWEN_EFFORT, listOf("low", "medium", "xhigh"))
        )
        addModels(
            listOf("qwen3.8-max-0902"),
            effort(ReasoningControl.QWEN_EFFORT, listOf("low", "medium", "xhigh")),
            snapshots = false
        )
        val capability = budget(ReasoningControl.QWEN_BUDGET, 32768)
        addModels(listOf("qwen-plus"), capability, earliestDate = 20250428)
        addModels(listOf("qwen-flash"), capability, earliestDate = 20250728)
        addModels(listOf("qwen-plus-latest", "qwen-flash-latest"), capability, snapshots = false)
        addModels(
            listOf(
                "qwen3.5-plus", "qwen3.5-flash", "qwen3.6-plus", "qwen3.6-flash",
                "qwen3.7-max", "qwen3.7-plus", "qwen3.7-flash", "qwen3.7-max-preview"
            ),
            capability
        )
    }

    private fun registerMiniMaxModels() {
        addModels(
            listOf("minimax-m3.1-flash"),
            effort(ReasoningControl.OPENAI_EFFORT, extendedLevels)
        )
    }

    private fun registerKimiModels() {
        addModels(
            listOf("kimi-k3"),
            effort(ReasoningControl.OPENAI_EFFORT, listOf("low", "high", "max"))
        )
    }

    private fun registerXaiModels() {
        addModels(
            listOf("grok-4.6", "grok-4.7"),
            effort(ReasoningControl.OPENAI_EFFORT, listOf("low", "medium", "high", "xhigh"))
        )
        addModels(listOf("grok-4.5"), effort(ReasoningControl.OPENAI_EFFORT, standardLevels))
    }

    private fun registerMimoModels() {
        addModels(
            listOf("mimo-v2.6-flash", "mimo-v2.6-pro"),
            effort(ReasoningControl.MIMO_THINKING_TOGGLE, emptyList())
        )
    }

    private fun registerMuseModels() {
        addModels(
            listOf("muse-spark-1.3"),
            effort(ReasoningControl.OPENAI_EFFORT, listOf("low", "medium", "high", "xhigh"))
        )
    }

    private fun registerZhipuModels() {
        addModels(
            listOf("glm-5.3", "glm-5.3-flash", "glm-5.3-flashx"),
            effort(ReasoningControl.ZHIPU_EFFORT, listOf("low", "high", "max"))
        )
        addModels(listOf("glm-5.2"), effort(ReasoningControl.ZHIPU_EFFORT, listOf("high", "max")))
        addModels(listOf("glm-5.3-flash-pro"), effort(ReasoningControl.ZHIPU_EFFORT, listOf("low", "high", "max")))
    }
}
