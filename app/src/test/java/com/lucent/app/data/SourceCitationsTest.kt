package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceCitationsTest {

    @Test
    fun footnoteNumbersFollowFirstAppearanceAndReuseTheSameNumber() {
        val text = "甲。〔REF:42:3f2a91c4:S12-7〕乙。〔REF:7:0001:S1-1〕丙。〔REF:42:3f2a91c4:S12-7〕"

        val numbering = SourceCitations.numbering(text)

        assertEquals(1, numbering["〔REF:42:3f2a91c4:S12-7〕"])
        assertEquals(2, numbering["〔REF:7:0001:S1-1〕"])
        assertEquals(2, numbering.size)
    }

    @Test
    fun shortFormIsNumberedToo() {
        val numbering = SourceCitations.numbering("甲。〔S12-7〕乙。〔S12-8〕")

        assertEquals(1, numbering["〔S12-7〕"])
        assertEquals(2, numbering["〔S12-8〕"])
    }

    @Test
    fun fullyQualifiedMarkerCarriesSourceRevisionAndSlice() {
        val ref = SourceCitations.parse("〔REF:42:3f2a91c4:S12-7〕")

        assertEquals(42L, ref?.sourceId)
        assertEquals("3f2a91c4", ref?.revision)
        assertEquals("S12-7", ref?.sliceId)
        assertTrue(ref?.isFullyQualified == true)
    }

    @Test
    fun shortMarkerIsOnlyResolvedAgainstTheSingleKnownSource() {
        val ref = SourceCitations.parse("〔S12-7〕", singleSourceId = 5L, singleRevision = "abcd1234")

        assertEquals(5L, ref?.sourceId)
        assertEquals("abcd1234", ref?.revision)
        assertEquals("S12-7", ref?.sliceId)
        assertTrue(ref?.isFullyQualified == true)
    }

    @Test
    fun malformedMarkersAreNotRecognised() {
        assertNull(SourceCitations.parse("〔REF:42:S12〕"))
        assertNull(SourceCitations.parse("〔REF:42:3f2a91c4:S12〕"))
        assertNull(SourceCitations.parse("s12-7"))
        assertTrue(SourceCitations.numbering("普通正文，没有任何引用。").isEmpty())
    }

    @Test
    fun theMarkerRegexMatchesExactSpansSoRawTextCanBeRebuilt() {
        val text = "甲〔REF:1:00000001:S2-3〕乙"
        val matches = SourceCitations.marker.findAll(text).map { it.value to it.range }.toList()

        assertEquals(1, matches.size)
        assertEquals("〔REF:1:00000001:S2-3〕", matches.first().first)
        assertEquals(text.indexOf('〔'), matches.first().second.first)
    }
}
