package com.lucent.app.data

object SourceCitations {

    private const val FULL = """〔REF:(\d+):([0-9a-fA-F]{1,16}):(S\d+-\d+)〕"""
    private const val SHORT = """〔(S\d+-\d+)〕"""

    val marker = Regex("$FULL|$SHORT")

    fun parse(raw: String, singleSourceId: Long? = null, singleRevision: String? = null): CitationRef? {
        val match = marker.matchEntire(raw.trim()) ?: return null
        val groups = match.groupValues
        return if (groups[1].isNotEmpty()) {
            CitationRef(groups[1].toLongOrNull(), groups[2], groups[3], raw)
        } else {
            CitationRef(singleSourceId, singleRevision, groups[4], raw)
        }
    }

    fun numbering(text: String): Map<String, Int> {
        val numbers = LinkedHashMap<String, Int>()
        for (match in marker.findAll(text)) {
            if (!numbers.containsKey(match.value)) numbers[match.value] = numbers.size + 1
        }
        return numbers
    }
}

data class CitationRef(
    val sourceId: Long?,
    val revision: String?,
    val sliceId: String,
    val raw: String
) {
    val isFullyQualified: Boolean get() = sourceId != null && revision != null
}
