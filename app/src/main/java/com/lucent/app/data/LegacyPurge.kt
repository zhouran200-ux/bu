package com.lucent.app.data

import android.content.Context

object LegacyPurge {

    private const val SAMPLE_TITLE = "用户预置文档"
    private const val SAMPLE_BODY_PREFIX = "〔CH:1〕 第一百零三章 拥抱"

    fun isSampleDocument(title: String, body: String): Boolean =
        title.trim() == SAMPLE_TITLE && body.startsWith(SAMPLE_BODY_PREFIX)

    suspend fun purgeSampleDocuments(context: Context, db: AppDatabase) {
        val doomed = db.noteDao().getAllOnce().filter { isSampleDocument(it.title, it.body) }
        if (doomed.isEmpty()) return
        val ids = doomed.map { it.id }.toSet()
        db.notebookDao().getAllItemsOnce()
            .filter { it.itemKind == NotebookItem.KIND_NOTE && it.itemId in ids }
            .forEach { db.notebookDao().deleteItemById(it.id) }
        val appContext = context.applicationContext
        doomed.forEach { TrashCleanup.purgeNote(appContext, db, it) }
    }
}
