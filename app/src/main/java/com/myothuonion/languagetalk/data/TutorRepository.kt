package com.myothuonion.languagetalk.data

import com.myothuonion.languagetalk.model.AppLanguage
import com.myothuonion.languagetalk.model.BrainMode
import com.myothuonion.languagetalk.model.MessageRole
import com.myothuonion.languagetalk.model.TutorConfig
import com.myothuonion.languagetalk.model.TutorReply
import com.myothuonion.languagetalk.network.AudioPayload
import com.myothuonion.languagetalk.network.GeminiClient
import com.myothuonion.languagetalk.network.NvidiaClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class TutorRepository(
    private val dao: LanguageTalkDao,
    private val settingsStore: SettingsStore,
    private val secrets: SecretStore,
    private val gemini: GeminiClient,
    private val nvidia: NvidiaClient
) {
    val chats = dao.observeChats()
    val memories = dao.observeMemories()
    val sources = dao.observeKnowledgeSources()
    val settings = settingsStore.settings

    fun messages(chatId: Long) = dao.observeMessages(chatId)

    suspend fun createChat(config: TutorConfig): Long = dao.insertChat(
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

    suspend fun sendMessage(chatId: Long, userText: String, audio: AudioPayload? = null): TutorReply {
        val chat = dao.getChat(chatId) ?: error("Chat not found")
        val appSettings = settingsStore.settings.first()
        val history = dao.recentMessages(chatId).reversed()
        val memories = dao.enabledMemories()
        val sources = dao.enabledKnowledgeSources()
        val system = buildSystemInstruction(chat, memories, sources, appSettings.explanationLanguage)
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
            BrainMode.GEMINI_ONLY -> gemini.tutorReply(
                secrets.geminiApiKey, appSettings.geminiModel, system, history, effectiveText, audio
            )

            BrainMode.NVIDIA_BRAIN -> {
                if (audio != null) {
                    val draft = gemini.tutorReply(
                        secrets.geminiApiKey, appSettings.geminiModel, system, history, effectiveText, audio
                    )
                    val checked = nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history,
                        "The learner sent a voice message.", draft.spokenText
                    )
                    gemini.finalizeWithReview(
                        secrets.geminiApiKey, appSettings.geminiModel, system, effectiveText, draft, checked
                    )
                } else {
                    val raw = nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history, effectiveText
                    )
                    TutorReply(reply = raw)
                }
            }

            BrainMode.HYBRID_AUTO -> {
                val draft = gemini.tutorReply(
                    secrets.geminiApiKey, appSettings.geminiModel, system, history, effectiveText, audio
                )
                if (shouldVerifyWithNvidia(chat, effectiveText) && secrets.nvidiaApiKey.isNotBlank()) {
                    val review = nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history, effectiveText, draft.spokenText
                    )
                    gemini.finalizeWithReview(
                        secrets.geminiApiKey, appSettings.geminiModel, system, effectiveText, draft, review
                    )
                } else draft
            }

            BrainMode.BEST_QUALITY -> coroutineScope {
                val geminiDraft = async {
                    gemini.tutorReply(
                        secrets.geminiApiKey, appSettings.geminiModel, system, history, effectiveText, audio
                    )
                }
                val nvidiaDraft = async {
                    nvidia.review(
                        secrets.nvidiaApiKey, appSettings.nvidiaModel, system, history, effectiveText
                    )
                }
                val draft = geminiDraft.await()
                gemini.finalizeWithReview(
                    secrets.geminiApiKey, appSettings.geminiModel, system,
                    effectiveText, draft, nvidiaDraft.await()
                )
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
        return gemini.synthesize(
            secrets.geminiApiKey,
            settings.geminiTtsModel,
            text,
            chat.voiceName,
            chat.voiceStyle
        )
    }

    suspend fun previewVoice(voice: String, style: String, text: String): AudioPayload {
        val settings = settingsStore.settings.first()
        return gemini.synthesize(secrets.geminiApiKey, settings.geminiTtsModel, text, voice, style)
    }

    suspend fun analyzeAndAddSource(name: String, mimeType: String, uri: String, bytes: ByteArray) {
        require(bytes.size <= 14 * 1024 * 1024) { "File size must be 14 MB or less" }
        val settings = settingsStore.settings.first()
        val summary = gemini.summarizeSource(
            secrets.geminiApiKey, settings.geminiModel, name, mimeType, bytes
        )
        dao.insertKnowledgeSource(
            KnowledgeSourceEntity(name = name, mimeType = mimeType, uri = uri, summary = summary)
        )
    }

    suspend fun addMemory(title: String, content: String, category: String = "Personal") {
        dao.insertMemory(MemoryEntity(title = title, content = content, category = category))
    }

    suspend fun toggleMemory(memory: MemoryEntity) = dao.updateMemory(memory.copy(enabled = !memory.enabled))
    suspend fun deleteMemory(memory: MemoryEntity) = dao.deleteMemory(memory)
    suspend fun toggleSource(source: KnowledgeSourceEntity) = dao.updateKnowledgeSource(source.copy(enabled = !source.enabled))
    suspend fun deleteSource(source: KnowledgeSourceEntity) = dao.deleteKnowledgeSource(source)
    suspend fun deleteChat(chatId: Long) = dao.deleteChat(chatId)

    suspend fun saveSettings(settings: AppSettings, geminiKey: String?, nvidiaKey: String?) {
        settingsStore.update { settings }
        geminiKey?.let { secrets.geminiApiKey = it.trim() }
        nvidiaKey?.let { secrets.nvidiaApiKey = it.trim() }
    }

    fun hasGeminiKey() = secrets.geminiApiKey.isNotBlank()
    fun hasNvidiaKey() = secrets.nvidiaApiKey.isNotBlank()

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
        explanationLanguage: String
    ): String = buildString {
        val target = runCatching { AppLanguage.valueOf(chat.language) }.getOrDefault(AppLanguage.KOREAN)
        appendLine("You are Language Talk AI, a patient and practical language tutor.")
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
