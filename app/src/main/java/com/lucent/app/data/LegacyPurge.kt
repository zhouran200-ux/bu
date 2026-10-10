package com.lucent.app.data

object LegacyPurge {

    private const val SAMPLE_TITLE = "用户预置文档"
    private const val SAMPLE_BODY_PREFIX = "〔CH:1〕 第一百零三章 拥抱"

    fun isSampleDocument(title: String, body: String): Boolean =
        title.trim() == SAMPLE_TITLE && body.startsWith(SAMPLE_BODY_PREFIX)

    suspend fun release(db: AppDatabase, note: Note) {
        db.notebookDao().getAllItemsOnce()
            .filter { it.itemKind == NotebookItem.KIND_NOTE && it.itemId == note.id }
            .forEach { db.notebookDao().deleteItemById(it.id) }
        TrashCleanup.purgeNote(db, note)
    }
}
