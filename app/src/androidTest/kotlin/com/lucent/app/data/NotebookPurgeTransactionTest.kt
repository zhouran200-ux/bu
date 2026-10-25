package com.lucent.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookPurgeTransactionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @After fun closeDatabase() { db.close() }

    @Test fun exclusiveItemQueryPreservesSharedAndForeignDocuments() = runBlocking {
        val first = db.notebookDao().insert(Notebook(title = "first"))
        val second = db.notebookDao().insert(Notebook(title = "second"))
        val own = db.noteDao().insert(Note(title = "own", body = "body"))
        val shared = db.noteDao().insert(Note(title = "shared", body = "body"))
        db.notebookDao().insertItem(NotebookItem(notebookId = first, itemKind = NotebookItem.KIND_NOTE, itemId = own))
        db.notebookDao().insertItem(NotebookItem(notebookId = first, itemKind = NotebookItem.KIND_NOTE, itemId = shared))
        db.notebookDao().insertItem(NotebookItem(notebookId = second, itemKind = NotebookItem.KIND_NOTE, itemId = shared))
        assertTrue(db.notebookDao().exists(first))
        assertFalse(db.notebookDao().exists(Long.MAX_VALUE))
        assertEquals(listOf(own), db.notebookDao().exclusiveItemIds(first, NotebookItem.KIND_NOTE))
        db.notebookDao().unlinkEverywhere(NotebookItem.KIND_NOTE, shared)
        assertEquals(listOf(own), db.notebookDao().getItemsOnce(first).map { it.itemId })
        assertTrue(db.notebookDao().getItemsOnce(second).isEmpty())
    }

    @Test fun lateDeleteFailureRollsBackDocumentsChatAndLinksAndAllowsRetry() = runBlocking {
        val notebookId = db.notebookDao().insert(Notebook(id = 900001, title = "retained"))
        val noteId = db.noteDao().insert(Note(title = "source", body = "original"))
        NotebookPurge.linkNote(db, notebookId, noteId)
        db.chatDao().insert(ChatMessage(id = 910001, role = "user", content = "question",
            conversationId = ChatScope.forNotebook(notebookId)))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER prevent_notebook_delete BEFORE DELETE ON notebooks BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        )
        try { NotebookPurge.purge(db, notebookId); fail("must report injected failure") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertNotNull(db.noteDao().getByIdOnce(noteId))
        assertEquals(1, db.chatDao().getForConversationOnce(ChatScope.forNotebook(notebookId)).size)
        assertEquals(1, db.notebookDao().getItemsOnce(notebookId).size)
        assertEquals(1, db.notebookDao().getAllOnce().size)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER prevent_notebook_delete")
        NotebookPurge.purge(db, notebookId)
        assertNull(db.noteDao().getByIdOnce(noteId))
        assertTrue(db.chatDao().getForConversationOnce(ChatScope.forNotebook(notebookId)).isEmpty())
        assertTrue(db.notebookDao().getAllOnce().isEmpty())
    }

    @Test fun secondImportIsRejectedWithoutCreatingOrphanDocument() = runBlocking {
        val notebook = db.notebookDao().insert(Notebook(id = 900004, title = "single"))
        val first = NotebookPurge.importDocument(db, notebook, Note(title = "first", body = "original"))
        try {
            NotebookPurge.importDocument(db, notebook, Note(title = "second", body = "must not insert"))
            fail("second document must be rejected")
        } catch (_: IllegalStateException) { }
        assertEquals(first, db.noteDao().getAllOnce().single().id)
        assertEquals(1, db.notebookDao().getItemsOnce(notebook).size)
        try {
            NotebookPurge.importDocument(db, 999999, Note(title = "missing", body = "must not insert"))
            fail("deleted notebook must reject import")
        } catch (_: IllegalStateException) { }
        assertEquals(1, db.noteDao().getAllOnce().size)
    }

    @Test fun documentChangesBumpTheNotebookModifiedTimeButIdleRemovalDoesNot() = runBlocking {
        val notebook = db.notebookDao().insert(Notebook(id = 900005, title = "modified", updatedAt = 1_000L))
        NotebookPurge.importDocument(db, notebook, Note(title = "doc", body = "body"))
        assertTrue(db.notebookDao().getAllOnce().single().updatedAt > 1_000L)
        db.notebookDao().touch(notebook, 2_000L)
        NotebookPurge.removeDocument(db, notebook)
        assertTrue(db.notebookDao().getAllOnce().single().updatedAt > 2_000L)
        db.notebookDao().touch(notebook, 3_000L)
        NotebookPurge.removeDocument(db, notebook)
        assertEquals(3_000L, db.notebookDao().getAllOnce().single().updatedAt)
    }

    @Test fun staleReorderPreservesFreshTitleAndModificationTime() = runBlocking {
        val dao = db.notebookDao()
        val id = dao.insert(Notebook(title = "old", updatedAt = 100L))
        val stale = dao.getAllOnce().single()
        dao.rename(id, "fresh", 200L)
        NotebookRepository(db).reorder(listOf(stale.copy(manualOrder = 1000)))
        val saved = dao.getAllOnce().single()
        assertEquals("fresh", saved.title)
        assertEquals(200L, saved.updatedAt)
        assertEquals(1000, saved.manualOrder)
        dao.rename(id, "fresh", 300L)
        assertEquals(200L, dao.getAllOnce().single().updatedAt)
        dao.rename(id, "renamed", 150L)
        assertEquals(200L, dao.getAllOnce().single().updatedAt)
        assertEquals(1000, dao.getAllOnce().single().manualOrder)
    }

    @Test fun sharedSourceSurvivesDeletingOnlyOneNotebook() = runBlocking {
        val first = db.notebookDao().insert(Notebook(id = 900002, title = "first"))
        val second = db.notebookDao().insert(Notebook(id = 900003, title = "second"))
        val note = db.noteDao().insert(Note(title = "shared", body = "preserve"))
        db.notebookDao().insertItem(NotebookItem(notebookId = first, itemKind = NotebookItem.KIND_NOTE, itemId = note))
        db.notebookDao().insertItem(NotebookItem(notebookId = second, itemKind = NotebookItem.KIND_NOTE, itemId = note))
        NotebookPurge.purge(db, first)
        assertNotNull(db.noteDao().getByIdOnce(note))
        assertEquals(1, db.notebookDao().getItemsOnce(second).size)
        assertEquals(second, db.notebookDao().getAllOnce().single().id)
    }
}
