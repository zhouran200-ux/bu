package com.lucent.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["archived"]),
        Index(value = ["trashedAt"])
    ]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val tags: String = "",
    val attachments: String = "[]",
    val archived: Boolean = false,
    val archivedAt: Long? = null,
    val pinned: Boolean = false,
    val color: String = "",
    val trashedAt: Long? = null,
    val manualOrder: Int = 0,
    val isDraft: Boolean = false,
    val draftSavedAt: Long? = null,
    val hidden: Boolean = false,
    val bodySpans: String = "",
    val formatOverride: String? = null
)

@Entity(tableName = "note_embeddings", primaryKeys = ["noteId", "model"])
data class NoteEmbedding(
    val noteId: Long,
    val model: String,
    val dim: Int,
    val vec: ByteArray,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoteEmbedding) return false
        return noteId == other.noteId && model == other.model && dim == other.dim &&
            vec.contentEquals(other.vec) && updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = noteId.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + dim
        result = 31 * result + vec.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

@Entity(
    tableName = "note_versions",
    indices = [Index(value = ["noteId"])]
)
data class NoteVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val body: String,
    val tags: String = "",
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notebooks",
    indices = [Index(value = ["updatedAt"])]
)
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val color: String = "",
    val manualOrder: Int = 0,
    val trashedAt: Long? = null,
    val pinned: Boolean = false
)

@Entity(
    tableName = "notebook_items",
    indices = [
        Index(value = ["notebookId"]),
        Index(value = ["itemKind", "itemId"])
    ]
)
data class NotebookItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val itemKind: String,
    val itemId: Long,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_NOTE = "NOTE"
    }
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachmentMime: String? = null,
    val attachmentData: String? = null,
    val attachmentName: String? = null,
    val attachmentList: String? = null,
    val conversationId: Long = 1,
    val tokens: Int = 0,
    val replyToId: Long = 0,
    val reasoningBlocks: String? = null,
    val reasoningText: String? = null,
    val quotedRole: String? = null,
    val quotedText: String? = null
)

@Entity(tableName = "chat_conversations")
data class ChatConversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
