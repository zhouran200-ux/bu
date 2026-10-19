package com.lucent.app.ui

import org.junit.Assert.*
import org.junit.Test

class IncrementalMarkdownReuseTest {
    @Test fun appendingClosedParagraphsReusesOldBlocks() {
        val parser = IncrementalMarkdownParser()
        val newline = 10.toChar().toString()
        val prefix = "first" + newline + newline
        val first = parser.parse(prefix + "second")
        val next = parser.parse(prefix + "second" + newline + newline + "third")
        assertSame(first[0], next[0])
        assertEquals(parseBlocks(prefix + "second" + newline + newline + "third"), next)
    }

    @Test fun replacingTextResetsCachedPrefix() {
        val parser = IncrementalMarkdownParser()
        val newline = 10.toChar().toString()
        parser.parse("old" + newline + newline + "tail")
        assertEquals(parseBlocks("new text"), parser.parse("new text"))
    }

    @Test fun codeFenceTailDoesNotCloseAtBlankLines() {
        val parser = IncrementalMarkdownParser()
        val newline = 10.toChar().toString()
        val first = listOf("paragraph", "", "~~~", "code", "", "tail").joinToString(newline).replace("~~~", "```")
        parser.parse(first)
        val text = first + newline + "```" + newline + "after"
        assertEquals(parseBlocks(text), parser.parse(text))
    }

    @Test fun unfinishedLineDoesNotCloseAParagraph() {
        val parser = IncrementalMarkdownParser()
        assertEquals(0, IncrementalMarkdownParser.findSafeClosedSplit("first\n"))
        assertEquals(parseBlocks("first\n"), parser.parse("first\n"))
        assertEquals(listOf(MdBlock.Paragraph("first\nsecond")), parser.parse("first\nsecond"))
    }

    @Test fun repeatedBlankLinesRemainInTheOpenTail() {
        for (newline in listOf("\n", "\r\n", "\r")) {
            val parser = IncrementalMarkdownParser()
            val first = parser.parse("first$newline$newline")
            val text = "first$newline$newline \t$newline$newline" + "second"
            val next = parser.parse(text)
            assertEquals(parseBlocks(text), next)
            assertEquals(2, next.count { it == MdBlock.Blank })
            if (newline != "\r") assertSame(first[0], next[0])
        }
    }

    @Test fun closingFenceMustHaveAFinishedLine() {
        for (newline in listOf("\n", "\r\n", "\r")) {
            val parser = IncrementalMarkdownParser()
            val fence = "```"
            val initial = "$fence${newline}code$newline$fence"
            assertEquals(0, IncrementalMarkdownParser.findSafeClosedSplit(initial))
            assertEquals(parseBlocks(initial), parser.parse(initial))
            val continued = initial + "ignored"
            assertEquals(listOf(MdBlock.Code(listOf("code"))), parser.parse(continued))
            val finished = continued + newline + "after"
            assertEquals(parseBlocks(finished), parser.parse(finished))
        }
    }

    @Test fun crlfOffsetsAndSplitCarriageReturnsStayExact() {
        val parser = IncrementalMarkdownParser()
        val prefixes = listOf("first\r", "first\r\n", "first\r\n\r", "first\r\n\r\n", "first\r\n\r\nsecond")
        prefixes.forEach { assertEquals(parseBlocks(it), parser.parse(it)) }
        assertEquals(7, IncrementalMarkdownParser.findSafeClosedSplit("first\r\n\r\nsecond"))
        assertEquals(0, IncrementalMarkdownParser.findSafeClosedSplit("first\r\n\r"))
    }

    @Test fun everyCharacterAndLineBoundaryMatchesFullParsing() {
        for (text in streamingDocuments()) {
            assertPrefixesMatch(text, (0..text.length).toList())
            val newlineEnds = text.indices.filter { text[it] == '\n' || text[it] == '\r' }.map { it + 1 }
            assertPrefixesMatch(text, listOf(0) + newlineEnds + text.length)
        }
    }

    @Test fun everyTwoChunkSplitMatchesFullParsing() {
        for (text in streamingDocuments()) {
            for (split in 0..text.length) {
                assertPrefixesMatch(text, listOf(split, text.length))
            }
        }
    }

    @Test fun completedBlocksRetainIdentityAcrossAllTailUpdates() {
        for (newline in listOf("\n", "\r\n", "\r")) {
            val parser = IncrementalMarkdownParser()
            val fence = "```"
            val prefix = listOf("# heading", "", "paragraph", "", fence, "code", fence).joinToString(newline) + newline
            val initial = parser.parse(prefix + "tail")
            val closed = initial.dropLast(1)
            val tail = "tail${newline}continued$newline$newline$newline> quote$newline$newline" + "last"
            for (end in 4..tail.length) {
                val text = prefix + tail.take(end)
                val next = parser.parse(text)
                assertEquals(parseBlocks(text), next)
                closed.forEachIndexed { index, block -> assertSame(block, next[index]) }
            }
        }
    }

    @Test fun replacingAndClearingAfterCrLfBlocksResetsParsing() {
        val parser = IncrementalMarkdownParser()
        parser.parse("old\r\n\r\nbody\r\n\r\n")
        for (text in listOf("new\r\ntext", "", "next\n\n\nbody")) {
            assertEquals(parseBlocks(text), parser.parse(text))
        }
    }

    private fun assertPrefixesMatch(text: String, ends: List<Int>) {
        val parser = IncrementalMarkdownParser()
        for (end in ends) {
            val prefix = text.take(end)
            assertEquals("prefix=$end text=$text", parseBlocks(prefix), parser.parse(prefix))
        }
    }

    private fun streamingDocuments(): List<String> {
        val fence = "```"
        val documents = listOf(
            "first\nsecond\n\nthird",
            "first\n\n\n\nsecond\n \t\n\nthird\n\n\n",
            "\n\nleading\n\n# heading\n- bullet\n1. numbered\n> quote\n---\n\nlast",
            "before\n\n$fence\ncode\n\nline\n${fence}ignored\n\n\nafter",
            "$fence\ncode\n$fence\n$fence\nmore\n$fence\n\nlast",
            "before\n\n$fence\nunfinished\n\n",
            "before\n\n   ${fence}kotlin\ncode\n  $fence\n\n\nlast",
            "**bold\ncontinued**\n\n[label](https://example.test/long)\n\nlast"
        )
        return documents.flatMap { text ->
            listOf(text, text.replace("\n", "\r\n"), text.replace("\n", "\r"))
        } + "mixed\r\n\rparagraph\n\n$fence\rcode\r\n$fence\r\n\nlast"
    }
}
