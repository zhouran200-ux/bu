package com.lucent.app.data

import android.content.Context

object NotebookPurge {

    fun noteIdsExclusiveTo(items: List<NotebookItem>, notebookId: Long): List<Long> {
        val owners = mutableMapOf<Long, MutableSet<Long>>()
        items.filter { it.itemKind == NotebookItem.KIND_NOTE }.forEach { item ->
            owners.getOrPut(item.itemId) { mutableSetOf() }.add(item.notebookId)
        }
        return owners.filterValues { it == setOf(notebookId) }.keys.toList()
    }

    suspend fun releaseNote(context: Context, db: AppDatabase, notebookId: Long, noteId: Long) {
        val exclusive = noteIdsExclusiveTo(db.notebookDao().getAllItemsOnce(), notebookId).contains(noteId)
        db.notebookDao().removeItem(notebookId, NotebookItem.KIND_NOTE, noteId)
        if (!exclusive) return
        db.noteDao().getByIdOnce(noteId)?.let { TrashCleanup.purgeNote(context, db, it) }
    }

    suspend fun purge(context: Context, db: AppDatabase, notebookId: Long) {
        val doomed = noteIdsExclusiveTo(db.notebookDao().getAllItemsOnce(), notebookId)
        doomed.forEach { noteId ->
            db.noteDao().getByIdOnce(noteId)?.let { TrashCleanup.purgeNote(context, db, it) }
        }
        db.chatDao().clearConversation(ChatScope.forNotebook(notebookId))
        db.notebookDao().deleteItemsForNotebook(notebookId)
        db.notebookDao().deleteById(notebookId)
    }
}
