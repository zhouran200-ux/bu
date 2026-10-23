package com.lucent.app.data

import android.content.Context
import android.net.Uri
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedDocument(
    val title: String,
    val markdown: String
)

object MarkdownDocumentParser {

    private val SUPPORTED_EXTENSIONS = setOf("txt", "docx", "doc")

    fun isSupported(name: String): Boolean {
        val ext = extensionOf(name)
        return ext in SUPPORTED_EXTENSIONS
    }

    fun parseUri(context: Context, uri: Uri): Result<ParsedDocument> = AndroidDocumentImporter.parse(context, uri)

    fun parseTxtToMarkdown(bytes: ByteArray): String {
        DocumentInputLimits.checkInput(bytes)
        if (bytes.isEmpty()) return ""
        return PlainTextMarkdown.normalize(DocumentTextDecoder.decode(bytes))
    }

    fun parseDocToMarkdown(bytes: ByteArray): String {
        DocumentInputLimits.checkInput(bytes)
        return PlainTextMarkdown.normalize(DocTextExtractor.extract(bytes))
    }

    fun parseDocxToMarkdown(bytes: ByteArray): String {
        DocumentInputLimits.checkInput(bytes)
        val documentXml = extractZipEntry(bytes, "word/document.xml")
            ?: error("无效的 DOCX 文件结构 (缺少 word/document.xml)")

        return parseDocxXml(documentXml)
    }

    private fun extractZipEntry(bytes: ByteArray, targetName: String): String? {
        var expanded = 0L
        var entries = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= DocumentInputLimits.MAX_ZIP_ENTRIES) { "DOCX条目数超过限制" }
                val remaining = (DocumentInputLimits.MAX_EXPANDED_BYTES - expanded).coerceAtLeast(0).toInt()
                if (!entry.isDirectory && entry.name == targetName) {
                    val data = DocumentInputLimits.read(zip, minOf(remaining, DocumentInputLimits.MAX_XML_BYTES))
                    return String(data, Charsets.UTF_8)
                }
                expanded += DocumentInputLimits.discard(zip, remaining)
                zip.closeEntry()
            }
        }
        return null
    }

    private fun parseDocxXml(xml: String): String {
        DocumentXmlLimits.check(xml)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw org.xml.sax.SAXException("不允许外部XML实体") }
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
                    val separatorLength = if (out.isNotEmpty() && !out.endsWith("\n\n")) 2 else 0
                    val tblMd = parseTable(element, DocumentInputLimits.MAX_INPUT_BYTES - out.length - separatorLength)
                    if (tblMd.isNotBlank()) {
                        if (out.isNotEmpty() && !out.endsWith("\n\n")) {
                            out.append("\n\n")
                        }
                        out.append(tblMd)
                    }
                }
            }
            require(out.length <= DocumentInputLimits.MAX_INPUT_BYTES) { "转换后的正文超过大小限制" }
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
        val plainBuilder = StringBuilder()
        var prefixApplied = false
        if (headingLevel in 1..6) {
            textBuilder.append("#".repeat(headingLevel)).append(" ")
            prefixApplied = true
        } else if (isList) {
            textBuilder.append("- ")
            prefixApplied = true
        }

        for (rElement in visibleRuns(pElement)) {
            run {
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
                    plainBuilder.append(trimmed)
                } else {
                    textBuilder.append(t)
                }
            }
        }

        if (!prefixApplied) {
            val plain = plainBuilder.toString().trim()
            if (plain.isNotEmpty() && DocumentHeadings.plainTitle(plain) != null) {
                textBuilder.insert(0, "# ")
            }
        }

        return textBuilder.toString().trimEnd()
    }

    private fun visibleRuns(parent: Element): Sequence<Element> = sequence {
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            when (child.localName ?: child.tagName.substringAfterLast(':')) {
                "r" -> yield(child)
                "hyperlink", "ins", "moveTo", "smartTag", "fldSimple", "sdtContent" -> yieldAll(visibleRuns(child))
                "sdt" -> findChildByLocalName(child, "sdtContent")?.let { yieldAll(visibleRuns(it)) }
            }
        }
    }

    private fun parseTable(tblElement: Element, remainingCharacters: Int): String {
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
        var renderedLength = 6L * maxCols + 2L
        for (row in rows) {
            renderedLength += 4L * maxCols + 2L
            renderedLength += row.sumOf { it.length.toLong() - 1L }
            require(renderedLength - 1L <= remainingCharacters.toLong()) { "转换后的正文超过大小限制" }
        }
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

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()
}
