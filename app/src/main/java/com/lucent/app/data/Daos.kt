package com.lucent.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE archived = 0 AND trashedAt IS NULL AND isDraft = 0 AND hidden = 0 ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Note?

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Note>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes")
    suspend fun clearAll()
}

@Dao
interface NoteEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: NoteEmbedding)

    @Query("SELECT * FROM note_embeddings WHERE model = :model")
    suspend fun getForModel(model: String): List<NoteEmbedding>

    @Query("SELECT * FROM note_embeddings WHERE noteId = :noteId")
    suspend fun getForNote(noteId: Long): List<NoteEmbedding>

    @Query("DELETE FROM note_embeddings WHERE noteId = :noteId AND model = :model")
    suspend fun delete(noteId: Long, model: String)

    @Query("DELETE FROM note_embeddings WHERE model = :model")
    suspend fun deleteAllForModel(model: String)

    @Query("DELETE FROM note_embeddings")
    suspend fun clearAll()
}

@Dao
interface NoteVersionDao {
    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC")
    fun getForNote(noteId: Long): Flow<List<NoteVersion>>

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC")
    suspend fun getForNoteOnce(noteId: Long): List<NoteVersion>

    @Query("SELECT * FROM note_versions ORDER BY savedAt DESC")
    suspend fun getAllOnce(): List<NoteVersion>

    @Query("SELECT COUNT(*) FROM note_versions WHERE noteId = :noteId")
    suspend fun countForNote(noteId: Long): Int

    @Insert
    suspend fun insert(version: NoteVersion): Long

    @Delete
    suspend fun delete(version: NoteVersion)

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query("DELETE FROM note_versions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM note_versions WHERE noteId = :noteId AND id NOT IN (" +
            "SELECT id FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC LIMIT :keep)"
    )
    suspend fun trimTo(noteId: Long, keep: Int)

    @Query("DELETE FROM note_versions WHERE noteId NOT IN (SELECT id FROM notes)")
    suspend fun pruneOrphaned()

    @Query("DELETE FROM note_versions")
    suspend fun clearAll()
}

data class ConversationContent(
    val conversationId: Long,
    val content: String?
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getForConversation(conversationId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getForConversationOnce(conversationId: Long): List<ChatMessage>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun countInConversation(conversationId: Long): Int

    @Query("SELECT conversationId AS conversationId, GROUP_CONCAT(content, ' ') AS content FROM chat_messages GROUP BY conversationId")
    suspend fun conversationContents(): List<ConversationContent>

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface ChatConversationDao {
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ChatConversation>>

    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<ChatConversation>

    @Query("SELECT * FROM chat_conversations WHERE id = :id")
    suspend fun getById(id: Long): ChatConversation?

    @Insert
    suspend fun insert(conversation: ChatConversation): Long

    @Update
    suspend fun update(conversation: ChatConversation)

    @Delete
    suspend fun delete(conversation: ChatConversation)

    @Query("DELETE FROM chat_conversations")
    suspend fun clearAll()
}

data class NotebookCount(val notebookId: Long, val count: Int)

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<Notebook>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Notebook?

    @Query("SELECT notebookId AS notebookId, COUNT(*) AS count FROM notebook_items GROUP BY notebookId")
    fun itemCounts(): Flow<List<NotebookCount>>

    @Query("SELECT * FROM notebook_items WHERE notebookId = :notebookId ORDER BY addedAt DESC")
    fun getItems(notebookId: Long): Flow<List<NotebookItem>>

    @Query("SELECT * FROM notebook_items WHERE notebookId = :notebookId ORDER BY addedAt DESC")
    suspend fun getItemsOnce(notebookId: Long): List<NotebookItem>

    @Query("SELECT * FROM notebook_items WHERE itemKind = :kind")
    suspend fun getItemsByKindOnce(kind: String): List<NotebookItem>

    @Query("SELECT * FROM notebook_items")
    suspend fun getAllItemsOnce(): List<NotebookItem>

    @Query("SELECT COUNT(*) FROM notebook_items WHERE notebookId = :notebookId AND itemKind = :kind AND itemId = :itemId")
    suspend fun membershipExistsOnce(notebookId: Long, kind: String, itemId: Long): Int

    @Insert
    suspend fun insert(notebook: Notebook): Long

    @Update
    suspend fun update(notebook: Notebook)

    @Insert
    suspend fun insertItem(item: NotebookItem): Long

    @Query("DELETE FROM notebook_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("DELETE FROM notebook_items WHERE notebookId = :notebookId AND itemKind = :itemKind AND itemId = :itemId")
    suspend fun removeItem(notebookId: Long, itemKind: String, itemId: Long)

    @Query("DELETE FROM notebook_items WHERE notebookId = :notebookId")
    suspend fun deleteItemsForNotebook(notebookId: Long)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notebooks")
    suspend fun clearAll()

    @Query("DELETE FROM notebook_items")
    suspend fun clearAllItems()
}

suspend fun NotebookDao.pruneOrphans(noteDao: NoteDao) {
    val noteMembers = getItemsByKindOnce(NotebookItem.KIND_NOTE)
    if (noteMembers.isNotEmpty()) {
        val alive = noteDao.getByIds(noteMembers.map { it.itemId }.toSet().toList()).map { it.id }.toHashSet()
        noteMembers.filter { it.itemId !in alive }.forEach { deleteItemById(it.id) }
    }
}
