package com.lucent.app.data

import android.content.Context
import java.io.File

object TrashCleanup {

    private const val LEGACY_ATTACHMENT_DIR = "attachments"
    private const val LEGACY_PREVIEW_DIR = "attachment-preview"

    suspend fun purgeExpired(context: Context) {
        val appContext = context.applicationContext
        discardLegacyAttachmentFiles(appContext)

        val db = AppDatabase.getInstance(appContext)
        val notes = db.noteDao().getAllOnce()

        notes.filter { LegacyPurge.isSampleDocument(it.title, it.body) }
            .forEach { LegacyPurge.release(db, it) }
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
