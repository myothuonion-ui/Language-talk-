package com.myothuonion.languagetalk.data

import com.myothuonion.languagetalk.model.AppLanguage
import com.myothuonion.languagetalk.model.BrainMode
import com.myothuonion.languagetalk.model.GeminiRouteStatus
import com.myothuonion.languagetalk.model.KoreanNameResult
import com.myothuonion.languagetalk.model.MessageRole
import com.myothuonion.languagetalk.model.LiveSessionConfig
import com.myothuonion.languagetalk.model.TranslationResult
import com.myothuonion.languagetalk.model.TutorConfig
import com.myothuonion.languagetalk.model.TutorReply
import com.myothuonion.languagetalk.model.knownKoreanNameResult
import com.myothuonion.languagetalk.network.AudioPayload
import com.myothuonion.languagetalk.network.AiApiException
import com.myothuonion.languagetalk.network.GeminiClient
import com.myothuonion.languagetalk.network.HandsFreeTurn
import com.myothuonion.languagetalk.network.NvidiaClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import com.myothuonion.languagetalk.model.PracticeMode
import com.myothuonion.languagetalk.model.PracticeState
import com.myothuonion.languagetalk.model.PracticeEngine
import com.myothuonion.languagetalk.model.PracticeAssessment
import com.myothuonion.languagetalk.model.ContextSelector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.UUID

