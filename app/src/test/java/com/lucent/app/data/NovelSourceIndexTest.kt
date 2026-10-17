package com.lucent.app.data

import org.junit.Assert.*
import org.junit.Test

class NovelSourceIndexTest {
    @Test fun unchangedSourceReusesIndexAndEvictionRebuilds() {
        val note = Note(id = 987654321, title = "index-test", body = "正文")
        val first = NovelSourceIndex.get(note)
        assertSame(first, NovelSourceIndex.get(note))
        NovelSourceIndex.evict(note.id)
        assertNotSame(first, NovelSourceIndex.get(note))
        NovelSourceIndex.evict(note.id)
    }

    @Test fun changedBodyNeverReturnsOldRevision() {
        val note = Note(id = 987654322, title = "index-test", body = "old")
        val first = NovelSourceIndex.get(note)
        val next = NovelSourceIndex.get(note.copy(body = "new"))
        assertNotEquals(first.revision, next.revision)
        assertEquals("new", next.body)
        NovelSourceIndex.evict(note.id)
    }

    @Test fun oversizedDocumentIsReturnedButNotRetained() {
        val note = Note(id = 987654323, title = "large", body = "a".repeat(1_100_000))
        assertNotSame(NovelSourceIndex.get(note), NovelSourceIndex.get(note))
        NovelSourceIndex.evict(note.id)
    }
}
