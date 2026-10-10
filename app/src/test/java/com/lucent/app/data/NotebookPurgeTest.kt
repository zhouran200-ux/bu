package com.lucent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookPurgeTest {

    private fun noteItem(notebookId: Long, noteId: Long, rowId: Long = 0L) =
        NotebookItem(id = rowId, notebookId = notebookId, itemKind = NotebookItem.KIND_NOTE, itemId = noteId)

    private fun taskItem(notebookId: Long, taskId: Long, rowId: Long = 0L) =
        NotebookItem(id = rowId, notebookId = notebookId, itemKind = "TASK", itemId = taskId)

    @Test
    fun testOnlyNotesHeldByThisNotebookAloneAreReleased() {
        val items = listOf(
            noteItem(notebookId = 1L, noteId = 10L),
            noteItem(notebookId = 1L, noteId = 11L),
            noteItem(notebookId = 2L, noteId = 11L),
            noteItem(notebookId = 2L, noteId = 12L)
        )
        assertEquals(listOf(10L), NotebookPurge.noteIdsExclusiveTo(items, 1L))
        assertEquals(listOf(12L), NotebookPurge.noteIdsExclusiveTo(items, 2L))
    }

    @Test
    fun testSharedNotesAreNeverReleased() {
        val items = listOf(
            noteItem(notebookId = 1L, noteId = 11L),
            noteItem(notebookId = 2L, noteId = 11L),
            noteItem(notebookId = 3L, noteId = 11L)
        )
        assertTrue(NotebookPurge.noteIdsExclusiveTo(items, 1L).isEmpty())
        assertTrue(NotebookPurge.noteIdsExclusiveTo(items, 2L).isEmpty())
        assertTrue(NotebookPurge.noteIdsExclusiveTo(items, 3L).isEmpty())
    }

    @Test
    fun testDuplicateRowsInsideOneNotebookStillCountAsExclusive() {
        val items = listOf(
            noteItem(notebookId = 1L, noteId = 10L, rowId = 1L),
            noteItem(notebookId = 1L, noteId = 10L, rowId = 2L)
        )
        assertEquals(listOf(10L), NotebookPurge.noteIdsExclusiveTo(items, 1L))
    }

    @Test
    fun testOtherItemKindsAreIgnored() {
        val items = listOf(
            noteItem(notebookId = 1L, noteId = 10L),
            taskItem(notebookId = 1L, taskId = 99L),
            taskItem(notebookId = 2L, taskId = 99L)
        )
        assertEquals(listOf(10L), NotebookPurge.noteIdsExclusiveTo(items, 1L))
        assertTrue(NotebookPurge.noteIdsExclusiveTo(items, 2L).isEmpty())
    }

    @Test
    fun testUnknownNotebookReleasesNothing() {
        val items = listOf(noteItem(notebookId = 1L, noteId = 10L))
        assertTrue(NotebookPurge.noteIdsExclusiveTo(items, 77L).isEmpty())
        assertTrue(NotebookPurge.noteIdsExclusiveTo(emptyList(), 1L).isEmpty())
    }
}
