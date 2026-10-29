package com.lucent.app.data

internal fun anthropicOutputCap(model: String): Int {
    val withoutSnapshot = model.trim().replace(Regex("[-_]?(20[0-9]{2}[-_]?[0-9]{2}[-_]?[0-9]{2})$"), "")
    val name = (ModelCapabilityRegistry.find(model)?.id
        ?: ModelCapabilityRegistry.find(withoutSnapshot)?.id ?: withoutSnapshot)
        .lowercase().substringAfterLast('/').filter { it.isLetterOrDigit() }
    val legacy = setOf("claude3opus", "claudeopus3", "claude3sonnet", "claudesonnet3", "claude3haiku", "claudehaiku3")
    val threeFive = setOf("claude35sonnet", "claudesonnet35", "claude35haiku", "claudehaiku35")
    val fourFive = setOf("claudeopus45", "claude45opus", "claudesonnet45", "claude45sonnet", "claudehaiku45", "claude45haiku")
    val newer = listOf("opus" to "46", "sonnet" to "46", "opus" to "47", "opus" to "48",
        "opus" to "5", "opus" to "55", "sonnet" to "5", "sonnet" to "55", "haiku" to "55",
        "fable" to "5", "fable" to "51", "mythos" to "5", "mythos" to "51")
        .flatMap { (tier, version) -> listOf("claude$tier$version", "claude$version$tier") }.toSet()
    return when {
        name in legacy || name in setOf("claude2", "claude20", "claude21") -> 4096
        name in threeFive -> 8192
        name in fourFive -> 65536
        name in newer -> 131072
        else -> Int.MAX_VALUE
    }
}
