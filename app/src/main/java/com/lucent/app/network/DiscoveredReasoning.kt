package com.lucent.app.network

import com.lucent.app.data.ModelCapabilityRegistry
import com.lucent.app.data.ModelDefinition
import com.lucent.app.data.ReasoningCapability
import com.lucent.app.data.ReasoningControl
import org.json.JSONObject

internal fun discoveredReasoning(id: String, spec: ApiSpec, json: JSONObject): ModelDefinition? {
    if (json.has("supported") && json.opt("supported") !is Boolean) return null
    if (json.has("adaptive") && json.opt("adaptive") !is Boolean) return null
    val control = when (json.opt("control")) {
        "openai_effort", "effort" -> ReasoningControl.OPENAI_EFFORT
        "anthropic_effort" -> ReasoningControl.ANTHROPIC_EFFORT
        "anthropic_budget" -> ReasoningControl.ANTHROPIC_BUDGET
        "gemini_thinking_level", "thinking_level" -> ReasoningControl.GEMINI_THINKING_LEVEL
        "gemini_thinking_budget", "thinking_budget" -> ReasoningControl.GEMINI_THINKING_BUDGET
        "deepseek_effort" -> ReasoningControl.DEEPSEEK_EFFORT
        "qwen_effort" -> ReasoningControl.QWEN_EFFORT
        "qwen_budget" -> ReasoningControl.QWEN_BUDGET
        "zhipu_effort", "glm_effort" -> ReasoningControl.ZHIPU_EFFORT
        "none" -> ReasoningControl.NONE
        else -> return null
    }
    if (spec == ApiSpec.GOOGLE && control !in setOf(ReasoningControl.NONE,
            ReasoningControl.GEMINI_THINKING_LEVEL, ReasoningControl.GEMINI_THINKING_BUDGET)) return null
    if (spec == ApiSpec.ANTHROPIC && control !in setOf(ReasoningControl.NONE,
            ReasoningControl.ANTHROPIC_EFFORT, ReasoningControl.ANTHROPIC_BUDGET)) return null
    val known = ModelCapabilityRegistry.findOrRegister(id, spec.name).reasoning.takeIf { it.control == control }
    val allowed = if (control == ReasoningControl.GEMINI_THINKING_LEVEL) setOf("MINIMAL", "LOW", "MEDIUM", "HIGH")
        else setOf("none", "minimal", "low", "medium", "high", "xhigh", "max")
    val levels = if (json.has("levels")) {
        val array = json.optJSONArray("levels") ?: return null
        (0 until array.length()).map { (array.opt(it) as? String)?.takeIf { level -> level in allowed } ?: return null }
            .distinct().takeIf { it.isNotEmpty() } ?: return null
    } else known?.levels.orEmpty()
    val thinking = if (json.has("thinking_type")) (json.opt("thinking_type") as? String)
        ?.takeIf { it == "enabled" || it == "adaptive" } ?: return null else known?.thinkingType
    val budget = if (json.has("thinking_budget_max")) {
        val value = json.opt("thinking_budget_max") as? Number ?: return null
        value.toInt().takeIf { it in 1..1_000_000 && value.toDouble() == it.toDouble() } ?: return null
    } else known?.thinkingBudgetMax
    val supported = json.optBoolean("supported", true) && control != ReasoningControl.NONE
    return ModelDefinition(id, ReasoningCapability(supported,
        if (supported) control else ReasoningControl.NONE,
        json.optBoolean("adaptive", known?.isAdaptive ?: true), levels, thinking, budget))
}
