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
import com.myothuonion.languagetalk.network.AudioPayload
import com.myothuonion.languagetalk.network.AiApiException
import com.myothuonion.languagetalk.network.GeminiClient
import com.myothuonion.languagetalk.network.NvidiaClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow

class TutorRepository(
    private val dao: LanguageTalkDao,
    private val settingsStore: SettingsStore,
    private val secrets: SecretStore,
    private val gemini: GeminiClient,
    private val nvidia: NvidiaClient
) {
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
            brainMode = config.brainMode.name
            )
        )
        if (config.initialMemory.isNotBlank()) {
            addMemory("ဒီ Chat အတွက် Memory", config.initialMemory.trim(), "Chat", chatId)
        }
        return chatId
    }

    suspend fun sendMessage(chatId: Long, userText: String, audio: AudioPayload? = null): TutorReply {
        val chat = dao.getChat(chatId) ?: error("Chat not found")
        val appSettings = settingsStore.settings.first()
        val history = dao.recentMessages(chatId).reversed()
        val memories = dao.enabledMemories(chatId)
        val sources = dao.enabledKnowledgeSources()
        val system = buildSystemInstruction(chat, memories, sources, appSettings.explanationLanguage, appSettings.globalBehavior)
        val effectiveText = userText.ifBlank { "This is my voice message." }

        dao.insertMessage(
            MessageEntity(
                chatId = chatId,
                role = MessageRole.USER.name,
                content = if (audio != null && userText.isBlank()) "🎤 Voice message" else userText
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
                        "The learner sent a voice message.", draft.spokenText
                    )
                    geminiFinalize(appSettings.geminiModel, system, effectiveText, draft, checked)
                } else {
                    val raw = nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history, effectiveText
                    )
                    TutorReply(reply = raw)
                }
            }

            BrainMode.HYBRID_AUTO -> {
                val draft = geminiTutor(appSettings.geminiModel, system, history, effectiveText, audio)
                if (shouldVerifyWithNvidia(chat, effectiveText) && secrets.nvidiaApiKey.isNotBlank()) {
                    val review = nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history, effectiveText, draft.spokenText
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
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history, effectiveText
                    )
                }
                val draft = geminiDraft.await()
                geminiFinalize(appSettings.geminiModel, system, effectiveText, draft, nvidiaDraft.await())
            }
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
        dao.touchChat(chatId)
        return reply
    }

    suspend fun synthesize(chatId: Long, text: String): AudioPayload {
        val chat = dao.getChat(chatId) ?: error("Chat not found")
        val settings = settingsStore.settings.first()
        return geminiSpeech(settings.geminiTtsModel, text, chat.voiceName, chat.voiceStyle)
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
        val instruction = buildSystemInstruction(
            chat,
            dao.enabledMemories(chatId),
            dao.enabledKnowledgeSources(),
            appSettings.explanationLanguage,
            appSettings.globalBehavior
        ) + buildString {
            if (recentHistory.isNotEmpty()) {
                appendLine("\nRecent conversation to continue naturally:")
                recentHistory.forEach { appendLine("${it.role}: ${it.content}") }
            }
            appendLine("\nThis is a hands-free live conversation. Speak naturally and briefly.")
            appendLine("Listen continuously without requiring a send button. Let the learner interrupt you at any time.")
        }
        if (secrets.geminiApiKey.isBlank()) error("Settings ထဲတွင် Gemini API key ထည့်ပါ")
        return LiveSessionConfig(
            apiKey = secrets.geminiApiKey,
            models = liveModels(appSettings.liveModel),
            systemInstruction = instruction,
            voiceName = chat.voiceName
        )
    }

    suspend fun saveLiveTurn(chatId: Long, userText: String, assistantText: String) {
        if (userText.isNotBlank()) {
            dao.insertMessage(MessageEntity(chatId = chatId, role = MessageRole.USER.name, content = userText))
        }
        if (assistantText.isNotBlank()) {
            dao.insertMessage(MessageEntity(chatId = chatId, role = MessageRole.ASSISTANT.name, content = assistantText))
        }
        dao.touchChat(chatId)
    }

    suspend fun createKoreanNames(name: String): KoreanNameResult {
        require(name.isNotBlank()) { "မြန်မာနာမည်ထည့်ပါ" }
        val appSettings = settingsStore.settings.first()
        val routed = withGeminiFallback(
            task = "Korean Name Studio",
            requested = appSettings.geminiModel,
            candidates = textModels(appSettings.geminiModel)
        ) { model -> gemini.createKoreanNames(secrets.geminiApiKey, model, name.trim()) }
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
    suspend fun deleteChat(chatId: Long) {
        dao.deleteMemoriesForChat(chatId)
        dao.deleteChat(chatId)
    }

    suspend fun saveSettings(settings: AppSettings) {
        settingsStore.update { settings }
    }

    fun hasGeminiKey() = secrets.geminiApiKey.isNotBlank()
    fun hasNvidiaKey() = secrets.nvidiaApiKey.isNotBlank()

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
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-2.5-flash"
    ).filter(String::isNotBlank).distinct()

    private fun ttsModels(requested: String) = listOf(
        requested,
        "gemini-3.1-flash-tts-preview",
        "gemini-2.5-flash-preview-tts"
    ).filter(String::isNotBlank).distinct()

    private fun liveModels(requested: String) = listOf(
        requested,
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
        appendLine("Reply naturally, then ask exactly one useful follow-up question so the learner speaks again.")
        appendLine("Do not invent details from personal documents. If context is insufficient, say so clearly.")
        appendLine("The JSON reply field must be in the target language. translation/explanation should be in $explanationLanguage.")
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
