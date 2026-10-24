package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUiSchedulerAndParserTest {

    @Test
    fun testStreamUiSchedulerBurstFlush() {
        var flushedCount = 0
        var lastFlushedContent = ""

        val scheduler = StreamUiScheduler(
            timeWindowMs = 5000L,
            charBurstThreshold = 20
        ) { content, _ ->
            flushedCount++
            lastFlushedContent = content
        }

        scheduler.append("1234567890", null)
        assertEquals(0, flushedCount)

        scheduler.append("ABCDEFGHIJKLMNO", null)
        assertEquals(1, flushedCount)
        assertEquals("1234567890ABCDEFGHIJKLMNO", lastFlushedContent)

        scheduler.append("tail", null)
        scheduler.forceFlush()
        assertEquals(2, flushedCount)
        assertEquals("1234567890ABCDEFGHIJKLMNOtail", scheduler.getFinalContent())
    }

    @Test
    fun testStreamUiSchedulerStructuralBoundaryFlush() {
        var flushedCount = 0
        var lastFlushedContent = ""

        val scheduler = StreamUiScheduler(
            timeWindowMs = 5000L,
            charBurstThreshold = 100
        ) { content, _ ->
            flushedCount++
            lastFlushedContent = content
        }

        scheduler.append("第一段文字\n\n", null)
        assertEquals(1, flushedCount)
        assertEquals("第一段文字\n\n", lastFlushedContent)
    }

    @Test
    fun testIncrementalMarkdownParserClosedSplits() {
        val parser = IncrementalMarkdownParser()

        val blocks1 = parser.parse("这是第一段正在生成")
        assertTrue(blocks1.isNotEmpty())
        assertTrue(blocks1[0] is MdBlock.Paragraph)

        val text2 = "这是第一段已经完成。\n\n这是第二段正在进行..."
        val blocks2 = parser.parse(text2)
        assertTrue(blocks2.size >= 2)

        val text3 = "这是第一段已经完成。\n\n这是第二段正在进行...增加更多文字。"
        val blocks3 = parser.parse(text3)
        assertTrue(blocks3.size >= 2)
        assertEquals("这是第一段已经完成。", (blocks3[0] as MdBlock.Paragraph).text)
    }

    @Test
    fun testThoughtBurstUpdatesTheThoughtLaneOnly() {
        var contentFlushes = 0
        var lastThought = ""

        val scheduler = StreamUiScheduler(
            timeWindowMs = 5000L,
            charBurstThreshold = 100,
            thoughtWindowMs = 5000L,
            thoughtBurstThreshold = 8,
            onFlush = { _, _ -> contentFlushes++ },
            onThought = { lastThought = it }
        )

        scheduler.append("", "先看看")
        assertEquals(0, contentFlushes)
        assertEquals("", lastThought)

        scheduler.append("", "题目的条件")
        assertEquals(0, contentFlushes)
        assertEquals("先看看题目的条件", lastThought)
    }

    @Test
    fun testContentBurstCarriesTheReasoningAlong() {
        var lastContent = ""
        var lastReasoning: String? = null
        var thoughtPushes = 0

        val scheduler = StreamUiScheduler(
            timeWindowMs = 5000L,
            charBurstThreshold = 6,
            thoughtWindowMs = 5000L,
            thoughtBurstThreshold = 500,
            onFlush = { content, reasoning ->
                lastContent = content
                lastReasoning = reasoning
            },
            onThought = { thoughtPushes++ }
        )

        scheduler.append("推理中的", "第一段思考")
        scheduler.append("回答文字", null)

        assertEquals("推理中的回答文字", lastContent)
        assertEquals("第一段思考", lastReasoning)
        assertEquals(0, thoughtPushes)
    }

    @Test
    fun testThoughtBoundaryDoesNotInvalidateContentLane() {
        var contentFlushes = 0

        val scheduler = StreamUiScheduler(
            timeWindowMs = 5000L,
            charBurstThreshold = 500,
            thoughtWindowMs = 5000L,
            thoughtBurstThreshold = 500,
            onFlush = { _, _ -> contentFlushes++ }
        )

        scheduler.append("", "一段完整的思考\n\n")
        assertEquals(0, contentFlushes)
        assertEquals("一段完整的思考\n\n", scheduler.getFinalReasoning())
    }

    @Test
    fun testFlushPendingFlushesStrandedBuffer() {
        var flushedContent = ""
        val scheduler = StreamUiScheduler(
            timeWindowMs = 5000L,
            charBurstThreshold = 500
        ) { content, _ -> flushedContent = content }

        scheduler.append("stranded chunk", null)
        assertTrue(scheduler.hasPending())
        assertEquals("", flushedContent)

        scheduler.flushPending()
        assertFalse(scheduler.hasPending())
        assertEquals("stranded chunk", flushedContent)
    }
}
