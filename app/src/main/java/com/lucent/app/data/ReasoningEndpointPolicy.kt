package com.lucent.app.data

internal fun reasoningForEndpoint(config: ResolvedReasoningConfig, provider: String, baseUrl: String): ResolvedReasoningConfig {
    if (config.isAuto) return config
    val nativeClaude = provider.contains("anthropic", true) || provider.contains("claude", true)
    val nativeGoogle = provider.contains("google", true) || provider.contains("gemini", true)
    if ((nativeClaude && config.claudeEffort == null && config.claudeBudgetTokens == null) ||
        (nativeGoogle && config.geminiThinkingLevel == null && config.geminiThinkingBudget == null)) {
        return ResolvedReasoningConfig(config.preset, isAuto = true, displayTag = "当前协议未确认该模型的思考控制，使用模型默认")
    }
    val host = runCatching { java.net.URI(baseUrl.trim()).host?.lowercase() }.getOrNull()
    if (!nativeClaude && !nativeGoogle && config.claudeEffort != null && host == "api.anthropic.com") {
        if (config.claudeBudgetTokens != null) return config.copy(claudeEffort = null,
            displayTag = "Claude " + config.claudeBudgetTokens + "t 预算")
        return ResolvedReasoningConfig(config.preset, isAuto = true, displayTag = "Claude 兼容接口不支持 effort，请切换原生协议")
    }
    return config
}
