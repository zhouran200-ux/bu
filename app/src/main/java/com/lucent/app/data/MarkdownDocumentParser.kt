package com.lucent.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedDocument(
    val title: String,
    val markdown: String,
    val extension: String
)

object MarkdownDocumentParser {

    private val SUPPORTED_EXTENSIONS = setOf("txt", "docx")

    fun isSupported(name: String): Boolean {
        val ext = extensionOf(name)
        return ext in SUPPORTED_EXTENSIONS
    }

    fun parseUri(context: Context, uri: Uri): Result<ParsedDocument> = runCatching {
        val fileName = queryDisplayName(context, uri) ?: "未命名文档"
        val ext = extensionOf(fileName)
        if (ext !in SUPPORTED_EXTENSIONS) {
            error("仅支持导入 TXT 与 Word (DOCX) 格式文档")
        }

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取该文件数据")

        val title = fileName.substringBeforeLast(".").ifBlank { "导入文档" }
        val markdown = when (ext) {
            "docx" -> parseDocxToMarkdown(bytes)
            "txt" -> parseTxtToMarkdown(bytes)
            else -> error("暂不支持该格式")
        }

        ParsedDocument(
            title = title,
            markdown = markdown,
            extension = ext
        )
    }

    fun parseTxtToMarkdown(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val text = decodeText(bytes)
        return text.replace("\r\n", "\n").replace("\r", "\n").trim()
    }

    fun parseDocxToMarkdown(bytes: ByteArray): String {
        val documentXml = extractZipEntry(bytes, "word/document.xml")
            ?: error("无效的 DOCX 文件结构 (缺少 word/document.xml)")

        return parseDocxXml(documentXml)
    }

