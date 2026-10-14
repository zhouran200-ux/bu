package com.lucent.app.data

import java.security.MessageDigest

object LegacyPurge {

    private const val SAMPLE_TITLE = "用户预置文档"
    private const val SAMPLE_BODY_PREFIX_LENGTH = 16
    private const val SAMPLE_BODY_PREFIX_DIGEST =
        "08e0b7ad6264fd8d98ba45292ebd99d1a7b5d881e00021cdf68208996b494405"

    fun isSampleDocument(title: String, body: String): Boolean =
        title.trim() == SAMPLE_TITLE && digestOfPrefix(body) == SAMPLE_BODY_PREFIX_DIGEST

    suspend fun release(db: AppDatabase, note: Note) {
        db.notebookDao().getAllItemsOnce()
            .filter { it.itemKind == NotebookItem.KIND_NOTE && it.itemId == note.id }
            .forEach { db.notebookDao().deleteItemById(it.id) }
        TrashCleanup.purgeNote(db, note)
    }

    private fun digestOfPrefix(body: String): String? {
        if (body.length < SAMPLE_BODY_PREFIX_LENGTH) return null
        val prefix = body.take(SAMPLE_BODY_PREFIX_LENGTH)
        val bytes = MessageDigest.getInstance("SHA-256").digest(prefix.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}