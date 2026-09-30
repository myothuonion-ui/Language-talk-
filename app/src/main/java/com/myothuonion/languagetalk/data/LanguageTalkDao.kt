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
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM messages WHERE audioPath != '' ORDER BY createdAt DESC LIMIT 40")
    fun observeRecordings(): Flow<List<MessageEntity>>
    @Query("DELETE FROM review_items WHERE chatId = :chatId")
    suspend fun clearReviews(chatId: Long)

    @Query("SELECT * FROM learning_progress ORDER BY updatedAt DESC")
    fun observeProgress(): Flow<List<LearningProgressEntity>>

    @Query("SELECT * FROM learning_progress WHERE chatId = :chatId")
    suspend fun getProgress(chatId: Long): LearningProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: LearningProgressEntity)

    @Query("SELECT * FROM review_items ORDER BY dueAt ASC")
    fun observeReviews(): Flow<List<ReviewItemEntity>>

    @Query("SELECT * FROM review_items WHERE chatId = :chatId AND sentence = :sentence LIMIT 1")
    suspend fun getReview(chatId: Long, sentence: String): ReviewItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReview(review: ReviewItemEntity)

    @Query("SELECT * FROM chats")
    suspend fun allChats(): List<ChatEntity>
    @Query("SELECT * FROM messages")
    suspend fun allMessages(): List<MessageEntity>
    @Query("SELECT * FROM memories")
    suspend fun allMemories(): List<MemoryEntity>
    @Query("SELECT * FROM knowledge_sources")
    suspend fun allSources(): List<KnowledgeSourceEntity>
    @Query("SELECT * FROM learning_progress")
    suspend fun allProgress(): List<LearningProgressEntity>
    @Query("SELECT * FROM review_items")
    suspend fun allReviews(): List<ReviewItemEntity>
    @Query("UPDATE messages SET audioPath = '', audioMimeType = '' WHERE id = :messageId")
    suspend fun clearRecording(messageId: Long)
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND audioPath != ''")
    suspend fun recordingsForChat(chatId: Long): List<MessageEntity>

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
