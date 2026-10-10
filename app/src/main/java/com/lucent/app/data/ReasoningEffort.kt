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

    val label: String
        get() = when (this) {
            PROVIDER_DEFAULT -> com.lucent.app.i18n.S.reasoningDefault
            NONE -> com.lucent.app.i18n.S.reasoningNone
            MINIMAL -> com.lucent.app.i18n.S.reasoningMinimal
            LOW -> com.lucent.app.i18n.S.reasoningLow
            MEDIUM -> com.lucent.app.i18n.S.reasoningMedium
            HIGH -> com.lucent.app.i18n.S.reasoningHigh
            XHIGH -> com.lucent.app.i18n.S.reasoningXhigh
            MAX -> com.lucent.app.i18n.S.reasoningMax
        }

    val detail: String
        get() = when (this) {
            PROVIDER_DEFAULT -> com.lucent.app.i18n.S.reasoningDefaultSub
            NONE -> com.lucent.app.i18n.S.reasoningNoneSub
            MINIMAL -> com.lucent.app.i18n.S.reasoningMinimalSub
            LOW -> com.lucent.app.i18n.S.reasoningLowSub
            MEDIUM -> com.lucent.app.i18n.S.reasoningMediumSub
            HIGH -> com.lucent.app.i18n.S.reasoningHighSub
            XHIGH -> com.lucent.app.i18n.S.reasoningXhighSub
            MAX -> com.lucent.app.i18n.S.reasoningMaxSub
        }

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

object ReasoningEfforts {

    private fun modelKey(model: String): String =
        model.trim().lowercase().removePrefix("models/").substringAfterLast('/')

    private fun versionOf(model: String, after: String): Double? {
        val at = model.indexOf(after)
        if (at < 0) return null
        val tail = model.substring(at + after.length)
        val match = Regex("(\\d+)(?:[.-](\\d{1,2})(?!\\d))?").find(tail) ?: return null
        val major = match.groupValues[1].toDoubleOrNull() ?: return null
        val minor = match.groupValues[2].toDoubleOrNull() ?: 0.0
        return major + minor / 10.0
    }

    private fun levels(vararg values: ReasoningEffort): List<ReasoningEffort> =
        listOf(ReasoningEffort.PROVIDER_DEFAULT) + values

    private val OPENAI_WIDE = levels(
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.XHIGH,
        ReasoningEffort.MAX
    )

    private val OPENAI_MEDIUM = levels(
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.XHIGH
    )

    private val OPENAI_SMALL = levels(
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val OPENAI_MINIMAL = levels(
        ReasoningEffort.MINIMAL,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val OPENAI_ASTRA = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.XHIGH,
        ReasoningEffort.MAX
    )

    private val CLAUDE_ADAPTIVE = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.MAX
    )

    private val CLAUDE_ADAPTIVE_WIDE = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH,
        ReasoningEffort.XHIGH,
        ReasoningEffort.MAX
    )

    private val LOW_MEDIUM_HIGH = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val GEMINI_LEVELS = levels(
        ReasoningEffort.MINIMAL,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val GEMINI_NO_MINIMAL = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val GEMINI_BUDGETS = levels(
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val GEMINI_BUDGETS_NO_OFF = levels(
        ReasoningEffort.LOW,
        ReasoningEffort.MEDIUM,
        ReasoningEffort.HIGH
    )

    private val DEEPSEEK_LEVELS = listOf(
        ReasoningEffort.PROVIDER_DEFAULT,
        ReasoningEffort.NONE,
        ReasoningEffort.LOW,
        ReasoningEffort.HIGH,
        ReasoningEffort.MAX
    )

    private val KIMI_K3_LEVELS = listOf(
        ReasoningEffort.PROVIDER_DEFAULT,
        ReasoningEffort.LOW,
        ReasoningEffort.HIGH,
        ReasoningEffort.MAX
    )

    private val ON_OFF = listOf(ReasoningEffort.PROVIDER_DEFAULT, ReasoningEffort.NONE)

    private val NOTHING = listOf(ReasoningEffort.PROVIDER_DEFAULT)

    fun anthropicBudgetTokens(effort: ReasoningEffort): Int? = when (effort) {
        ReasoningEffort.LOW -> 2048
        ReasoningEffort.MEDIUM -> 8192
        ReasoningEffort.HIGH -> 16384
        else -> null
    }

    fun googleThinkingBudget(effort: ReasoningEffort): Int? = when (effort) {
        ReasoningEffort.NONE -> 0
        ReasoningEffort.LOW -> 1024
        ReasoningEffort.MEDIUM -> 8192
        ReasoningEffort.HIGH -> 24576
        else -> null
    }
}
