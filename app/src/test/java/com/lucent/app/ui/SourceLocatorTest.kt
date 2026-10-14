package com.lucent.app.ui

import com.lucent.app.data.SourceTagging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceLocatorTest {

    @Test
    fun blockOffsetsPointIntoTheOriginalBody() {
        val body = "# 第一章 起点\n\n正文一。\n\n正文二。"
        val blocks = splitRenderBlocks(body)

        assertTrue(blocks.isNotEmpty())
        blocks.forEach { block ->
            assertTrue(block.startOffset >= 0)
            assertTrue(block.endOffset <= body.length)
            assertTrue(block.startOffset < block.endOffset)
            assertEquals(block.text, body.substring(block.startOffset, block.endOffset))
        }
    }

    @Test
    fun offsetsStayExactAcrossSeveralChapters() {
        val body = "# 第一章 起点\n\n第一段。\n\n# 第二章 转折\n\n第二段。"
        val blocks = splitRenderBlocks(body)

        assertEquals(2, blocks.size)
        blocks.forEach { block ->
            assertEquals(block.text, body.substring(block.startOffset, block.endOffset))
        }
    }

    @Test
    fun chunksOfALongChapterKeepStrictlyIncreasingOffsets() {
        val body = "# 第一章\n\n" + (1..40).joinToString("\n\n") { "第${it}段正文。" }
        val blocks = splitRenderBlocks(body, maxChars = 40)

        assertTrue(blocks.size > 1)
        val offsets = blocks.map { it.startOffset }
        assertEquals(offsets.sorted(), offsets)
        assertEquals(offsets.distinct(), offsets)
    }

    @Test
    fun aSliceResolvesToTheBlockInsideItsOwnChapter() {
        val body = "# 第一章 起点\n\n第一段正文。\n\n第二段正文。\n\n# 第二章 转折\n\n第三段正文。"
        val blocks = splitRenderBlocks(body)
        val slice = SourceTagging.tag("书名", body).slices.first { it.id == "S2-1" }

        val index = SourceLocator.blockIndexForSlice("书名", body, blocks, "S2-1")

        assertTrue(index >= 0)
        assertEquals(1, blocks[index].sectionIndex)
        assertTrue(blocks[index].startOffset <= slice.startOffset)
    }

    @Test
    fun unknownSliceIdsResolveToNothing() {
        val body = "# 第一章\n\n正文。"
        val blocks = splitRenderBlocks(body)

        assertEquals(-1, SourceLocator.blockIndexForSlice("书名", body, blocks, "S99-9"))
    }

    @Test
    fun offsetsBeforeTheFirstBlockResolveToTheFirstBlock() {
        val blocks = splitRenderBlocks("# 第一章\n\n正文。")

        assertEquals(0, SourceLocator.blockIndexForOffset(blocks, 0))
        assertEquals(-1, SourceLocator.blockIndexForOffset(emptyList(), 0))
    }
}
