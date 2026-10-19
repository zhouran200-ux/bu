package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingMarkdownTailTest {
    @Test fun splitLongCitationNeverShowsRawMetadataBeforeTheBadge() {
        val marker = "〔REF:23:v2-" + "a".repeat(64) + ":S12-34〕"
        for (end in 1 until marker.length) {
            assertEquals("answer ", streamingMarkdownTail("answer " + marker.take(end)))
        }
        assertEquals("answer $marker", streamingMarkdownTail("answer $marker"))
    }

    @Test fun ordinaryOrMalformedTextIsNotWithheldIndefinitely() {
        for (text in listOf("ordinary", "〔阅读〕", "〔REF:not valid", "〔REF:123\nnext", "〔REF:" + "a".repeat(129))) {
            assertEquals(text, streamingMarkdownTail(text))
        }
    }

    @Test fun codeExamplesAndImpossibleMarkerPrefixesRemainUnchanged() {
        val tick = 96.toChar().toString()
        for (text in listOf(
            tick + "〔REF:1:", tick.repeat(3) + "\n〔REF:1:",
            "〔REF:abc", "〔REF:1:v0", "〔REF:1:abc:S-", "〔REF:1:abc:S1:x"
        )) assertEquals(text, streamingMarkdownTail(text))
        val afterCode = tick + "code" + tick + " 〔REF:1:"
        assertEquals(tick + "code" + tick + " ", streamingMarkdownTail(afterCode))
    }

    @Test fun overflowingSourceIdsCannotBecomeValidAndAreNotHidden() {
        val invalid = "answer 〔REF:9223372036854775808"
        assertEquals(invalid, streamingMarkdownTail(invalid))
        assertEquals("answer ", streamingMarkdownTail("answer 〔REF:00001"))
        assertEquals("answer ", streamingMarkdownTail("answer 〔REF:0"))
    }

    @Test fun unmatchedCodeInEarlierBlocksDoesNotExposePendingCitation() {
        val tick = 96.toChar().toString()
        for (boundary in listOf("\n\nanswer ", "\n# heading ", "\n- bullet ", "\n1. item ", "\n> quote ")) {
            val prefix = tick + "unmatched" + boundary
            assertEquals(prefix, streamingMarkdownTail(prefix + "〔REF:1:v2-"))
        }
    }

    @Test fun completedCitationsStayVisibleWhenAnotherCitationIsPending() {
        val prefix = "first〔REF:1:abc:S1-1〕 second "
        assertEquals(prefix, streamingMarkdownTail(prefix + "〔REF:2:"))
        assertEquals(prefix + "〔REF:2:def:S2-2〕 end", streamingMarkdownTail(prefix + "〔REF:2:def:S2-2〕 end"))
    }
}
