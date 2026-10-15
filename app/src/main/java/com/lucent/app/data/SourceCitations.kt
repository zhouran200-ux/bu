package com.lucent.app.data

object SourceCitations {

    private const val FULL = """〔REF:(\d+):(v[1-9][0-9]*-[0-9a-fA-F]{64}|[0-9a-fA-F]{1,16}):(S\d+-\d+)〕"""
    private const val SHORT = """〔S[：:]?([1-9][0-9]*-[1-9][0-9]*)〕"""

    val marker = Regex("$FULL|$SHORT")

    fun parse(raw: String, singleSourceId: Long? = null, singleRevision: String? = null): CitationRef? {
        val match = marker.matchEntire(raw.trim()) ?: return null
        val groups = match.groupValues
        return if (groups[1].isNotEmpty()) {
            val sourceId = groups[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            CitationRef(sourceId, groups[2].lowercase(), groups[3], raw)
        } else {
            CitationRef(singleSourceId, singleRevision, "S" + groups[4], raw)
        }
    }

    fun rejectionReason(ref: CitationRef, sourceId: Long?, currentRevision: String?): String? = when {
        !ref.isFullyQualified -> "引用缺少来源或版本，无法验证原文位置"
        sourceId == null || ref.sourceId != sourceId -> "引用不可用：找不到对应的资料文档"
        ref.revision != currentRevision -> "资料或索引已更新，已停止跳转以避免定位到错误剧情"
        else -> null
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
