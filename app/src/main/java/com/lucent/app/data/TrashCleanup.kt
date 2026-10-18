package com.lucent.app.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TrashCleanup {

    private const val LEGACY_ATTACHMENT_DIR = "attachments"
    private const val LEGACY_PREVIEW_DIR = "attachment-preview"
    private const val LEGACY_CLEANUP_PREFS = "lucent_legacy_cleanup"
    private const val LEGACY_PURGE_DONE = "legacy_attachment_purge_done"

    suspend fun cleanupLegacyArtifacts(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        discardLegacyAttachmentFilesOnce(appContext)

        val db = AppDatabase.getInstance(appContext)
        val notes = db.noteDao().getByTrimmedTitleOnce(LegacyPurge.SAMPLE_TITLE)

        notes.filter { LegacyPurge.isSampleDocument(it.title, it.body) }
            .forEach { LegacyPurge.release(db, it) }
    }

    private fun discardLegacyAttachmentFilesOnce(context: Context) {
        val prefs = context.getSharedPreferences(LEGACY_CLEANUP_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(LEGACY_PURGE_DONE, false)) return
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val removed = listOf(
            File(root, LEGACY_ATTACHMENT_DIR).deleteRecursively(),
            File(context.cacheDir, LEGACY_PREVIEW_DIR).deleteRecursively()
        )
        if (removed.all { it }) prefs.edit().putBoolean(LEGACY_PURGE_DONE, true).apply()
    }

    suspend fun purgeNote(db: AppDatabase, note: Note) {
        db.noteDao().delete(note)
        NovelSourceIndex.evict(note.id)
    }
}
