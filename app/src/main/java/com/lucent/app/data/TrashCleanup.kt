package com.lucent.app.data

import android.content.Context

object TrashCleanup {

    const val RETENTION_DAYS = 30
    private const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60 * 60 * 1000

    suspend fun purgeExpired(context: Context) {
        val appContext = context.applicationContext
        val db = AppDatabase.getInstance(appContext)
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        val notes = db.noteDao().getAllOnce()

        notes.filter { LegacyPurge.isSampleDocument(it.title, it.body) }
            .forEach { LegacyPurge.release(appContext, db, it) }

        notes.forEach { note ->
            val trashedAt = note.trashedAt ?: return@forEach
            if (trashedAt >= cutoff) return@forEach
            purgeNote(appContext, db, note)
        }

    }

    suspend fun purgeNote(context: Context, db: AppDatabase, note: Note) {
        val appContext = context.applicationContext
        Attachments.parse(note.attachments).forEach { att ->
            if (AttachmentStore.looksLikeId(att.data)) AttachmentStore.delete(appContext, att.data)
        }
        db.noteDao().delete(note)
    }
}
