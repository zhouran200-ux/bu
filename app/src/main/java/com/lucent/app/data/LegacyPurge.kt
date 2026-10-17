package com.lucent.app.data

import java.security.MessageDigest

object LegacyPurge {

    internal const val SAMPLE_TITLE = "用户预置文档"
    private const val SAMPLE_BODY_DIGEST =
        "1934f4d617f6f75ab2a1aa897c35bf2ae4647f74a643862a1ecceaa5cfe498ef"

    fun isSampleDocument(title: String, body: String): Boolean =
        title.trim() == SAMPLE_TITLE && digestOf(body) == SAMPLE_BODY_DIGEST

    suspend fun release(db: AppDatabase, note: Note) {
        db.notebookDao().unlinkEverywhere(NotebookItem.KIND_NOTE, note.id)
        TrashCleanup.purgeNote(db, note)
    }

    private fun digestOf(body: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}