package com.lucent.app.network

import com.lucent.app.data.ResolvedReasoningConfig
import com.lucent.app.data.anthropicOutputCap
import org.json.JSONObject

internal fun applyCompatibleReasoning(payload: JSONObject, config: ResolvedReasoningConfig?, model: String, url: String) {
    if (config == null || config.isAuto) return
    val google = googleThinkingConfig(config, compatible = true)
    if (google != null) {
        require(config.openAiEffort == null && config.claudeEffort == null) { "Conflicting reasoning controls" }
        payload.put("extra_body", JSONObject().put("google", JSONObject().put("thinking_config", google)))
        return
    }
    config.openAiThinkingType?.let { type ->
        require(config.openAiEffort == null && config.claudeEffort == null) { "Conflicting reasoning controls" }
        payload.put("thinking", JSONObject().put("type", type))
        return
    }
    config.qwenBudgetTokens?.let { budget ->
        require(budget > 0 && config.openAiEffort == null) { "Invalid Qwen thinking budget" }
        payload.put("enable_thinking", true)
        payload.put("thinking_budget", budget)
        return
    }
    val officialClaude = runCatching { java.net.URI(url).host == "api.anthropic.com" }.getOrDefault(false)
    if (config.claudeBudgetTokens != null) {
        applyAnthropicReasoning(payload, config.copy(claudeEffort = null), model)
        if (!officialClaude && config.claudeEffort != null) payload.put("reasoning_effort", config.claudeEffort)
        return
    }
    if (config.claudeEffort != null) {
        if (!officialClaude) payload.put("reasoning_effort", config.claudeEffort)
        return
    }
    config.openAiEffort?.let { payload.put("reasoning_effort", it) }
}

internal fun applyAnthropicReasoning(payload: JSONObject, config: ResolvedReasoningConfig?, model: String) {
    val selected = config?.takeUnless { it.isAuto }
    val requestedBudget = selected?.claudeBudgetTokens ?: 0
    require(requestedBudget >= 0 && requestedBudget <= Int.MAX_VALUE - 1024) { "Invalid thinking budget" }
    val maxTokens = minOf(anthropicOutputCap(model), maxOf(8192, requestedBudget + if (requestedBudget > 0) 1024 else 0))
    payload.put("max_tokens", maxTokens)
    selected?.claudeEffort?.let { payload.put("output_config", JSONObject().put("effort", it)) }
    when {
        requestedBudget > 0 -> payload.put("thinking", JSONObject()
            .put("type", "enabled")
            .put("budget_tokens", minOf(requestedBudget, maxTokens - 1024).coerceAtLeast(1024)))
        selected?.claudeThinkingType == "adaptive" -> payload.put("thinking", JSONObject()
            .put("type", "adaptive").put("display", "summarized"))
    }
}

internal fun googleThinkingConfig(config: ResolvedReasoningConfig?, compatible: Boolean): JSONObject? {
    if (config == null || config.isAuto) return null
    val level = config.geminiThinkingLevel
    val budget = config.geminiThinkingBudget
    require(level == null || budget == null) { "Use either thinking level or budget, not both" }
    if (level == null && budget == null) return null
    return JSONObject().apply {
        if (level != null) put(if (compatible) "thinking_level" else "thinkingLevel", if (compatible) level.lowercase() else level)
        if (budget != null) {
            require(budget >= -1) { "Invalid Gemini thinking budget" }
            put(if (compatible) "thinking_budget" else "thinkingBudget", budget)
        }
        put(if (compatible) "include_thoughts" else "includeThoughts", true)
    }
}
