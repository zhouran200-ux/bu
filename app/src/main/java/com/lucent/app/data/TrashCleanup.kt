package com.lucent.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object TrashCleanup {

    private const val LEGACY_ATTACHMENT_DIR = "attachments"
    private const val LEGACY_PREVIEW_DIR = "attachment-preview"
    private const val LEGACY_CLEANUP_PREFS = "lucent_legacy_cleanup"
    private const val LEGACY_PURGE_DONE = "legacy_attachment_purge_done"
    internal const val LEGACY_SAMPLE_PURGE_DONE = "legacy_sample_document_purge_done"
    private val sampleCleanupMutex = Mutex()

    suspend fun cleanupLegacyArtifacts(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        discardLegacyAttachmentFilesOnce(appContext)

        val prefs = appContext.getSharedPreferences(LEGACY_CLEANUP_PREFS, Context.MODE_PRIVATE)
        discardLegacySampleDocumentsOnce(prefs) { AppDatabase.getInstance(appContext) }
    }

    internal suspend fun discardLegacySampleDocumentsOnce(
        prefs: SharedPreferences,
        database: () -> AppDatabase
    ) = sampleCleanupMutex.withLock {
        if (prefs.getBoolean(LEGACY_SAMPLE_PURGE_DONE, false)) return@withLock
        val db = database()
        db.withTransaction {
            val dao = db.noteDao()
            dao.getByTitleOnce(LegacyPurge.SAMPLE_TITLE).forEach { note ->
                if (LegacyPurge.isSampleDocument(note.title, note.body)) LegacyPurge.release(db, note)
            }
            dao.getNonExactTitlesOnce(LegacyPurge.SAMPLE_TITLE).forEach { candidate ->
                if (candidate.title.trim() == LegacyPurge.SAMPLE_TITLE) {
                    val note = dao.getByIdOnce(candidate.id)
                    if (note != null && LegacyPurge.isSampleDocument(note.title, note.body)) {
                        LegacyPurge.release(db, note)
                    }
                }
            }
        }
        check(prefs.edit().putBoolean(LEGACY_SAMPLE_PURGE_DONE, true).commit()) {
            "Unable to persist legacy sample cleanup marker"
        }
    }

    private fun discardLegacyAttachmentFilesOnce(context: Context) {
        val prefs = context.getSharedPreferences(LEGACY_CLEANUP_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(LEGACY_PURGE_DONE, false)) return
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val removed = listOf(
            File(root, LEGACY_ATTACHMENT_DIR).deleteRecursively(),
            File(context.cacheDir, LEGACY_PREVIEW_DIR).deleteRecursively()
        )
        if (removed.all { it }) {
            check(prefs.edit().putBoolean(LEGACY_PURGE_DONE, true).commit()) {
                "Unable to persist legacy attachment cleanup marker"
            }
        }
    }

    suspend fun purgeNote(db: AppDatabase, note: Note) {
        db.noteDao().delete(note)
        NovelSourceIndex.evict(note.id)
    }
}
