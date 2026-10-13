package com.myothuonion.languagetalk.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.Base64
import java.util.UUID
import com.myothuonion.languagetalk.model.BookShelfState
import com.myothuonion.languagetalk.model.LearningState

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
    val recordPractice: Boolean = true,
    val appearance: String = "SYSTEM"
)

@Serializable
data class BackupAudio(val messageId: Long, val data: String)

@Serializable
data class BackupReplayAudio(val clipId: Long, val data: String)

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
    val preferences: BackupPreferences = BackupPreferences(),
    val books: BookShelfState = BookShelfState(),
    val learning: LearningState = LearningState(),
    val annotations: List<ReaderAnnotation> = emptyList(),
    val pageExplanations: List<PageExplanation> = emptyList(),
    val audioTracks: List<BookAudioAsset> = emptyList(),
    val replaySessions: List<ReplaySession> = emptyList(),
    val replayClips: List<ReplayClip> = emptyList(),
    val replayAudio: List<BackupReplayAudio> = emptyList()
)

object BackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun decode(bytes: ByteArray): LearningBackup {
        require(bytes.size <= MAX_BYTES) { "Backup must be 32 MB or less" }
        val text = bytes.decodeToString()
        require(json.parseToJsonElement(text).jsonObject["format"]?.jsonPrimitive?.contentOrNull == "language-talk-backup") { "Not a Language Talk backup" }
        val backup = json.decodeFromString<LearningBackup>(text)
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
        require(backup.books.progress.size <= 2 && backup.books.progress.map { it.bookId }.distinct().size == backup.books.progress.size) { "Invalid book progress" }
        require(backup.books.progress.all { it.bookId in setOf("ttmik-beginner", "ttmik-intermediate") &&
            it.chapter in 1..40 && it.section in 0..12 && it.activity in 0..100 && it.mistakes.size <= 100 && it.completedSections.size <= 500 }) { "Invalid book lesson state" }
        require(backup.books.explanations.size <= 500 && backup.books.explanations.all { it.key.length <= 180 && it.text.length <= 60000 }) { "Book explanations are too large" }
        require(backup.preferences.appearance in setOf("SYSTEM", "LIGHT", "DARK"))
        require(backup.annotations.size <= 5000 && backup.annotations.map { it.id }.distinct().size == backup.annotations.size)
        backup.annotations.forEach {
            require(it.id.length <= 100 && it.bookId.matches(Regex("[a-zA-Z0-9-]{1,90}")) && it.page in 0..4999 &&
                it.quote.length in 1..1200 && it.context.length <= 4000 && it.note.length <= 4000 && it.rectangles.length <= 60000 &&
                it.kind in setOf("HIGHLIGHT", "UNDERLINE", "BOLD", "NOTE", "SAVE") &&
                it.startOffset >= -1 && it.endOffset >= -1 &&
                com.myothuonion.languagetalk.model.PageTeachingCodec.json.decodeFromString<List<com.myothuonion.languagetalk.model.PageRect>>(it.rectangles).all { rect -> rect.valid() })
        }
        require(backup.pageExplanations.size <= 1000 && backup.pageExplanations.map { it.key }.distinct().size == backup.pageExplanations.size)
        backup.pageExplanations.forEach {
            require(it.key.length <= 100 && it.page in 0..4999 && it.sourceBlocks.length <= 180000 && it.teaching.length <= 60000)
            com.myothuonion.languagetalk.model.PageTeachingCodec.decode(it.teaching, it.blocks(), requireComplete = false)
        }
        require(backup.audioTracks.size <= 1000 && backup.audioTracks.all { it.id.matches(Regex("[a-f0-9]{64}")) &&
            it.fileName == it.id + ".audio" && it.track in 1..9999 && it.mimeType.startsWith("audio/") })
        require(backup.audioTracks.all { it.pagesJson.length <= 30000 && BackupCodec.json.decodeFromString<List<Int>>(it.pagesJson).all { page -> page in 0..4999 } })
        require(backup.replaySessions.size <= 5000 && backup.replayClips.size <= 50000)
        val sessionIds = backup.replaySessions.map { it.id }.toSet()
        val clipIds = backup.replayClips.map { it.id }.toSet()
        require(sessionIds.size == backup.replaySessions.size && clipIds.size == backup.replayClips.size)
        require(backup.replaySessions.all { it.id.length in 1..300 && it.title.length <= 300 && it.sourceId.length <= 250 && it.page in -1..4999 })
        require(backup.replayClips.all { it.id > 0 && it.sessionId in sessionIds && it.speaker in setOf("USER", "AI") && it.text.length <= 6000 && it.durationMs in 0..3600000 })
        require(backup.replayAudio.map { it.clipId }.distinct().size == backup.replayAudio.size && backup.replayAudio.all { it.clipId in clipIds })
        backup.replayAudio.forEach { require(Base64.getDecoder().decode(it.data).size <= 4 * 1024 * 1024) { "အသံကြီးလွန်းပါတယ်။ Session ZIP နဲ့ ထုတ်ပါ။" } }
        return backup
    }
}

