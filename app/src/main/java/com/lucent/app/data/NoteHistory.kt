package com.lucent.app.data

object NoteHistory {

    const val MAX_VERSIONS_PER_NOTE = 35

    @Volatile
    var enabled: Boolean = true

    suspend fun recordIfChanged(
        db: AppDatabase,
        existing: Note,
        newTitle: String,
        newBody: String,
        newTags: String,
        savedAt: Long = existing.updatedAt
    ) {
        if (!enabled) return

        val unchanged = existing.title == newTitle &&
            existing.body == newBody &&
            existing.tags == newTags
        if (unchanged) return

        db.noteVersionDao().insert(
            NoteVersion(
                noteId = existing.id,
                title = existing.title,
                body = existing.body,
                tags = existing.tags,
                savedAt = savedAt
            )
        )
        db.noteVersionDao().trimTo(existing.id, MAX_VERSIONS_PER_NOTE)
    }

    fun applyTo(note: Note, version: NoteVersion): Note = note.copy(
        title = version.title,
        body = version.body,
        tags = version.tags,
        updatedAt = System.currentTimeMillis()
    )

    suspend fun deleteAllFor(db: AppDatabase, noteId: Long) {
        db.noteVersionDao().deleteForNote(noteId)
    }
}
