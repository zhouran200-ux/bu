package com.lucent.app.data

object SourceTagging {

    const val TARGET_SLICE_CHARS = 600
    const val MAX_SLICE_CHARS = 900

    data class Chapter(val number: Int, val title: String, val startOffset: Int, val endOffset: Int)

    data class Slice(val chapter: Int, val number: Int, val startOffset: Int, val endOffset: Int) {
        val id: String get() = "S$chapter-$number"
    }

    data class Tagged(val text: String, val chapters: List<Chapter>, val slices: List<Slice>) {
        fun sliceOf(id: String): Slice? = slices.firstOrNull { it.id == id }
    }

    const val INDEX_VERSION = 1

    fun revisionOf(body: String, title: String = ""): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        listOf(INDEX_VERSION.toString(), TARGET_SLICE_CHARS.toString(), MAX_SLICE_CHARS.toString(), title, body)
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

        val chapters = mutableListOf<Chapter>()
        val slices = mutableListOf<Slice>()
        val out = StringBuilder()

        sectionsOf(body).forEachIndexed { index, section ->
            val number = index + 1
            val headingText = section.heading ?: title.ifBlank { "正文 ${index + 1}" }
            out.append("〔CH").append(number).append("〕\n")
            out.append(section.heading ?: "# $headingText").append("\n\n")
            chapters += Chapter(number, headingText, section.start, section.end)

            var sliceNumber = 1
            for (piece in slicesOf(section.paragraphs)) {
                out.append("〔S").append(number).append('-').append(sliceNumber).append("〕\n")
                out.append(piece.text).append("\n\n")
                slices += Slice(number, sliceNumber, piece.start, piece.end)
                sliceNumber++
            }
        }

        return Tagged(out.toString().trimEnd(), chapters, slices)
    }

    private class Section(
        val heading: String?,
        val start: Int,
        val end: Int,
        val paragraphs: List<Piece>
    )

    private class Piece(val text: String, val start: Int, val end: Int)

    private fun sectionsOf(body: String): List<Section> {
        val lines = body.split("\n")
        val lineOffsets = IntArray(lines.size)
        var offset = 0
        for (i in lines.indices) {
            lineOffsets[i] = offset
            offset += lines[i].length + 1
        }

        val boundaries = mutableListOf<Int>()
        val firstHeading = lines.indexOfFirst { DocumentHeadings.titleOf(it) != null }
        if (firstHeading != 0) boundaries += 0
        for (i in lines.indices) {
            if (DocumentHeadings.titleOf(lines[i]) != null) boundaries += i
        }

        val sections = mutableListOf<Section>()
        boundaries.forEachIndexed { position, first ->
            val lastExclusive = if (position + 1 < boundaries.size) boundaries[position + 1] else lines.size
            if (first >= lastExclusive) return@forEachIndexed
            val isHeading = DocumentHeadings.titleOf(lines[first]) != null
            val heading = if (isHeading) lines[first].trimEnd('\r').trim() else null
            val bodyFirst = if (isHeading) first + 1 else first
            val start = lineOffsets[first]
            val lastLine = lines[lastExclusive - 1].trimEnd('\r')
            val end = lineOffsets[lastExclusive - 1] + lastLine.length
            val paragraphs = if (bodyFirst >= lastExclusive) {
                emptyList()
            } else {
                paragraphsOf(lines, lineOffsets, bodyFirst, lastExclusive)
            }
            sections += Section(heading, start, end, paragraphs)
        }
        return sections
    }

    private fun paragraphsOf(
        lines: List<String>,
        lineOffsets: IntArray,
        from: Int,
        until: Int
    ): List<Piece> {
        val pieces = mutableListOf<Piece>()
        val buffer = StringBuilder()
        var start = -1
        var end = -1

        fun flush() {
            if (buffer.isNotEmpty()) {
                pieces += Piece(buffer.toString().trim(), start, end)
                buffer.setLength(0)
            }
            start = -1
            end = -1
        }

        for (i in from until until) {
            val text = lines[i].trimEnd('\r')
            if (text.isBlank()) {
                flush()
                continue
            }
            if (buffer.isEmpty()) start = lineOffsets[i] else buffer.append('\n')
            buffer.append(text)
            end = lineOffsets[i] + text.length
        }
        flush()
        return pieces
    }

    private fun slicesOf(paragraphs: List<Piece>): List<Piece> {
        val slices = mutableListOf<Piece>()
        val buffer = StringBuilder()
        var start = -1
        var end = -1

        fun flush() {
            if (buffer.isNotEmpty()) {
                slices += Piece(buffer.toString(), start, end)
                buffer.setLength(0)
            }
            start = -1
            end = -1
        }

        paragraphs.flatMap { splitLongParagraph(it) }.forEach { piece ->
            val overflow = buffer.length + piece.text.length + 2 > MAX_SLICE_CHARS
            if (buffer.isNotEmpty() && (overflow || buffer.length >= TARGET_SLICE_CHARS)) flush()
            if (buffer.isEmpty()) start = piece.start else buffer.append("\n\n")
            buffer.append(piece.text)
            end = piece.end
        }
        flush()
        return slices
    }

    private fun splitLongParagraph(piece: Piece): List<Piece> {
        if (piece.text.length <= MAX_SLICE_CHARS) return listOf(piece)

        val result = mutableListOf<Piece>()
        val text = piece.text
        val buffer = StringBuilder()
        var pieceStart = 0
        var cursor = 0
        while (cursor < text.length) {
            val ch = text[cursor]
            buffer.append(ch)
            val sentenceEnd = ch == '。' || ch == '！' || ch == '？' || ch == '…' || ch == '\n' ||
                ch == '.' || ch == '!' || ch == '?'
            if ((sentenceEnd && buffer.length >= MAX_SLICE_CHARS) || buffer.length >= MAX_SLICE_CHARS * 2) {
                result += Piece(buffer.toString().trim(), piece.start + pieceStart, piece.start + cursor + 1)
                buffer.setLength(0)
                pieceStart = cursor + 1
            }
            cursor++
        }
        if (buffer.isNotEmpty()) {
            result += Piece(buffer.toString().trim(), piece.start + pieceStart, piece.end)
        }
        return result
    }
}
