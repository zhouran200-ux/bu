package com.lucent.app.ui

internal fun streamingMarkdownTail(text: String): String {
    val opening = text.lastIndexOf('〔')
    if (opening < 0) return text
    val tail = text.substring(opening)
    val prefix = "〔REF:"
    val pending = if (tail.length < prefix.length) prefix.startsWith(tail)
        else tail.startsWith(prefix) && tail.length <= 128 && citationPrefixCouldComplete(tail.substring(prefix.length))
    return if (pending && !insideMarkdownCode(text)) text.substring(0, opening) else text
}

private fun citationPrefixCouldComplete(payload: String): Boolean {
    val parts = payload.split(':')
    if (parts.size > 3 || parts[0].any { it !in '0'..'9' }) return false
    if (parts[0].isNotEmpty() && parts[0].toLongOrNull() == null) return false
    if (parts.size == 1) return true
    if (parts[0].toLongOrNull()?.takeIf { it > 0 } == null) return false
    val revision = parts[1]
    val hex = { value: String -> value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } }
    val revisionComplete: Boolean
    if (revision.startsWith('v')) {
        val dash = revision.indexOf('-')
        val version = revision.substring(1, if (dash < 0) revision.length else dash)
        if (version.isNotEmpty() && (version[0] !in '1'..'9' || version.any { it !in '0'..'9' })) return false
        if (dash < 0) return parts.size == 2
        val hash = revision.substring(dash + 1)
        if (version.isEmpty() || hash.length > 64 || !hex(hash)) return false
        revisionComplete = hash.length == 64
    } else {
        if (revision.length > 16 || !hex(revision)) return false
        revisionComplete = revision.isNotEmpty()
    }
    if (parts.size == 2) return true
    if (!revisionComplete) return false
    val slice = parts[2]
    if (slice.isEmpty()) return true
    if (slice[0] != 'S') return false
    val numbers = slice.drop(1).split('-')
    return numbers.size <= 2 && numbers.all { number -> number.all { it in '0'..'9' } } &&
        (numbers.size == 1 || numbers[0].isNotEmpty())
}

private fun insideMarkdownCode(text: String): Boolean {
    val lastBlock = parseBlocks(text).lastOrNull()
    val tail = when (lastBlock) {
        is MdBlock.Code -> return true
        is MdBlock.Paragraph -> lastBlock.text
        is MdBlock.Heading -> lastBlock.text
        is MdBlock.Bullet -> lastBlock.text
        is MdBlock.Numbered -> lastBlock.text
        is MdBlock.Quote -> lastBlock.text
        else -> return false
    }
    var inlineWidth = 0
    var i = 0
    while (i < tail.length) {
        if (tail[i] != '`') { i++; continue }
        val start = i
        while (i < tail.length && tail[i] == '`') i++
        val width = i - start
        if (inlineWidth == 0) inlineWidth = width else if (inlineWidth == width) inlineWidth = 0
    }
    return inlineWidth != 0
}
