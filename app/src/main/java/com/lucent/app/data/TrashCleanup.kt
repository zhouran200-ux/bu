package com.lucent.app.data

import android.content.Context
import java.io.File

object TrashCleanup {

    const val RETENTION_DAYS = 30
    private const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60 * 60 * 1000

    private const val LEGACY_ATTACHMENT_DIR = "attachments"
    private const val LEGACY_PREVIEW_DIR = "attachment-preview"

    suspend fun purgeExpired(context: Context) {
        val appContext = context.applicationContext
        discardLegacyAttachmentFiles(appContext)

        val db = AppDatabase.getInstance(appContext)
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        val notes = db.noteDao().getAllOnce()

        notes.filter { LegacyPurge.isSampleDocument(it.title, it.body) }
            .forEach { LegacyPurge.release(db, it) }

        notes.forEach { note ->
            val trashedAt = note.trashedAt ?: return@forEach
            if (trashedAt >= cutoff) return@forEach
            purgeNote(db, note)
        }

    }

    private fun discardLegacyAttachmentFiles(context: Context) {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        File(root, LEGACY_ATTACHMENT_DIR).deleteRecursively()
        File(context.cacheDir, LEGACY_PREVIEW_DIR).deleteRecursively()
    }

    suspend fun purgeNote(db: AppDatabase, note: Note) {
        db.noteDao().delete(note)
    }
}
