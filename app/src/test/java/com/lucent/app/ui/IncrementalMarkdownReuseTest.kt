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
}
