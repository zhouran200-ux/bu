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
    fun testIsSupported() {
        assertTrue(MarkdownDocumentParser.isSupported("document.txt"))
        assertTrue(MarkdownDocumentParser.isSupported("REPORT.DOCX"))
        assertTrue(MarkdownDocumentParser.isSupported("my_file.docx"))

        assertFalse(MarkdownDocumentParser.isSupported("document.pdf"))
        assertFalse(MarkdownDocumentParser.isSupported("archive.zip"))
        assertFalse(MarkdownDocumentParser.isSupported("legacy.doc"))
        assertFalse(MarkdownDocumentParser.isSupported("image.png"))
    }

    @Test
    fun testParseTxtToMarkdown() {
        val raw = "第一行内容\r\n第二行内容\r\n\r\n第三行内容"
        val md = MarkdownDocumentParser.parseTxtToMarkdown(raw.toByteArray(Charsets.UTF_8))
        assertEquals("第一行内容\n第二行内容\n\n第三行内容", md)
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
