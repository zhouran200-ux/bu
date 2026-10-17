package com.lucent.app.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownDocumentParserTest {

    @Test
    fun docxRejectsInternalAndExternalEntityDeclarations() {
        for (declaration in listOf("<!DOCTYPE document [<!ENTITY entity 'expanded'>]>", "<!DOCTYPE document SYSTEM 'file:///nonexistent'>")) {
            val xml = declaration + "<document><body><p><r><t>safe</t></r></p></body></document>"
            assertTrue(runCatching { MarkdownDocumentParser.parseDocxToMarkdown(docxWith(xml)) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun testIsSupported() {
        assertTrue(MarkdownDocumentParser.isSupported("document.txt"))
        assertTrue(MarkdownDocumentParser.isSupported("REPORT.DOCX"))
        assertTrue(MarkdownDocumentParser.isSupported("my_file.docx"))
        assertTrue(MarkdownDocumentParser.isSupported("legacy.doc"))
        assertTrue(MarkdownDocumentParser.isSupported("旧文档.DOC"))

        assertFalse(MarkdownDocumentParser.isSupported("document.pdf"))
        assertFalse(MarkdownDocumentParser.isSupported("archive.zip"))
        assertFalse(MarkdownDocumentParser.isSupported("image.png"))
    }

    @Test
    fun testParseTxtToMarkdown() {
        val raw = "第一行内容。\r\n第二行内容。\r\n\r\n第三行内容。"
        val md = MarkdownDocumentParser.parseTxtToMarkdown(raw.toByteArray(Charsets.UTF_8))
        assertEquals("第一行内容。\n\n第二行内容。\n\n第三行内容。", md)
    }

    @Test
    fun txtHardWrappedLinesAreJoinedAndParagraphBreaksAreKept() {
        val raw = "他站起身，\n看了看窗外。\n她没有说话。"
        val md = MarkdownDocumentParser.parseTxtToMarkdown(raw.toByteArray(Charsets.UTF_8))
        assertEquals("他站起身，看了看窗外。\n\n她没有说话。", md)
    }

    @Test
    fun txtChapterLinesBecomeMarkdownHeadings() {
        val raw = "第57章 剥皮\n他翻开了那本书。\n第五十八章 新线索\n线索出现了。"
        val md = MarkdownDocumentParser.parseTxtToMarkdown(raw.toByteArray(Charsets.UTF_8))
        assertEquals("# 第57章 剥皮\n\n他翻开了那本书。\n\n# 第五十八章 新线索\n\n线索出现了。", md)
    }

    @Test
    fun txtBomVariantsAreStrippedInsteadOfLeakingIntoTheText() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "标题".toByteArray(Charsets.UTF_8)
        assertEquals("标题", MarkdownDocumentParser.parseTxtToMarkdown(utf8Bom))

        val utf16Le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "标题".toByteArray(Charsets.UTF_16LE)
        assertEquals("标题", MarkdownDocumentParser.parseTxtToMarkdown(utf16Le))

        val utf16Be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "标题".toByteArray(Charsets.UTF_16BE)
        assertEquals("标题", MarkdownDocumentParser.parseTxtToMarkdown(utf16Be))
    }

    @Test
    fun gb18030TextIsRecoveredWhenItIsNotValidUtf8() {
        val gbk = "中文段落".toByteArray(java.nio.charset.Charset.forName("GB18030"))
        assertEquals("中文段落", MarkdownDocumentParser.parseTxtToMarkdown(gbk))
    }

    @Test
    fun emptyAndBlankTxtFilesProduceNoContent() {
        assertEquals("", MarkdownDocumentParser.parseTxtToMarkdown(ByteArray(0)))
        assertEquals("", MarkdownDocumentParser.parseTxtToMarkdown("   \r\n\r\n  ".toByteArray(Charsets.UTF_8)))
    }

    private fun docxWith(xml: String, entryName: String = "word/document.xml"): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(xml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    @Test
    fun docxWithoutTheDocumentPartFailsInsteadOfReturningSilentGarbage() {
        val failure = runCatching {
            MarkdownDocumentParser.parseDocxToMarkdown(docxWith("<xml/>", entryName = "word/other.xml"))
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException, "缺少 document.xml 必须报错")
    }

    @Test
    fun docxWithBrokenXmlFailsInsteadOfHanging() {
        val failure = runCatching {
            MarkdownDocumentParser.parseDocxToMarkdown(docxWith("<w:document><w:body>"))
        }.exceptionOrNull()
        assertTrue(failure is Exception)
    }

    @Test
    fun docxHeadingLevelsAndListsBecomeMarkdown() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:pPr><w:pStyle w:val="Heading3"/></w:pPr><w:r><w:t>三级标题</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/></w:numPr></w:pPr><w:r><w:t>列表项</w:t></w:r></w:p>
                <w:p><w:r><w:rPr><w:b/><w:i/></w:rPr><w:t>粗斜体</w:t></w:r><w:r><w:rPr><w:strike/></w:rPr><w:t>删除线</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val md = MarkdownDocumentParser.parseDocxToMarkdown(docxWith(xml))
        assertTrue(md.contains("### 三级标题"), md)
        assertTrue(md.contains("- 列表项"), md)
        assertTrue(md.contains("***粗斜体***"), md)
        assertTrue(md.contains("~~删除线~~"), md)
    }

    @Test
    fun docxTablesBecomeMarkdownTablesWithEscapedPipes() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:tbl>
                  <w:tr><w:tc><w:p><w:r><w:t>列A</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>列B</w:t></w:r></w:p></w:tc></w:tr>
                  <w:tr><w:tc><w:p><w:r><w:t>a|b</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>2</w:t></w:r></w:p></w:tc></w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()
        val md = MarkdownDocumentParser.parseDocxToMarkdown(docxWith(xml))
        assertTrue(md.contains("| 列A | 列B |"), md)
        assertTrue(md.contains("| --- | --- |"), md)
        assertTrue(md.contains("""a\|b"""), md)
    }

    @Test
    fun docxPlainChapterParagraphsBecomeHeadingsWithoutAWordStyle() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>第57章 剥皮</w:t></w:r></w:p>
                <w:p><w:r><w:t>他翻开了那本书。</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val md = MarkdownDocumentParser.parseDocxToMarkdown(docxWith(xml))
        assertTrue(md.startsWith("# 第57章 剥皮"), md)
        assertTrue(md.contains("他翻开了那本书。"), md)
        assertFalse(md.contains("# 他翻开了"), md)
    }

    @Test
    fun testParseDocxToMarkdown() {
        val docXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr>
                            <w:pStyle w:val="Heading1"/>
                        </w:pPr>
                        <w:r>
                            <w:t>第一章 概述</w:t>
                        </w:r>
                    </w:p>
                    <w:p>
                        <w:r>
                            <w:t>这是一段正文，包含</w:t>
                        </w:r>
                        <w:r>
                            <w:rPr>
                                <w:b/>
                            </w:rPr>
                            <w:t>粗体重点</w:t>
                        </w:r>
                        <w:r>
                            <w:t>以及</w:t>
                        </w:r>
                        <w:r>
                            <w:rPr>
                                <w:i/>
                            </w:rPr>
                            <w:t>斜体描述</w:t>
                        </w:r>
                        <w:r>
                            <w:t>。</w:t>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(docXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val md = MarkdownDocumentParser.parseDocxToMarkdown(baos.toByteArray())
        assertTrue(md.contains("# 第一章 概述"))
        assertTrue(md.contains("**粗体重点**"))
        assertTrue(md.contains("*斜体描述*"))
    }
}