class BackupStore(private val db: AppDatabase, private val settings: SettingsStore, private val recordings: File,
    private val books: BookStore? = null, private val learning: LearningStore? = null) {
    private val replayDirectory = File(recordings.parentFile, "replay-audio")
    suspend fun export(): ByteArray {
        val dao = db.dao()
        val preferences = settings.settings.first()
        val bookState = books?.export() ?: BookShelfState()
        val learningState = learning?.export() ?: LearningState()
        val backup = db.withTransaction {
            val messages = dao.allMessages()
            val audio = messages.filter { it.audioPath.isNotBlank() && it.audioPath == File(it.audioPath).name }.mapNotNull { message ->
                val file = File(recordings, message.audioPath)
                if (file.isFile) BackupAudio(message.id, Base64.getEncoder().encodeToString(file.readBytes())) else null
            }
            val replayClips = dao.allReplayClips()
            val replayAudio = replayClips.mapNotNull { clip ->
                val file = File(replayDirectory, clip.audioName)
                if (clip.audioName.matches(Regex("[a-zA-Z0-9._-]{1,100}")) && file.isFile) {
                    require(file.length() <= 4 * 1024 * 1024) { "ဒီအသံက 4 MB ကျော်ပါတယ်။ Replays မှ Session ZIP ထုတ်ပါ။" }
                    BackupReplayAudio(clip.id, Base64.getEncoder().encodeToString(file.readBytes()))
                } else null
            }
            LearningBackup(annotations = dao.allAnnotations(), pageExplanations = dao.allPageExplanations(), audioTracks = dao.allBookAudio(), chats = dao.allChats(), messages = messages, memories = dao.allMemories(),
                sources = dao.allSources().map { it.copy(uri = "") }, progress = dao.allProgress(), reviews = dao.allReviews(), audio = audio, books = bookState,
                replaySessions = dao.allReplaySessions(), replayClips = replayClips, replayAudio = replayAudio,
                learning = learningState.copy(attempts = learningState.attempts.map { it.copy(audioName = "") }),
                preferences = BackupPreferences(preferences.displayName, preferences.learnerProfile, preferences.globalBehavior,
                    preferences.defaultVoiceName, preferences.defaultVoiceStyle, preferences.defaultPace, preferences.defaultSilenceMs,
                    preferences.defaultSpeakCorrections, preferences.autoLearningMemory, preferences.recordPractice, preferences.appearance))
        }
        return BackupCodec.json.encodeToString(backup).encodeToByteArray().also {
            require(it.size <= BackupCodec.MAX_BYTES) { "Backup exceeds 32 MB. Remove old recordings before exporting." }
        }
    }

    suspend fun restore(bytes: ByteArray): Int {
        val backup = BackupCodec.decode(bytes)
        books?.validateRestore(backup.books)
        learning?.validateRestore(backup.learning)
        val stagedFiles = mutableListOf<File>()
        recordings.mkdirs()
        replayDirectory.mkdirs()
        try {
            val audio = backup.audio.associate { item ->
                val message = backup.messages.first { it.id == item.messageId }
                val extension = if (message.audioMimeType.contains("wav")) "wav" else "m4a"
                val file = File(recordings, UUID.randomUUID().toString() + "." + extension)
                stagedFiles += file
                file.writeBytes(Base64.getDecoder().decode(item.data))
                item.messageId to file.name
            }
            val replayAudio = backup.replayAudio.associate { item ->
                val clip = backup.replayClips.first { it.id == item.clipId }
                val suffix = if (clip.mimeType.contains("wav")) ".wav" else ".m4a"
                val file = File(replayDirectory, UUID.randomUUID().toString() + suffix)
                stagedFiles += file; file.writeBytes(Base64.getDecoder().decode(item.data))
                item.clipId to file.name
            }
            db.withTransaction {
                val dao = db.dao()
                backup.annotations.forEach { dao.saveAnnotation(it) }
                backup.pageExplanations.forEach { dao.savePageExplanation(it) }
                backup.audioTracks.forEach { dao.saveBookAudio(it) }
                val chatMap = backup.chats.associate { it.id to dao.insertChat(it.copy(id = 0, silenceMs = it.silenceMs.coerceIn(800, 4000))) }
                backup.messages.forEach { dao.insertMessage(it.copy(id = 0, chatId = chatMap.getValue(it.chatId), audioPath = audio[it.id].orEmpty())) }
                backup.memories.forEach { dao.insertMemory(it.copy(id = 0, scopeChatId = it.scopeChatId?.let(chatMap::getValue))) }
                backup.sources.forEach { dao.insertKnowledgeSource(it.copy(id = 0, uri = "")) }
                backup.progress.forEach { dao.saveProgress(it.copy(chatId = chatMap.getValue(it.chatId), completed = it.completed.coerceIn(0, 5))) }
                backup.reviews.forEach { dao.saveReview(it.copy(id = 0, chatId = chatMap.getValue(it.chatId))) }
                val replayMap = backup.replaySessions.associate { session ->
                    val id = "restored-" + UUID.randomUUID()
                    val source = if (session.kind == "CONVERSATION") session.sourceId.removePrefix("chat-").toLongOrNull()?.let { old ->
                        chatMap[old]?.let { "chat-$it" }
                    } ?: session.sourceId else session.sourceId
                    dao.saveReplaySession(session.copy(id = id, sourceId = source)); session.id to id
                }
                backup.replayClips.forEach { clip -> dao.insertReplayClip(clip.copy(id = 0, sessionId = replayMap.getValue(clip.sessionId),
                    audioName = replayAudio[clip.id].orEmpty(), durationMs = if (clip.id in replayAudio) clip.durationMs else 0)) }
            }
        } catch (failure: Throwable) {
            stagedFiles.forEach { it.delete() }
            throw failure
        }
        val prefs = backup.preferences
        settings.update { it.copy(displayName = prefs.displayName.ifBlank { it.displayName }, learnerProfile = prefs.profile,
            globalBehavior = prefs.globalBehavior, defaultVoiceName = prefs.voiceName, defaultVoiceStyle = prefs.voiceStyle,
            defaultPace = prefs.pace, defaultSilenceMs = prefs.silenceMs.coerceIn(800, 4000), defaultSpeakCorrections = prefs.speakCorrections,
            autoLearningMemory = prefs.autoLearningMemory, recordPractice = prefs.recordPractice, appearance = prefs.appearance, darkTheme = prefs.appearance == "DARK") }
        if (backup.books.progress.isNotEmpty() || backup.books.explanations.isNotEmpty()) books?.restore(backup.books)
        if (backup.learning.units.isNotEmpty() || backup.learning.books.isNotEmpty() || backup.learning.cards.isNotEmpty() ||
            backup.learning.placementDone || backup.learning.studySeconds.isNotEmpty()) learning?.restore(backup.learning)
        return backup.chats.size
    }
}
