package com.lucent.app.data

object DocumentHeadings {

    private val markdownHeading = Regex("^#{1,6}\\s+(.+?)(?:\\s+#+)?$")
    private val plainHeading = Regex(
        "^第\\s*[0-9０-９零〇一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟]+\\s*[章回节卷部篇](?:[\\s:：、.．·—-]+.*)?$"
    )

    fun markdownTitle(line: String): String? {
        val candidate = line.trim().removePrefix("\uFEFF").trim()
        return markdownHeading.matchEntire(candidate)?.groupValues?.get(1)?.trim()
    }

    fun plainTitle(line: String): String? {
        val candidate = line.trim().removePrefix("\uFEFF").trim()
        return candidate.takeIf { plainHeading.matches(it) }
    }

    fun titleOf(line: String): String? = markdownTitle(line) ?: plainTitle(line)
}