class TutorRepository(
    private val dao: LanguageTalkDao,
    private val database: AppDatabase,
    private val recordingDirectory: File,
    private val settingsStore: SettingsStore,
    private val secrets: SecretStore,
    private val gemini: GeminiClient,
    private val nvidia: NvidiaClient
) {
    private val turnMutex = Mutex()
    val allMemories = dao.observeAllMemories()
    val recordings = dao.observeRecordings()
    val progress = dao.observeProgress()
    val reviews = dao.observeReviews()
    val backup by lazy { BackupStore(database, settingsStore, recordingDirectory) }
    val chats = dao.observeChats()
    val memories = dao.observeGlobalMemories()
    val sources = dao.observeKnowledgeSources()
    val settings = settingsStore.settings
    val geminiRoute = MutableStateFlow(GeminiRouteStatus())

    fun messages(chatId: Long) = dao.observeMessages(chatId)
    fun chatMemories(chatId: Long) = dao.observeChatMemories(chatId)

    suspend fun createChat(config: TutorConfig): Long {
        val chatId = dao.insertChat(
            ChatEntity(
            title = config.topic.ifBlank { "New conversation" },
            language = config.language.name,
            topic = config.topic,
            level = config.level,
            tutorRole = config.role.name,
            correctionMode = config.correctionMode.name,
            customPrompt = config.customPrompt,
            voiceName = config.voiceName,
            voiceStyle = config.voiceStyle,
            brainMode = config.brainMode.name,
            practiceMode = config.practiceMode.name,
            speakingPace = config.speakingPace,
            silenceMs = config.silenceMs.coerceIn(800, 4000),
            speakCorrections = config.speakCorrections
            )
        )
        if (config.initialMemory.isNotBlank()) {
            addMemory("ဒီ Chat အတွက် Memory", config.initialMemory.trim(), "Chat", chatId)
        }
        dao.saveProgress(LearningProgressEntity(chatId = chatId, goal = config.topic))
        return chatId
    }

    suspend fun sendMessage(chatId: Long, userText: String, audio: AudioPayload? = null, opening: Boolean = false): TutorReply = turnMutex.withLock {
        val chat = dao.getChat(chatId) ?: error("Chat not found")
        val appSettings = settingsStore.settings.first()
        val history = dao.recentMessages(chatId).reversed()
        val memories = ContextSelector.memories(dao.enabledMemories(chatId), chatId, chat.topic + " " + userText)
        val sources = ContextSelector.sources(dao.enabledKnowledgeSources(), chat.topic + " " + userText)
        val currentProgress = dao.getProgress(chatId) ?: LearningProgressEntity(chatId, chat.topic)
        val system = buildSystemInstruction(chat, memories, sources, appSettings.explanationLanguage, appSettings.globalBehavior) +
            "\nLearner profile (${appSettings.displayName}): ${appSettings.learnerProfile}\n" + PracticeEngine.instruction(currentProgress.state(), practiceMode(chat)) +
            "\nReturn targetSentence, nextTargetSentence, assessment (NONE/PASSED/RETRY/UNSURE), lessonNote, voiceCommand, memoryFact, memoryEvidence, and speechText. " +
            "speechText is the exact brief spoken lesson, including one useful correction when speakCorrections=${chat.speakCorrections}, then one question. " +
            "For commands use REPEAT/SLOW/NORMAL/EXPLAIN/KEEP_TOPIC; never advance for commands. " +
            "Only emit memoryFact if the learner explicitly asks to remember a real fact; memoryEvidence must be an exact quote from heardText or the user text. " +
            "If no audio is attached, never judge pronunciation. Speech pace: ${chat.speakingPace}. Voice style: ${chat.voiceStyle}."
        val effectiveText = userText.ifBlank { "This is my voice message." }
        val storedAudio = if (appSettings.recordPractice && audio != null) storeAudio(audio) else ""

        val userMessageId = if (opening) 0L else dao.insertMessage(
            MessageEntity(
                chatId = chatId,
                role = MessageRole.USER.name,
                content = if (audio != null && userText.isBlank()) "🎤 Voice message" else userText,
                audioPath = storedAudio,
                audioMimeType = if (storedAudio.isNotBlank()) audio?.mimeType.orEmpty() else ""
            )
        )

        val mode = runCatching { BrainMode.valueOf(chat.brainMode) }.getOrDefault(appSettings.brainMode)
        val reply = when (mode) {
            BrainMode.GEMINI_ONLY -> geminiTutor(
                appSettings.geminiModel, system, history, effectiveText, audio
            )

            BrainMode.NVIDIA_BRAIN -> {
                if (audio != null) {
                    val draft = geminiTutor(appSettings.geminiModel, system, history, effectiveText, audio)
                    val checked = nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history,
                        draft.heardText.ifBlank { "Unclear voice message; do not infer pronunciation from text." }, draft.spokenText
                    )
                    geminiFinalize(appSettings.geminiModel, system, effectiveText, draft, checked)
                } else {
                    val raw = nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history, effectiveText
                    )
                    geminiFinalize(appSettings.geminiModel, system, effectiveText, TutorReply(reply = raw), raw)
                }
            }

            BrainMode.HYBRID_AUTO -> {
                val draft = geminiTutor(appSettings.geminiModel, system, history, effectiveText, audio)
                if (shouldVerifyWithNvidia(chat, draft.heardText.ifBlank { effectiveText }) && secrets.nvidiaApiKey.isNotBlank()) {
                    val review = nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history, draft.heardText.ifBlank { effectiveText }, draft.spokenText
                    )
                    geminiFinalize(appSettings.geminiModel, system, effectiveText, draft, review)
                } else draft
            }

            BrainMode.BEST_QUALITY -> coroutineScope {
                val geminiDraft = async {
                    geminiTutor(appSettings.geminiModel, system, history, effectiveText, audio)
                }
                val nvidiaDraft = async {
                    nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history,
                        if (audio != null) geminiDraft.await().heardText else effectiveText
                    )
                }
                val draft = geminiDraft.await()
                geminiFinalize(appSettings.geminiModel, system, effectiveText, draft, nvidiaDraft.await())
            }
        }

        if (userMessageId > 0 && audio != null && reply.heardText.isNotBlank()) {
            dao.updateMessageContent(userMessageId, reply.heardText.trim())
        }

        dao.insertMessage(
            MessageEntity(
                chatId = chatId,
                role = MessageRole.ASSISTANT.name,
                content = listOf(reply.reply, reply.followUpQuestion).filter { it.isNotBlank() }.joinToString("\n\n"),
                translation = reply.translation,
                correction = reply.correction,
                explanation = reply.explanation
            )
        )
        applyAssessment(chat, PracticeAssessment(reply.targetSentence, reply.assessment, reply.correction, reply.lessonNote, reply.voiceCommand, reply.nextTargetSentence),
            reply.heardText.ifBlank { if (opening) "" else userText }, reply.memoryFact, reply.memoryEvidence)
        dao.touchChat(chatId)
        reply
    }

    suspend fun synthesize(chatId: Long, text: String): AudioPayload {
        val chat = dao.getChat(chatId) ?: error("Chat not found")
        val settings = settingsStore.settings.first()
        return geminiSpeech(settings.geminiTtsModel, text, chat.voiceName, chat.voiceStyle + " Speak at ${chat.speakingPace.lowercase()} pace.")
    }

    suspend fun prefersReliableVoice(chatId: Long): Boolean = dao.getChat(chatId)?.brainMode != BrainMode.GEMINI_ONLY.name

    suspend fun openingTurn(chatId: Long): HandsFreeTurn {
        val reply = sendMessage(chatId, "Start or continue my current practice step. Ask one short question and wait for my answer.", opening = true)
        val text = reply.speech(dao.getChat(chatId)?.speakCorrections ?: true)
        return HandsFreeTurn("", text, synthesize(chatId, text))
    }

    suspend fun handsFreeTurn(chatId: Long, audio: AudioPayload): HandsFreeTurn {
        val reply = sendMessage(chatId, "", audio)
        val speechText = reply.speech(dao.getChat(chatId)?.speakCorrections ?: true)
        val speech = synthesize(chatId, speechText)
        return HandsFreeTurn(
            heardText = reply.heardText.ifBlank { "Voice message" },
            replyText = speechText,
            speech = speech
        )
    }

    suspend fun previewVoice(voice: String, style: String, text: String): AudioPayload {
        val settings = settingsStore.settings.first()
        return geminiSpeech(settings.geminiTtsModel, text, voice, style)
    }

    suspend fun analyzeAndAddSource(name: String, mimeType: String, uri: String, bytes: ByteArray) {
        require(bytes.size <= 14 * 1024 * 1024) { "File size must be 14 MB or less" }
        val settings = settingsStore.settings.first()
        val summary = withGeminiFallback("Document", settings.geminiModel, textModels(settings.geminiModel)) { model ->
            gemini.summarizeSource(secrets.geminiApiKey, model, name, mimeType, bytes)
        }.value
        dao.insertKnowledgeSource(
            KnowledgeSourceEntity(name = name, mimeType = mimeType, uri = uri, summary = summary)
        )
    }

    suspend fun addMemory(title: String, content: String, category: String = "Personal", chatId: Long? = null) {
        dao.insertMemory(MemoryEntity(title = title, content = content, category = category, scopeChatId = chatId))
    }

    suspend fun updateChatBehavior(chatId: Long, behavior: String) {
        val chat = dao.getChat(chatId) ?: return
        dao.updateChat(chat.copy(customPrompt = behavior.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun liveSessionConfig(chatId: Long): LiveSessionConfig {
        val chat = dao.getChat(chatId) ?: error("Chat not found")
        val appSettings = settingsStore.settings.first()
        val recentHistory = dao.recentMessages(chatId, 16).reversed()
        val currentProgress = dao.getProgress(chatId) ?: LearningProgressEntity(chatId, chat.topic)
        val instruction = buildSystemInstruction(
            chat, ContextSelector.memories(dao.enabledMemories(chatId), chatId, chat.topic),
            ContextSelector.sources(dao.enabledKnowledgeSources(), chat.topic),
            appSettings.explanationLanguage, appSettings.globalBehavior
        ) + "\nLearner profile (${appSettings.displayName}): ${appSettings.learnerProfile}\n" +
            PracticeEngine.instruction(currentProgress.state(), practiceMode(chat)) + """
            This is live audio: speak directly, never speak JSON or field names.
            Voice style: ${chat.voiceStyle}. Pace: ${chat.speakingPace}. Speak useful corrections: ${chat.speakCorrections}.
            Listen to the actual audio before judging pronunciation. Wait for the learner's whole answer.
            Use update_learning_progress once per learner answer BEFORE speaking feedback. Use its returned stage to choose the next task.
            On the opening INTRO only, use that tool with assessment NONE to set the first target sentence.
            Never count repetition as independent mastery. Say one or two short sentences, then wait.
            Voice commands: ထပ်ပြော / 다시 말해 주세요 / repeat => REPEAT; ဖြည်းဖြည်း / 천천히 => SLOW;
            normal speed => NORMAL; မြန်မာလိုရှင်းပြ / explain => EXPLAIN; ဒီအကြောင်းပဲ / stay on topic => KEEP_TOPIC.
            Commands do not advance practice. Any personal memory needs an explicit remember request and verbatim evidence.
        """.trimIndent()
        if (secrets.geminiApiKey.isBlank()) error("Settings ထဲတွင် Gemini API key ထည့်ပါ")
        return LiveSessionConfig(
            apiKey = secrets.geminiApiKey,
            models = liveModels(appSettings.liveModel),
            systemInstruction = instruction,
            voiceName = chat.voiceName,
            silenceMs = chat.silenceMs,
            initialTurns = recentHistory.map { (if (it.role == "USER") "user" else "model") to it.content },
            openingPrompt = "Continue the current ${chat.practiceMode} lesson about ${currentProgress.goal}. Current step ${currentProgress.stage}. Ask one short question and wait for me.",
            recordAudio = appSettings.recordPractice
        )
    }

    suspend fun saveLiveTurn(chatId: Long, userText: String, assistantText: String, audio: AudioPayload? = null) = turnMutex.withLock {
        if (dao.getChat(chatId) == null) return@withLock
        if (userText.isNotBlank()) {
            val record = if (audio != null && settingsStore.settings.first().recordPractice) storeAudio(audio) else ""
            dao.insertMessage(MessageEntity(chatId = chatId, role = MessageRole.USER.name, content = userText,
                audioPath = record, audioMimeType = if (record.isNotBlank()) audio?.mimeType.orEmpty() else ""))
        }
        if (assistantText.isNotBlank()) {
            dao.insertMessage(MessageEntity(chatId = chatId, role = MessageRole.ASSISTANT.name, content = assistantText))
        }
        dao.touchChat(chatId)
    }

    suspend fun createKoreanNames(name: String): KoreanNameResult {
        require(name.isNotBlank()) { "မြန်မာနာမည်ထည့်ပါ" }
        knownKoreanNameResult(name.trim())?.let { return it }
        val appSettings = settingsStore.settings.first()
        val routed = withTimeout(40_000) {
            withGeminiFallback(
                task = "Korean Name Studio",
                requested = appSettings.geminiModel,
                candidates = nameModels(appSettings.geminiModel)
            ) { model ->
                try {
                    withTimeout(16_000) {
                        gemini.createKoreanNames(secrets.geminiApiKey, model, name.trim())
                    }
                } catch (_: TimeoutCancellationException) {
                    throw AiApiException("Gemini name model timed out", 504)
                }
            }
        }
        return routed.value.copy(activeModel = routed.model)
    }

    suspend fun quickTranslate(text: String, audio: AudioPayload? = null): TranslationResult {
        require(text.isNotBlank() || audio != null) { "ဘာသာပြန်မယ့် စာသား သို့မဟုတ် အသံထည့်ပါ" }
        val appSettings = settingsStore.settings.first()
        val routed = withGeminiFallback(
            task = "Quick Translate",
            requested = appSettings.geminiModel,
            candidates = textModels(appSettings.geminiModel)
        ) { model -> gemini.quickTranslate(secrets.geminiApiKey, model, text.trim(), audio) }
        return routed.value.copy(activeModel = routed.model)
    }

    suspend fun speakToolText(text: String): AudioPayload {
        val appSettings = settingsStore.settings.first()
        return geminiSpeech(
            requested = appSettings.geminiTtsModel,
            text = text,
            voice = "Kore",
            style = "Clear, calm Korean and Myanmar language tutor voice."
        )
    }

    suspend fun replaceGeminiKey(candidate: String): Int {
        val clean = candidate.trim()
        require(clean.isNotBlank()) { "Gemini API key ထည့်ပါ" }
        val models = gemini.listModels(clean)
        require(models.isNotEmpty()) { "ဒီ Gemini key မှာ အသုံးပြုနိုင်တဲ့ model မတွေ့ပါ" }
        secrets.geminiApiKey = clean
        return models.size
    }

    suspend fun testGeminiKey(candidate: String? = null): Int {
        val key = candidate?.trim()?.takeIf { it.isNotEmpty() } ?: secrets.geminiApiKey
        require(key.isNotBlank()) { "Gemini API key ထည့်ပါ" }
        return gemini.listModels(key).size
    }

    fun removeGeminiKey() { secrets.geminiApiKey = "" }

    fun replaceNvidiaKey(candidate: String) {
        require(candidate.isNotBlank()) { "NVIDIA API key ထည့်ပါ" }
        secrets.nvidiaApiKey = candidate.trim()
    }

    fun removeNvidiaKey() { secrets.nvidiaApiKey = "" }

    suspend fun toggleMemory(memory: MemoryEntity) = dao.updateMemory(memory.copy(enabled = !memory.enabled))
    suspend fun deleteMemory(memory: MemoryEntity) = dao.deleteMemory(memory)
    suspend fun toggleSource(source: KnowledgeSourceEntity) = dao.updateKnowledgeSource(source.copy(enabled = !source.enabled))
    suspend fun deleteSource(source: KnowledgeSourceEntity) = dao.deleteKnowledgeSource(source)
    suspend fun deleteChat(chatId: Long) = turnMutex.withLock {
        dao.deleteMemoriesForChat(chatId)
        dao.recordingsForChat(chatId).forEach { deleteAudio(it.audioPath) }
        dao.deleteChat(chatId)
    }

    suspend fun saveSettings(settings: AppSettings) {
        settingsStore.update { settings }
    }

    fun hasGeminiKey() = secrets.geminiApiKey.isNotBlank()
    fun hasNvidiaKey() = secrets.nvidiaApiKey.isNotBlank()

    private fun practiceMode(chat: ChatEntity) = runCatching { PracticeMode.valueOf(chat.practiceMode) }.getOrDefault(PracticeMode.GUIDED)

    private fun storeAudio(audio: AudioPayload): String {
        recordingDirectory.mkdirs()
        val extension = if (audio.mimeType.contains("wav")) "wav" else "m4a"
        val file = File(recordingDirectory, UUID.randomUUID().toString() + "." + extension)
        file.writeBytes(audio.bytes)
        return file.name
    }

    private fun deleteAudio(name: String) {
        if (name.isNotBlank() && name == File(name).name) File(recordingDirectory, name).delete()
    }

    suspend fun deleteRecording(message: MessageEntity) {
        dao.clearRecording(message.id)
        deleteAudio(message.audioPath)
    }

    fun recording(message: MessageEntity): AudioPayload? {
        if (message.audioPath.isBlank() || message.audioPath != File(message.audioPath).name) return null
        val file = File(recordingDirectory, message.audioPath)
        return if (file.isFile) AudioPayload(file.readBytes(), message.audioMimeType) else null
    }

    suspend fun updateMemory(memory: MemoryEntity, title: String, content: String) {
        require(title.isNotBlank() && content.isNotBlank())
        dao.updateMemory(memory.copy(title = title.trim().take(200), content = content.trim().take(4000), updatedAt = System.currentTimeMillis()))
    }

    suspend fun updatePractice(chatId: Long, mode: PracticeMode, goal: String, voice: String, style: String,
        pace: String, silenceMs: Int, speakCorrections: Boolean, restart: Boolean = false) = turnMutex.withLock {
        val chat = dao.getChat(chatId) ?: return@withLock
        val nextGoal = goal.trim().ifBlank { chat.topic }.take(500)
        val reset = restart || nextGoal != chat.topic || mode.name != chat.practiceMode
        database.withTransaction {
            dao.updateChat(chat.copy(topic = nextGoal, title = nextGoal, practiceMode = mode.name,
                voiceName = voice.ifBlank { "Kore" }, voiceStyle = style.take(1000), speakingPace = pace,
                silenceMs = silenceMs.coerceIn(800, 4000), speakCorrections = speakCorrections, updatedAt = System.currentTimeMillis()))
            if (reset) dao.saveProgress(LearningProgressEntity(chatId, nextGoal))
        }
    }

    suspend fun updateLearningNotes(item: LearningProgressEntity, notes: String, correction: String) = turnMutex.withLock {
        dao.saveProgress(item.copy(summary = notes.take(1600), lastCorrection = correction.take(500), updatedAt = System.currentTimeMillis()))
        dao.getChat(item.chatId)?.let { dao.updateChat(it.copy(summary = notes.take(1600))) }
    }

    suspend fun beginReview(item: ReviewItemEntity) = turnMutex.withLock {
        val existing = dao.getProgress(item.chatId) ?: LearningProgressEntity(item.chatId, "Review")
        dao.saveProgress(existing.copy(stage = "REPEAT", targetSentence = item.sentence, updatedAt = System.currentTimeMillis()))
        dao.getChat(item.chatId)?.let { dao.updateChat(it.copy(practiceMode = "GUIDED")) }
    }

    private suspend fun applyAssessment(chat: ChatEntity, result: PracticeAssessment, heard: String, fact: String = "", evidence: String = ""): PracticeState {
        val settings = settingsStore.settings.first()
        val old = dao.getProgress(chat.id) ?: LearningProgressEntity(chat.id, chat.topic)
        val groundedResult = if (heard.isBlank() && old.stage != "INTRO" && result.assessment == "PASSED") result.copy(assessment = "UNSURE") else result
        val safeResult = if (settings.autoLearningMemory) groundedResult else groundedResult.copy(note = "", correction = "")
        val next = PracticeEngine.advance(old.state(), safeResult, practiceMode(chat))
        database.withTransaction {
            dao.saveProgress(old.copy(stage = next.stage, targetSentence = next.targetSentence, completed = next.completed,
                summary = next.summary, lastCorrection = next.lastCorrection, updatedAt = System.currentTimeMillis()))
            if (settings.autoLearningMemory) {
                val sentence = old.targetSentence.ifBlank { result.targetSentence }.take(300)
                if (sentence.isNotBlank() && groundedResult.assessment in listOf("PASSED", "RETRY", "UNSURE") && result.command.isBlank()) {
                    val prior = dao.getReview(chat.id, sentence)
                    val success = groundedResult.assessment == "PASSED" && old.stage == "APPLY"
                    dao.saveReview(ReviewItemEntity(id = prior?.id ?: 0, chatId = chat.id, sentence = sentence,
                        correction = result.correction.take(500), successes = (prior?.successes ?: 0) + if (success) 1 else 0,
                        dueAt = if (success) System.currentTimeMillis() + 86_400_000 else System.currentTimeMillis()))
                }
                dao.updateChat(chat.copy(summary = next.summary, speakingPace = when (result.command) {
                    "SLOW" -> "SLOW"; "NORMAL" -> "NATURAL"; else -> chat.speakingPace
                }, updatedAt = System.currentTimeMillis()))
                ContextSelector.explicitFact(heard, fact, evidence, practiceMode(chat))?.let {
                    dao.insertMemory(MemoryEntity(title = "Remembered from conversation", content = it, category = "Personal"))
                }
            } else if (result.command in listOf("SLOW", "NORMAL")) {
                dao.updateChat(chat.copy(speakingPace = if (result.command == "SLOW") "SLOW" else "NATURAL"))
            }
        }
        return next
    }

    suspend fun handleLiveTool(chatId: Long, args: JsonObject): JsonObject = turnMutex.withLock {
        fun value(key: String) = args[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val chat = dao.getChat(chatId) ?: error("Chat not found")
        val state = applyAssessment(chat, PracticeAssessment(value("targetSentence"), value("assessment"), value("correction"),
            value("lessonNote"), value("voiceCommand"), value("nextTargetSentence")), value("heardText"), value("memoryFact"), value("memoryEvidence"))
        buildJsonObject {
            put("stage", JsonPrimitive(state.stage))
            put("goal", JsonPrimitive(state.goal))
            put("targetSentence", JsonPrimitive(state.targetSentence))
            put("completed", JsonPrimitive(state.completed))
            put("voicePace", JsonPrimitive(dao.getChat(chatId)?.speakingPace ?: "SLOW"))
            put("relevantDocuments", JsonPrimitive(ContextSelector.sources(dao.enabledKnowledgeSources(), value("heardText")).joinToString("\n") { "[${it.name}] ${it.summary}" }))
            put("nextInstruction", JsonPrimitive(PracticeEngine.instruction(state, practiceMode(chat))))
        }
    }

    private suspend fun geminiTutor(
        requested: String,
        system: String,
        history: List<MessageEntity>,
        text: String,
        audio: AudioPayload?
    ): TutorReply = withGeminiFallback("Message Chat", requested, textModels(requested)) { model ->
        gemini.tutorReply(secrets.geminiApiKey, model, system, history, text, audio)
    }.value

    private suspend fun geminiFinalize(
        requested: String,
        system: String,
        text: String,
        draft: TutorReply,
        review: String
    ): TutorReply = withGeminiFallback("Final answer", requested, textModels(requested)) { model ->
        gemini.finalizeWithReview(secrets.geminiApiKey, model, system, text, draft, review)
    }.value

    private suspend fun geminiSpeech(
        requested: String,
        text: String,
        voice: String,
        style: String
    ): AudioPayload = withGeminiFallback("Gemini voice", requested, ttsModels(requested)) { model ->
        gemini.synthesize(secrets.geminiApiKey, model, text, voice, style)
    }.value

    private suspend fun <T> withGeminiFallback(
        task: String,
        requested: String,
        candidates: List<String>,
        call: suspend (String) -> T
    ): Routed<T> {
        var lastFailure: Throwable? = null
        candidates.distinct().forEachIndexed { index, model ->
            try {
                val value = call(model)
                geminiRoute.value = GeminiRouteStatus(
                    task = task,
                    requestedModel = requested,
                    activeModel = model,
                    usedFallback = index > 0
                )
                return Routed(value, model)
            } catch (failure: Throwable) {
                lastFailure = failure
                if (!isGeminiFallbackEligible(failure) || index == candidates.lastIndex) throw failure
            }
        }
        throw lastFailure ?: AiApiException("Gemini model မရပါ")
    }

    private fun textModels(requested: String) = listOf(
        requested,
        "gemini-3.8-flash",
        "gemini-2.5-flash"
    ).filter(String::isNotBlank).distinct()

    private fun ttsModels(requested: String) = listOf(
        requested,
        "gemini-3.8-flash-tts",
        "gemini-3.1-flash-tts-preview",
        "gemini-2.5-flash-preview-tts"
    ).filter(String::isNotBlank).distinct()

    private fun nameModels(requested: String) = listOf(
        requested,
        "gemini-3.8-flash",
        "gemini-2.5-flash"
    ).filter(String::isNotBlank).distinct()

    private fun liveModels(requested: String) = listOf(
        requested,
        "gemini-3.8-live",
        "gemini-3.1-flash-live-preview",
        "gemini-2.5-flash-native-audio-preview-12-2025"
    ).filter(String::isNotBlank).distinct()

    private data class Routed<T>(val value: T, val model: String)

    private fun shouldVerifyWithNvidia(chat: ChatEntity, text: String): Boolean {
        val marker = "${chat.topic} $text".lowercase()
        return text.length > 280 || listOf(
            "grammar", "문법", "စာချုပ်", "contract", "ဥပဒေ", "အဓိပ္ပာယ်", "explain"
        ).any(marker::contains)
    }

    private fun buildSystemInstruction(
        chat: ChatEntity,
        memories: List<MemoryEntity>,
        sources: List<KnowledgeSourceEntity>,
        explanationLanguage: String,
        globalBehavior: String
    ): String = buildString {
        val target = runCatching { AppLanguage.valueOf(chat.language) }.getOrDefault(AppLanguage.KOREAN)
        appendLine("You are Language Talk AI, a patient and practical language tutor.")
        appendLine("Global behavior instructions: $globalBehavior")
        appendLine("Target language: ${target.label}; learner level: ${chat.level}; topic: ${chat.topic}.")
        appendLine("Role: ${chat.tutorRole}; correction mode: ${chat.correctionMode}.")
        appendLine("Explain corrections and meanings in $explanationLanguage, while keeping target-language examples intact.")
        appendLine("Stay on the learner's chosen goal. Ask one relevant question and WAIT. Never simulate the learner or give a monologue.")
        appendLine("Do not invent details from personal documents. If context is insufficient, say so clearly.")
        appendLine("Use the target language for practice and $explanationLanguage for short explanations when needed.")
        if (chat.customPrompt.isNotBlank()) appendLine("Chat-specific instruction: ${chat.customPrompt}")
        if (memories.isNotEmpty()) {
            appendLine("\nLearner memory:")
            memories.take(30).forEach { appendLine("- ${it.title}: ${it.content}") }
        }
        if (sources.isNotEmpty()) {
            appendLine("\nEnabled personal knowledge sources:")
            sources.take(15).forEach { appendLine("- [${it.name}] ${it.summary}") }
        }
    }
}

internal fun isGeminiFallbackEligible(error: Throwable): Boolean {
    val api = error as? AiApiException ?: return false
    if (api.statusCode == 401 || api.statusCode == 403) return false
    if (api.statusCode == 404 || api.statusCode == 429 || (api.statusCode ?: 0) >= 500) return true
    val message = api.message.orEmpty().lowercase()
    return api.statusCode == null || listOf(
        "model", "not found", "not supported", "unavailable", "quota",
        "invalid hangul", "invalid structured", "empty translation", "no audio"
    ).any(message::contains)
}
