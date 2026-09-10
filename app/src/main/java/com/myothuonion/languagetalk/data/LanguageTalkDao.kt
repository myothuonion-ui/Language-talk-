package com.myothuonion.languagetalk.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageTalkDao {
    @Query("SELECT * FROM chats WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChat(id: Long): ChatEntity?

    @Insert
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET updatedAt = :time WHERE id = :chatId")
    suspend fun touchChat(chatId: Long, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: Long)

    @Query("DELETE FROM memories WHERE scopeChatId = :chatId")
    suspend fun deleteMemoriesForChat(chatId: Long)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recentMessages(chatId: Long, limit: Int = 20): List<MessageEntity>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    @Query("SELECT * FROM memories WHERE scopeChatId IS NULL ORDER BY updatedAt DESC")
    fun observeGlobalMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE scopeChatId = :chatId ORDER BY updatedAt DESC")
    fun observeChatMemories(chatId: Long): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE enabled = 1 AND (scopeChatId IS NULL OR scopeChatId = :chatId) ORDER BY scopeChatId ASC, updatedAt DESC")
    suspend fun enabledMemories(chatId: Long): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("SELECT * FROM knowledge_sources ORDER BY createdAt DESC")
    fun observeKnowledgeSources(): Flow<List<KnowledgeSourceEntity>>

    @Query("SELECT * FROM knowledge_sources WHERE enabled = 1 ORDER BY createdAt DESC")
    suspend fun enabledKnowledgeSources(): List<KnowledgeSourceEntity>

    @Insert
    suspend fun insertKnowledgeSource(source: KnowledgeSourceEntity): Long

    @Update
    suspend fun updateKnowledgeSource(source: KnowledgeSourceEntity)

    @Delete
    suspend fun deleteKnowledgeSource(source: KnowledgeSourceEntity)
}
