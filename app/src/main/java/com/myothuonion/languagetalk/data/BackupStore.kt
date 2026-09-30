package com.myothuonion.languagetalk.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64
import java.util.UUID

@Serializable
data class BackupPreferences(
    val displayName: String = "",
    val profile: String = "",
    val globalBehavior: String = "",
    val voiceName: String = "Kore",
    val voiceStyle: String = "",
    val pace: String = "SLOW",
    val silenceMs: Int = 2000,
    val speakCorrections: Boolean = true,
    val autoLearningMemory: Boolean = true,
    val recordPractice: Boolean = true
)

@Serializable
data class BackupAudio(val messageId: Long, val data: String)

@Serializable
data class LearningBackup(
    val format: String = "language-talk-backup",
    val version: Int = 1,
    val chats: List<ChatEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val sources: List<KnowledgeSourceEntity> = emptyList(),
    val progress: List<LearningProgressEntity> = emptyList(),
    val reviews: List<ReviewItemEntity> = emptyList(),
    val audio: List<BackupAudio> = emptyList(),
    val preferences: BackupPreferences = BackupPreferences()
)

object BackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(bytes: ByteArray): LearningBackup {
        require(bytes.size <= MAX_BYTES) { "Backup must be 32 MB or less" }
        val backup = json.decodeFromString<LearningBackup>(bytes.decodeToString())
        require(backup.format == "language-talk-backup" && backup.version == 1) { "Unsupported backup format" }
        require(backup.chats.size <= 2000 && backup.messages.size <= 20000 && backup.memories.size <= 3000) { "Backup contains too many entries" }
        val ids = backup.chats.map { it.id }.toSet()
        require(ids.size == backup.chats.size && ids.all { it > 0 }) { "Duplicate or invalid chat IDs" }
        val messages = backup.messages.map { it.id }.toSet()
        require(messages.size == backup.messages.size && messages.all { it > 0 }) { "Duplicate or invalid message IDs" }
        require(backup.messages.all { it.chatId in ids } && backup.progress.all { it.chatId in ids } &&
            backup.reviews.all { it.chatId in ids } && backup.memories.all { it.scopeChatId == null || it.scopeChatId in ids }) { "Backup has missing chat references" }
        require(backup.audio.map { it.messageId }.distinct().size == backup.audio.size && backup.audio.all { it.messageId in messages }) { "Invalid recording references" }
        backup.audio.forEach { require(Base64.getDecoder().decode(it.data).size <= 4 * 1024 * 1024) { "A recording is too large" } }
        return backup
    }
}

class BackupStore(private val db: AppDatabase, private val settings: SettingsStore, private val recordings: File) {
    suspend fun export(): ByteArray {
        val dao = db.dao()
        val preferences = settings.settings.first()
        val backup = db.withTransaction {
            val messages = dao.allMessages()
            val audio = messages.filter { it.audioPath.isNotBlank() && it.audioPath == File(it.audioPath).name }.mapNotNull { message ->
                val file = File(recordings, message.audioPath)
                if (file.isFile) BackupAudio(message.id, Base64.getEncoder().encodeToString(file.readBytes())) else null
            }
            LearningBackup(chats = dao.allChats(), messages = messages, memories = dao.allMemories(),
                sources = dao.allSources().map { it.copy(uri = "") }, progress = dao.allProgress(), reviews = dao.allReviews(), audio = audio,
                preferences = BackupPreferences(preferences.displayName, preferences.learnerProfile, preferences.globalBehavior,
                    preferences.defaultVoiceName, preferences.defaultVoiceStyle, preferences.defaultPace, preferences.defaultSilenceMs,
                    preferences.defaultSpeakCorrections, preferences.autoLearningMemory, preferences.recordPractice))
        }
        return BackupCodec.json.encodeToString(backup).encodeToByteArray().also {
            require(it.size <= BackupCodec.MAX_BYTES) { "Backup exceeds 32 MB. Remove old recordings before exporting." }
        }
    }

    suspend fun restore(bytes: ByteArray): Int {
        val backup = BackupCodec.decode(bytes)
        val stagedFiles = mutableListOf<File>()
        recordings.mkdirs()
        try {
            val audio = backup.audio.associate { item ->
                val message = backup.messages.first { it.id == item.messageId }
                val extension = if (message.audioMimeType.contains("wav")) "wav" else "m4a"
                val file = File(recordings, UUID.randomUUID().toString() + "." + extension)
                stagedFiles += file
                file.writeBytes(Base64.getDecoder().decode(item.data))
                item.messageId to file.name
            }
            db.withTransaction {
                val dao = db.dao()
                val chatMap = backup.chats.associate { it.id to dao.insertChat(it.copy(id = 0, silenceMs = it.silenceMs.coerceIn(800, 4000))) }
                backup.messages.forEach { dao.insertMessage(it.copy(id = 0, chatId = chatMap.getValue(it.chatId), audioPath = audio[it.id].orEmpty())) }
                backup.memories.forEach { dao.insertMemory(it.copy(id = 0, scopeChatId = it.scopeChatId?.let(chatMap::getValue))) }
                backup.sources.forEach { dao.insertKnowledgeSource(it.copy(id = 0, uri = "")) }
                backup.progress.forEach { dao.saveProgress(it.copy(chatId = chatMap.getValue(it.chatId), completed = it.completed.coerceIn(0, 5))) }
                backup.reviews.forEach { dao.saveReview(it.copy(id = 0, chatId = chatMap.getValue(it.chatId))) }
            }
        } catch (failure: Throwable) {
            stagedFiles.forEach { it.delete() }
            throw failure
        }
        val prefs = backup.preferences
        settings.update { it.copy(displayName = prefs.displayName.ifBlank { it.displayName }, learnerProfile = prefs.profile,
            globalBehavior = prefs.globalBehavior, defaultVoiceName = prefs.voiceName, defaultVoiceStyle = prefs.voiceStyle,
            defaultPace = prefs.pace, defaultSilenceMs = prefs.silenceMs.coerceIn(800, 4000), defaultSpeakCorrections = prefs.speakCorrections,
            autoLearningMemory = prefs.autoLearningMemory, recordPractice = prefs.recordPractice) }
        return backup.chats.size
    }
}
