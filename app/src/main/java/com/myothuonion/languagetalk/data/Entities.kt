package com.myothuonion.languagetalk.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val language: String,
    val topic: String,
    val level: String,
    val tutorRole: String,
    val correctionMode: String,
    val customPrompt: String,
    val voiceName: String,
    val voiceStyle: String,
    val brainMode: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val summary: String = "",
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val practiceMode: String = "GUIDED",
    val speakingPace: String = "SLOW",
    val silenceMs: Int = 2000,
    val speakCorrections: Boolean = true
)

@Serializable
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ChatEntity::class,
        parentColumns = ["id"],
        childColumns = ["chatId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("chatId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val role: String,
    val content: String,
    val translation: String = "",
    val correction: String = "",
    val explanation: String = "",
    val audioPath: String = "",
    val audioMimeType: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "memories", indices = [Index("scopeChatId")])
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "Personal",
    val scopeChatId: Long? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "knowledge_sources")
data class KnowledgeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mimeType: String,
    val uri: String = "",
    val summary: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)


@Serializable
@Entity(tableName = "learning_progress", foreignKeys = [ForeignKey(
    entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE
)])
data class LearningProgressEntity(
    @PrimaryKey val chatId: Long,
    val goal: String,
    val stage: String = "INTRO",
    val targetSentence: String = "",
    val completed: Int = 0,
    val summary: String = "",
    val lastCorrection: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun state() = com.myothuonion.languagetalk.model.PracticeState(goal, stage, targetSentence, completed, summary, lastCorrection)
}

@Serializable
@Entity(tableName = "review_items", foreignKeys = [ForeignKey(
    entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["chatId", "sentence"], unique = true)])
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val sentence: String,
    val correction: String = "",
    val dueAt: Long = System.currentTimeMillis(),
    val successes: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
