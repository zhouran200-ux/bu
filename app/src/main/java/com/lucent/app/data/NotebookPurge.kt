package com.lucent.app.data

import androidx.room.withTransaction

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

    suspend fun importDocument(db: AppDatabase, notebookId: Long, note: Note): Long = db.withTransaction {
        check(db.notebookDao().exists(notebookId)) { "笔记本已删除，无法导入" }
        check(db.notebookDao().getItemsOnce(notebookId).none { it.itemKind == NotebookItem.KIND_NOTE }) {
            "每个笔记本只能导入一份文档"
        }
        val noteId = db.noteDao().insert(note)
        linkNote(db, notebookId, noteId)
        noteId
    }

    suspend fun purge(db: AppDatabase, notebookId: Long) {
        com.lucent.app.chat.guardedNotebookDeletion(
            dispatcher = kotlinx.coroutines.Dispatchers.Main.immediate,
            prepare = { com.lucent.app.chat.ChatSessions.prepareDelete(notebookId) },
            delete = {
                db.withTransaction {
                    val doomed = db.notebookDao().exclusiveItemIds(notebookId, NotebookItem.KIND_NOTE)
                    doomed.forEach { noteId ->
                        db.noteDao().getByIdOnce(noteId)?.let { TrashCleanup.purgeNote(db, it) }
                    }
                    db.chatDao().clearConversation(ChatScope.forNotebook(notebookId))
                    db.notebookDao().deleteItemsForNotebook(notebookId)
                    db.notebookDao().deleteById(notebookId)
                }
            },
            finish = { success -> com.lucent.app.chat.ChatSessions.finishDelete(notebookId, success) }
        )
    }
}
