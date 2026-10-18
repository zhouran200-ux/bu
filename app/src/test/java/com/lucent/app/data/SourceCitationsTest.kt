package com.lucent.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceCitationsTest {

    @Test
    fun commonModelBracketVariantsAreRecognized() {
        assertEquals("S12-7", SourceCitations.parse("【S：12-7】", 5L, "rev")?.sliceId)
        assertEquals("S12-8", SourceCitations.parse("[S:12-8]", 5L, "rev")?.sliceId)
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
    }

    @Test
    fun invalidFullSourceIdsNeverBecomeShortReferences() {
        assertNull(SourceCitations.parse("〔REF:999999999999999999999999:0001:S1-1〕", 5L, "0001"))
        assertNull(SourceCitations.parse("〔REF:0:0001:S1-1〕"))
    }

    @Test
    fun versionedDigestIsParsedAndMatchingSourceMayOpen() {
        val revision = SourceTagging.revisionOf("正文", "书")
        val ref = SourceCitations.parse("〔REF:42:$revision:S1-1〕")!!
        assertEquals(revision, ref.revision)
        assertNull(SourceCitations.rejectionReason(ref, 42L, revision))
        assertTrue(SourceCitations.rejectionReason(ref, 42L, SourceTagging.revisionOf("新正文", "书")) != null)
        assertTrue(SourceCitations.rejectionReason(ref, 7L, revision) != null)
        assertTrue(SourceCitations.rejectionReason(ref, null, null) != null)
    }

    @Test
    fun oldAndUnversionedReferencesRemainDisplayableButCannotOpenCurrentSources() {
        val revision = SourceTagging.revisionOf("正文")
        val old = SourceCitations.parse("〔REF:42:3f2a91c4:S1-1〕")!!
        val short = SourceCitations.parse("〔S1-1〕")!!
        assertTrue(SourceCitations.rejectionReason(old, 42L, revision) != null)
        assertTrue(SourceCitations.rejectionReason(short, 42L, revision) != null)
    }

    @Test
    fun indexVersionChangeAndTruncatedDigestsCannotPass() {
        val revision = SourceTagging.revisionOf("正文")
        val next = revision.replace("v2-", "v3-")
        val ref = SourceCitations.parse("〔REF:42:$next:S1-1〕")!!
        assertTrue(SourceCitations.rejectionReason(ref, 42L, revision) != null)
        assertNull(SourceCitations.parse("〔REF:42:v1-abcdef:S1-1〕"))
    }

    @Test
    fun colonTagsResolveOnlyUsingSavedReplyBinding() {
        val bound = SourceCitations.parse("〔S：12-7〕", 42L, "v2-saved")!!
        assertEquals("S12-7", bound.sliceId)
        assertNull(SourceCitations.rejectionReason(bound, 42L, "v2-saved"))
        assertTrue(SourceCitations.rejectionReason(bound, 42L, "v2-new") != null)
        assertTrue(SourceCitations.rejectionReason(SourceCitations.parse("〔S：12-7〕")!!, 42L, "v2-new") != null)
        assertEquals("S12-7", SourceCitations.parse("〔S:12-7〕")?.sliceId)
        assertNull(SourceCitations.parse("〔S：0-0〕"))
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
