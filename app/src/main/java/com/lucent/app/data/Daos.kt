package com.lucent.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
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
interface NoteVersionDao {
    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query("DELETE FROM note_versions WHERE noteId NOT IN (SELECT id FROM notes)")
    suspend fun pruneOrphaned()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getForConversationOnce(conversationId: Long): List<ChatMessage>

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
    suspend fun getItemsOnce(notebookId: Long): List<NotebookItem>

    @Query("SELECT * FROM notebook_items")
    suspend fun getAllItemsOnce(): List<NotebookItem>

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

    @Query("DELETE FROM notebook_items WHERE itemKind = :itemKind AND itemId = :itemId AND notebookId != :keepNotebookId")
    suspend fun unlinkItemExcept(itemKind: String, itemId: Long, keepNotebookId: Long)

    @Query("DELETE FROM notebook_items WHERE notebookId = :notebookId")
    suspend fun deleteItemsForNotebook(notebookId: Long)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notebooks")
    suspend fun clearAll()

}
