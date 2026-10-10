package com.lucent.app.data

object NotebookPurge {

    fun noteIdsExclusiveTo(items: List<NotebookItem>, notebookId: Long): List<Long> {
        val owners = mutableMapOf<Long, MutableSet<Long>>()
        items.filter { it.itemKind == NotebookItem.KIND_NOTE }.forEach { item ->
            owners.getOrPut(item.itemId) { mutableSetOf() }.add(item.notebookId)
        }
        return owners.filterValues { it == setOf(notebookId) }.keys.toList()
    }

    suspend fun linkNote(db: AppDatabase, notebookId: Long, noteId: Long) {
        db.notebookDao().unlinkItemExcept(NotebookItem.KIND_NOTE, noteId, notebookId)
        db.notebookDao().insertItem(
            NotebookItem(
                notebookId = notebookId,
                itemKind = NotebookItem.KIND_NOTE,
                itemId = noteId
            )
        )
    }

    suspend fun releaseNote(db: AppDatabase, notebookId: Long, noteId: Long) {
        val exclusive = noteIdsExclusiveTo(db.notebookDao().getAllItemsOnce(), notebookId).contains(noteId)
        db.notebookDao().removeItem(notebookId, NotebookItem.KIND_NOTE, noteId)
        if (!exclusive) return
        db.noteDao().getByIdOnce(noteId)?.let { TrashCleanup.purgeNote(db, it) }
    }

    suspend fun purge(db: AppDatabase, notebookId: Long) {
        val doomed = noteIdsExclusiveTo(db.notebookDao().getAllItemsOnce(), notebookId)
        doomed.forEach { noteId ->
            db.noteDao().getByIdOnce(noteId)?.let { TrashCleanup.purgeNote(db, it) }
        }
        db.chatDao().clearConversation(ChatScope.forNotebook(notebookId))
        db.notebookDao().deleteItemsForNotebook(notebookId)
        db.notebookDao().deleteById(notebookId)
    }
}
