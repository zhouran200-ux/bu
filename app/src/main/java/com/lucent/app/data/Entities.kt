package com.lucent.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["archived"]),
        Index(value = ["trashedAt"]),
        Index(value = ["title"])
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

@Entity(
    tableName = "notebooks",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["manualOrder", "updatedAt"])
    ]
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
        Index(value = ["itemKind", "itemId"]),
        Index(value = ["notebookId", "addedAt"])
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

@Entity(tableName = "chat_messages", indices = [Index(value = ["conversationId", "timestamp"])])
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
    val cachedTokens: Int = 0,
    val answerSeconds: Int = 0,
    val replyToId: Long = 0,
    val reasoningBlocks: String? = null,
    val reasoningText: String? = null,
    val quotedRole: String? = null,
    val quotedText: String? = null,
    @ColumnInfo(defaultValue = "NULL") val firstTokenMillis: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val generationMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val cachedTokensKnown: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val sourceNoteId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val sourceRevision: String? = null
)

object ChatScope {
    const val NOTEBOOK_CONVERSATION_FLOOR = 100000L

    fun forNotebook(notebookId: Long): Long = NOTEBOOK_CONVERSATION_FLOOR + notebookId
}
