package com.lucent.app.data

import com.lucent.app.i18n.S
import com.lucent.app.i18n.Tr
import com.lucent.app.i18n.Zh

object NoteTags {

    private val BUILT_IN: List<Pair<String, (Tr) -> String>> = listOf(
        Zh.tagStudy to { t: Tr -> t.tagStudy },
        Zh.tagWork to { t: Tr -> t.tagWork },
        Zh.tagGame to { t: Tr -> t.tagGame },
        Zh.tagSports to { t: Tr -> t.tagSports },
        Zh.tagOther to { t: Tr -> t.tagOther }
    )

    val DEFAULTS: List<String> = BUILT_IN.map { it.first }

    private val CANONICAL_BY_LABEL: Map<String, String> = HashMap<String, String>().apply {
        BUILT_IN.forEach { (key, pick) -> put(pick(Zh).lowercase(), key) }
    }

    fun canonical(tag: String): String {
        val t = tag.trim()
        if (t.isEmpty()) return t
        return CANONICAL_BY_LABEL[t.lowercase()] ?: t
    }

    fun label(tag: String): String {
        BUILT_IN.forEach { (key, pick) -> if (key.equals(tag, ignoreCase = true)) return pick(S) }
        return tag
    }

    fun parse(stored: String): List<String> =
        stored.split(",").map { canonical(it) }.filter { it.isNotBlank() }.distinct()

    fun displayLine(stored: String): String =
        parse(stored).joinToString(" ") { "#${label(it)}" }

    fun serialize(tags: List<String>): String =
        tags.map { canonical(it) }.filter { it.isNotBlank() }.distinct().joinToString(",")
}
