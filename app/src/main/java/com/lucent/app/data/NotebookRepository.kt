package com.lucent.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class NotebookRepository(private val db: AppDatabase) {

    fun notebooks(): Flow<List<Notebook>> = db.notebookDao().getAll()

    fun itemCounts(): Flow<List<NotebookCount>> = db.notebookDao().itemCounts()

    suspend fun create(title: String): Long = db.notebookDao().insert(Notebook(title = title))

    suspend fun rename(notebook: Notebook, title: String) {
        db.notebookDao().update(notebook.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun reorder(notebooks: List<Notebook>) {
        db.notebookDao().updateAll(notebooks)
    }

    suspend fun touch(notebookId: Long, updatedAt: Long = System.currentTimeMillis()) {
        db.notebookDao().touch(notebookId, updatedAt)
    }

    suspend fun purge(notebookId: Long) {
        NotebookPurge.purge(db, notebookId)
    }

    suspend fun items(notebookId: Long): List<NotebookItem> = db.notebookDao().getItemsOnce(notebookId)

    suspend fun notes(ids: List<Long>): List<Note> =
        if (ids.isEmpty()) emptyList() else db.noteDao().getByIds(ids)

    suspend fun importDocument(notebookId: Long, note: Note): Long =
        NotebookPurge.importDocument(db, notebookId, note)

    suspend fun replaceDocument(notebookId: Long, note: Note): Long =
        NotebookPurge.replaceDocument(db, notebookId, note)

    suspend fun removeDocument(notebookId: Long) {
        NotebookPurge.removeDocument(db, notebookId)
    }

    companion object {
        fun from(context: Context): NotebookRepository =
            NotebookRepository(AppDatabase.getInstance(context.applicationContext))
    }
}
