package com.lucent.app.data

enum class ReasoningEffort(val key: String) {
    PROVIDER_DEFAULT("default"),
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    companion object {
        val DEFAULT = PROVIDER_DEFAULT

        fun fromKey(key: String?): ReasoningEffort =
            entries.firstOrNull { it.key == (key?.trim()?.lowercase() ?: "") } ?: DEFAULT
    }
}

data class ReasoningPlan(
    val effort: String? = null,
    val thinkingOff: Boolean = false,
    val claudeAdaptive: Boolean = false,
    val claudeEffort: String? = null,
    val claudeBudgetTokens: Int? = null,
    val googleThinkingLevel: String? = null,
    val googleThinkingBudget: Int? = null
) {
    val empty: Boolean
        get() = effort == null && !thinkingOff && !claudeAdaptive && claudeBudgetTokens == null &&
            googleThinkingLevel == null && googleThinkingBudget == null

    companion object {
        val NONE = ReasoningPlan()
    }
}
