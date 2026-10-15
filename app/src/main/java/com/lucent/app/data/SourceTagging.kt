package com.lucent.app.data

object SourceTagging {

    const val TARGET_SLICE_CHARS = 500
    const val INDEX_VERSION = 2

    data class Chapter(val number: Int, val title: String, val startOffset: Int, val endOffset: Int)

    data class Slice(val chapter: Int, val number: Int, val startOffset: Int, val endOffset: Int) {
        val id: String get() = "S$chapter-$number"
    }

    data class Tagged(val text: String, val chapters: List<Chapter>, val slices: List<Slice>) {
        fun sliceOf(id: String): Slice? = slices.firstOrNull { it.id == id }
    }

    fun revisionOf(body: String, title: String = ""): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        listOf(INDEX_VERSION.toString(), TARGET_SLICE_CHARS.toString(), title, body)
            .forEach { field ->
                val bytes = field.toByteArray(Charsets.UTF_8)
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
                digest.update(bytes)
            }
        val hex = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        return "v$INDEX_VERSION-$hex"
    }

    fun tag(title: String, body: String): Tagged {
        if (body.isBlank()) return Tagged("", emptyList(), emptyList())

        val lines = linesOf(body)
        val boundaries = lines.indices.filter { lines[it].heading != null }.toMutableList()
        val firstHeading = boundaries.firstOrNull() ?: lines.size
        if (lines.take(firstHeading).any { !it.blank }) boundaries.add(0, 0)
        val chapters = mutableListOf<Chapter>()
        val slices = mutableListOf<Slice>()
        val out = StringBuilder()

        boundaries.forEachIndexed { index, first ->
            val until = boundaries.getOrNull(index + 1) ?: lines.size
            val number = index + 1
            val heading = lines[first].heading
            val headingText = heading ?: title.ifBlank { "正文 $number" }
            val end = if (until < lines.size) lines[until].start else body.length
            chapters += Chapter(number, headingText, lines[first].start, end)
            if (out.isNotEmpty()) out.append("\n\n")
            out.append("〔CH").append(number).append("〕\n")
            out.append(heading ?: "# $headingText")

            val paragraphs = paragraphsOf(lines, if (heading != null) first + 1 else first, until)
            slicesOf(paragraphs).forEachIndexed { sliceIndex, piece ->
                val sliceNumber = sliceIndex + 1
                out.append("\n\n〔S：").append(number).append('-').append(sliceNumber).append("〕\n")
                out.append(body.substring(piece.start, piece.end))
                slices += Slice(number, sliceNumber, piece.start, piece.end)
            }
        }

        return Tagged(out.toString(), chapters, slices)
    }

    private class Line(
        val start: Int,
        val end: Int,
        val blank: Boolean,
        val fenced: Boolean,
        val heading: String?
    )

    private class Piece(val start: Int, val end: Int)

    private class Fence(val marker: Char, val length: Int)

    private fun linesOf(body: String): List<Line> {
        val lines = mutableListOf<Line>()
        var start = 0
        var fence: Fence? = null
        while (start < body.length) {
            var end = start
            while (end < body.length && body[end] != '\r' && body[end] != '\n') end++
            var next = end
            if (next < body.length && body[next] == '\r') next++
            if (next < body.length && body[next] == '\n') next++
            val text = body.substring(start, end)
            val activeFence = fence
            val opening = if (activeFence == null) openingFence(text) else null
            val fenced = activeFence != null || opening != null
            if (activeFence != null) {
                if (closesFence(text, activeFence)) fence = null
            } else if (opening != null) {
                fence = opening
            }
            val heading = if (!fenced && DocumentHeadings.titleOf(text) != null) text else null
            lines += Line(start, end, text.isBlank(), fenced, heading)
            start = next
        }
        return lines
    }

    private fun openingFence(text: String): Fence? {
        val indent = text.takeWhile { it == ' ' }.length
        if (indent > 3 || indent == text.length) return null
        val marker = text[indent]
        if (marker != '`' && marker != '~') return null
        val length = text.drop(indent).takeWhile { it == marker }.length
        if (length < 3) return null
        if (marker == '`' && text.drop(indent + length).contains('`')) return null
        return Fence(marker, length)
    }

    private fun closesFence(text: String, fence: Fence): Boolean {
        val indent = text.takeWhile { it == ' ' }.length
        if (indent > 3) return false
        val length = text.drop(indent).takeWhile { it == fence.marker }.length
        return length >= fence.length && text.drop(indent + length).isBlank()
    }

    private fun paragraphsOf(lines: List<Line>, from: Int, until: Int): List<Piece> {
        val pieces = mutableListOf<Piece>()
        var start = -1
        var end = -1
        for (index in from until until) {
            val line = lines[index]
            if (line.blank && !line.fenced) {
                if (start >= 0) pieces += Piece(start, end)
                start = -1
            } else {
                if (start < 0) start = line.start
                end = line.end
            }
        }
        if (start >= 0) pieces += Piece(start, end)
        return pieces
    }

    private fun slicesOf(paragraphs: List<Piece>): List<Piece> {
        val slices = mutableListOf<Piece>()
        var current: Piece? = null
        for (paragraph in paragraphs) {
            val previous = current
            if (previous == null) {
                current = paragraph
                continue
            }
            val length = previous.end - previous.start
            val combinedLength = paragraph.end - previous.start
            if (length >= TARGET_SLICE_CHARS ||
                (combinedLength >= TARGET_SLICE_CHARS &&
                    TARGET_SLICE_CHARS - length <= combinedLength - TARGET_SLICE_CHARS)
            ) {
                slices += previous
                current = paragraph
            } else {
                current = Piece(previous.start, paragraph.end)
            }
        }
        current?.let { slices += it }
        return slices
    }
}
