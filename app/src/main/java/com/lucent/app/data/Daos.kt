package com.lucent.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class NoteTitleCandidate(val id: Long, val title: String)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE title = :title")
    suspend fun getByTitleOnce(title: String): List<Note>

    @Query("SELECT id, title FROM notes WHERE title != :title")
    suspend fun getNonExactTitlesOnce(title: String): List<NoteTitleCandidate>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getByIdOnce(id: Long): Note?

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Note>

    @Insert
    suspend fun insert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getForConversationOnce(conversationId: Long): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getById(id: Long): ChatMessage?

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @androidx.room.Upsert
    suspend fun upsert(message: ChatMessage)

    @Query("UPDATE chat_messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: Long)
}

data class NotebookCount(val notebookId: Long, val count: Int)

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY manualOrder ASC, updatedAt DESC")
    fun getAll(): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks ORDER BY manualOrder ASC, updatedAt DESC")
    suspend fun getAllOnce(): List<Notebook>

    @Query("SELECT EXISTS(SELECT 1 FROM notebooks WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    @Query("SELECT notebookId AS notebookId, COUNT(*) AS count FROM notebook_items GROUP BY notebookId")
    fun itemCounts(): Flow<List<NotebookCount>>

    @Query("SELECT * FROM notebook_items WHERE notebookId = :notebookId ORDER BY addedAt DESC")
    suspend fun getItemsOnce(notebookId: Long): List<NotebookItem>

    @Query("DELETE FROM notebook_items WHERE itemKind = :kind AND itemId = :itemId")
    suspend fun unlinkEverywhere(kind: String, itemId: Long)

    @Query("SELECT DISTINCT a.itemId FROM notebook_items a WHERE a.notebookId = :notebookId AND a.itemKind = :kind AND NOT EXISTS (SELECT 1 FROM notebook_items b WHERE b.itemId = a.itemId AND b.itemKind = :kind AND b.notebookId != :notebookId)")
    suspend fun exclusiveItemIds(notebookId: Long, kind: String): List<Long>

    @Insert
    suspend fun insert(notebook: Notebook): Long

    @Update
    suspend fun update(notebook: Notebook)

    @Update
    suspend fun updateAll(notebooks: List<Notebook>)

    @Insert
    suspend fun insertItem(item: NotebookItem): Long

    @Query("DELETE FROM notebook_items WHERE notebookId = :notebookId AND itemKind = :itemKind AND itemId = :itemId")
    suspend fun removeItem(notebookId: Long, itemKind: String, itemId: Long)

    @Query("DELETE FROM notebook_items WHERE itemKind = :itemKind AND itemId = :itemId AND notebookId != :keepNotebookId")
    suspend fun unlinkItemExcept(itemKind: String, itemId: Long, keepNotebookId: Long)

    @Query("DELETE FROM notebook_items WHERE notebookId = :notebookId")
    suspend fun deleteItemsForNotebook(notebookId: Long)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: Long)

}
