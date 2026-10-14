package com.lucent.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentSectionTest {

    @Test
    fun plainChaptersAreRecognisedWithoutBlankLines() {
        val body = "第一章 起点\n正文一\n第二回 回目\n正文二\n第3节 小节\n正文三"
        val blocks = splitRenderBlocks(body)

        assertEquals(3, blocks.size)
        assertEquals(listOf("第一章 起点", "第二回 回目", "第3节 小节"), blocks.map { it.sectionTitle })
        assertEquals(listOf(0, 1, 2), blocks.map { it.sectionIndex })
    }

    @Test
    fun webNovelChapterFormsAreAllRecognised() {
        val forms = listOf(
            "第一章 起点",
            "第 一 章 空格式",
            "第1章 数字式",
            "第001章 补零式",
            "第0001章 四位补零",
            "第１章 全角数字",
            "第１００章 全角百章",
            "第一百零八章 中文数字",
            "第108章无分隔",
            "01章 无前缀数字",
            "1章 无前缀单数字",
            "第12回 回目",
            "第3节 小节",
            "第2卷 卷二",
            "第4篇 篇名",
            "第5集 集名",
            "第6话 话名",
            "第7幕 幕名",
            "序章",
            "楔子",
            "引子",
            "尾声",
            "番外篇 别传",
            "Chapter 12",
            "【第9章】 方括号",
            "第10章：冒号分隔",
            "第11章、顿号分隔",
            "第12章-连字符分隔"
        )
        forms.forEach { line ->
            assertEquals(1, splitDocumentSections(line).size, "未识别为章节: $line")
            assertEquals(line, documentSectionTitle(line), "标题文本不符: $line")
        }
    }

    @Test
    fun chapterMentionsInParagraphsDoNotCreateSections() {
        val body = "第一章 起点\n他翻到了第58章，继续读下去。\n第58章的内容仍未结束。\n12章鱼很好吃。\n楔子是他写的。\n#不是标题"
        assertEquals(1, splitDocumentSections(body).size)
    }

    @Test
    fun headingLabelsRemoveMarkdownMarkersAndFileBom() {
        assertEquals("第一章 开始", documentSectionTitle("  ## 第一章 开始 ###  "))
        assertEquals("第一章 起点", documentSectionTitle("\uFEFF第一章 起点"))
        assertEquals("第9章 方括号", documentSectionTitle("【第9章 方括号】"))
    }

    @Test
    fun headingsInsideCodeFencesDoNotCreateSections() {
        val body = "# 文档\n```text\n# 代码标题\n第一章 起点\n```\n## 下一节\n正文"
        val blocks = splitRenderBlocks(body)
        assertEquals(listOf("文档", "下一节"), blocks.map { it.sectionTitle })
    }

    @Test
    fun longChaptersKeepTheirTitlesAcrossEveryRenderBlock() {
        val body = "第一章 起点\n" + "长段正文。".repeat(200) + "\n第二章 下一章\n另一章正文"
        val blocks = splitRenderBlocks(body, maxChars = 120)
        val nextChapter = blocks.indexOfFirst { it.sectionIndex == 1 }

        assertTrue(nextChapter > 1)
        blocks.take(nextChapter).indices.forEach { index ->
            assertEquals("第一章 起点", currentDocumentSectionTitle(blocks, index, "书名"))
        }
        assertEquals("第二章 下一章", currentDocumentSectionTitle(blocks, nextChapter, "书名"))
    }

    @Test
    fun aSingleChapterStillProvidesItsActualTitle() {
        val blocks = splitRenderBlocks("第一章 起点\n正文")
        assertEquals("第一章 起点", currentDocumentSectionTitle(blocks, 0, "书名"))
    }

    @Test
    fun headinglessAndEmptyDocumentsUseReadableFallbacks() {
        val blocks = splitRenderBlocks("没有章节标题的正文")
        assertEquals("导入的资料", currentDocumentSectionTitle(blocks, 0, "导入的资料"))
        assertEquals("正文", currentDocumentSectionTitle(blocks, 0, ""))
        assertEquals("资料", currentDocumentSectionTitle(emptyList(), 0, "资料"))
    }

    @Test
    fun footerAndOutOfRangePositionsKeepTheNearestChapter() {
        val blocks = splitRenderBlocks("# 开头\n一\n# 结尾\n二")
        assertEquals("开头", currentDocumentSectionTitle(blocks, -1, "资料"))
        assertEquals("结尾", currentDocumentSectionTitle(blocks, blocks.size, "资料"))
        assertEquals("结尾", currentDocumentSectionTitle(blocks, Int.MAX_VALUE, "资料"))
    }

    @Test
    fun switchingDocumentsDoesNotReuseThePreviousChapterTitle() {
        val first = splitRenderBlocks("第一章 旧书章\n正文")
        val second = splitRenderBlocks("第1章 新书章\n正文")
        assertEquals("第一章 旧书章", currentDocumentSectionTitle(first, 0, "旧书"))
        assertEquals("第1章 新书章", currentDocumentSectionTitle(second, 0, "新书"))
    }

    @Test
    fun longSectionsAreChunkedAtLineBoundariesNotInsideALine() {
        val lines = (1..40).map { "第${it}行内容" }
        val blocks = splitRenderBlocks(lines.joinToString("\n"), maxChars = 12)
        assertTrue(blocks.size > 1)
        blocks.forEach { block ->
            block.text.lines().filter { it.isNotBlank() }.forEach { line ->
                assertTrue(lines.contains(line), "分块把一行切成了半截: $line")
            }
        }
        assertEquals(lines, blocks.flatMap { it.text.lines() }.filter { it.isNotBlank() })
    }

    @Test
    fun aSingleLongParagraphIsKeptWholeInsteadOfBeingSplitMidParagraph() {
        val paragraph = "字".repeat(1500)
        val blocks = splitRenderBlocks("开头段落\n\n$paragraph", maxChars = 400)
        assertTrue(blocks.any { it.text == paragraph }, "长段落不应被拆开")
    }
}