    private fun extractZipEntry(bytes: ByteArray, targetName: String): String? {
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == targetName) {
                        val data = zip.readBytes()
                        return String(data, Charsets.UTF_8)
                    }
                    zip.closeEntry()
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun parseDocxXml(xml: String): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xml)))
        doc.documentElement.normalize()

        val bodyNode = findChildByLocalName(doc.documentElement, "body") ?: doc.documentElement
        val out = StringBuilder()

        val childNodes = bodyNode.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element

            when (element.localName ?: element.tagName.substringAfterLast(':')) {
                "p" -> {
                    val pMd = parseParagraph(element)
                    if (pMd.isNotBlank()) {
                        if (out.isNotEmpty() && !out.endsWith("\n\n")) {
                            out.append("\n\n")
                        }
                        out.append(pMd)
                    }
                }
                "tbl" -> {
                    val tblMd = parseTable(element)
                    if (tblMd.isNotBlank()) {
                        if (out.isNotEmpty() && !out.endsWith("\n\n")) {
                            out.append("\n\n")
                        }
                        out.append(tblMd)
                    }
                }
            }
        }

        return out.toString().trim()
    }

    private fun parseParagraph(pElement: Element): String {
        var headingLevel = 0
        var isList = false

        val pPr = findChildByLocalName(pElement, "pPr")
        if (pPr != null) {
            val pStyle = findChildByLocalName(pPr, "pStyle")
            if (pStyle != null) {
                val styleVal = pStyle.getAttribute("w:val").ifEmpty { pStyle.getAttribute("val") }
                headingLevel = when {
                    styleVal.contains("Heading1", ignoreCase = true) || styleVal.contains("Title", ignoreCase = true) -> 1
                    styleVal.contains("Heading2", ignoreCase = true) || styleVal.contains("Subtitle", ignoreCase = true) -> 2
                    styleVal.contains("Heading3", ignoreCase = true) -> 3
                    styleVal.contains("Heading4", ignoreCase = true) -> 4
                    styleVal.contains("Heading5", ignoreCase = true) -> 5
                    styleVal.contains("Heading6", ignoreCase = true) -> 6
                    else -> 0
                }
            }
            if (findChildByLocalName(pPr, "numPr") != null) {
                isList = true
            }
        }

        val textBuilder = StringBuilder()
        if (headingLevel in 1..6) {
            textBuilder.append("#".repeat(headingLevel)).append(" ")
        } else if (isList) {
            textBuilder.append("- ")
        }

        val runs = pElement.childNodes
        for (j in 0 until runs.length) {
            val rNode = runs.item(j)
            if (rNode.nodeType != Node.ELEMENT_NODE) continue
            val rElement = rNode as Element
            val localName = rElement.localName ?: rElement.tagName.substringAfterLast(':')
            if (localName == "r") {
                val rPr = findChildByLocalName(rElement, "rPr")
                val bold = rPr != null && (findChildByLocalName(rPr, "b") != null || findChildByLocalName(rPr, "bCs") != null)
                val italic = rPr != null && (findChildByLocalName(rPr, "i") != null || findChildByLocalName(rPr, "iCs") != null)
                val strike = rPr != null && findChildByLocalName(rPr, "strike") != null

                val runContent = StringBuilder()
                val rChildren = rElement.childNodes
                for (k in 0 until rChildren.length) {
                    val cNode = rChildren.item(k)
                    if (cNode.nodeType != Node.ELEMENT_NODE) continue
                    val cElement = cNode as Element
                    val cName = cElement.localName ?: cElement.tagName.substringAfterLast(':')
                    when (cName) {
                        "t" -> runContent.append(cElement.textContent)
                        "tab" -> runContent.append("    ")
                        "br", "cr" -> runContent.append("\n")
                    }
                }

                val t = runContent.toString()
                if (t.isNotBlank()) {
                    val leadingSpaces = t.takeWhile { it.isWhitespace() }
                    val trailingSpaces = t.takeLastWhile { it.isWhitespace() }
                    val trimmed = t.trim()
                    var formatted = trimmed
                    if (strike) formatted = "~~$formatted~~"
                    if (bold && italic) formatted = "***$formatted***"
                    else if (bold) formatted = "**$formatted**"
                    else if (italic) formatted = "*$formatted*"
                    textBuilder.append(leadingSpaces).append(formatted).append(trailingSpaces)
                } else {
                    textBuilder.append(t)
                }
            }
        }

        return textBuilder.toString().trimEnd()
    }

    private fun parseTable(tblElement: Element): String {
        val rows = mutableListOf<List<String>>()
        val tblChildren = tblElement.childNodes
        for (i in 0 until tblChildren.length) {
            val node = tblChildren.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val rowElement = node as Element
            if ((rowElement.localName ?: rowElement.tagName.substringAfterLast(':')) == "tr") {
                val cells = mutableListOf<String>()
                val rowChildren = rowElement.childNodes
                for (j in 0 until rowChildren.length) {
                    val cellNode = rowChildren.item(j)
                    if (cellNode.nodeType != Node.ELEMENT_NODE) continue
                    val cellElement = cellNode as Element
                    if ((cellElement.localName ?: cellElement.tagName.substringAfterLast(':')) == "tc") {
                        val cellTexts = mutableListOf<String>()
                        val tcChildren = cellElement.childNodes
                        for (k in 0 until tcChildren.length) {
                            val pNode = tcChildren.item(k)
                            if (pNode.nodeType != Node.ELEMENT_NODE) continue
                            val pEl = pNode as Element
                            if ((pEl.localName ?: pEl.tagName.substringAfterLast(':')) == "p") {
                                val pt = parseParagraph(pEl).trim()
                                if (pt.isNotEmpty()) cellTexts.add(pt)
                            }
                        }
                        val cellContent = cellTexts.joinToString("<br/>").replace("|", "\\|")
                        cells.add(cellContent.ifBlank { " " })
                    }
                }
                if (cells.isNotEmpty()) {
                    rows.add(cells)
                }
            }
        }

        if (rows.isEmpty()) return ""
        val maxCols = rows.maxOf { it.size }
        val sb = StringBuilder()
        rows.forEachIndexed { index, row ->
            val padded = (0 until maxCols).map { c -> row.getOrNull(c)?.ifBlank { " " } ?: " " }
            sb.append("| ").append(padded.joinToString(" | ")).append(" |\n")
            if (index == 0) {
                val div = (0 until maxCols).joinToString(" | ") { "---" }
                sb.append("| ").append(div).append(" |\n")
            }
        }
        return sb.toString().trim()
    }

    private fun findChildByLocalName(element: Element, localName: String): Element? {
        val list = element.childNodes
        for (i in 0 until list.length) {
            val node = list.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                val name = el.localName ?: el.tagName.substringAfterLast(':')
                if (name == localName) return el
            }
        }
        return null
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        return try {
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Throwable) {
            try {
                String(bytes, Charset.forName("GB18030"))
            } catch (_: Throwable) {
                String(bytes, Charsets.ISO_8859_1)
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()
}
