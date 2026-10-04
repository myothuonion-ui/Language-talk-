package com.myothuonion.languagetalk.model

import kotlinx.serialization.Serializable

@Serializable
enum class AiProviderKind(val label: String) {
    GEMINI("Gemini"), NVIDIA("NVIDIA"), OPENAI("OpenAI"), CLAUDE("Claude"), DEEPSEEK("DeepSeek"), CUSTOM("Custom")
}

@Serializable
enum class ApiFormat { OPENAI_CHAT, CLAUDE_MESSAGES, GEMINI }

@Serializable
enum class AiTask(val label: String) {
    TRANSLATE("Translate"), READER("Reader dictionary"), BOOK("စာအုပ်သင်ခန်းစာ"),
    COACH("ဒီနေ့ နည်းနည်းစီ"), CONVERSATION("စကားပြော AI"), SPEECH("ကိုရီးယားအသံ"),
    TRANSCRIBE("အသံကို စာဖမ်း"), LIVE("Native Live"), DOCUMENT("My Context documents")
}

@Serializable
enum class AiMode(val label: String) { AUTO("Auto"), REVIEW("AI နှစ်ခုနဲ့စစ်"), CUSTOM("Custom") }

@Serializable
data class AiProfile(
    val id: String,
    val label: String,
    val kind: AiProviderKind,
    val baseUrl: String,
    val format: ApiFormat = ApiFormat.OPENAI_CHAT,
    val enabled: Boolean = true,
    val useAsFallback: Boolean = false,
    val textModel: String = "",
    val textBackups: List<String> = emptyList(),
    val speechModel: String = "",
    val speechBackups: List<String> = emptyList(),
    val transcribeModel: String = "",
    val liveModel: String = "",
    val voice: String = "coral",
    val audioEndpoints: Boolean = false
) {
    fun supports(task: AiTask): Boolean = when (task) {
        AiTask.LIVE -> kind == AiProviderKind.GEMINI && format == ApiFormat.GEMINI && liveModel.isNotBlank()
        AiTask.SPEECH -> format == ApiFormat.GEMINI || (audioEndpoints && speechModel.isNotBlank())
        AiTask.TRANSCRIBE -> format == ApiFormat.GEMINI || (audioEndpoints && transcribeModel.isNotBlank())
        else -> textModel.isNotBlank()
    }
}

@Serializable
data class AiModelRef(val profileId: String, val model: String = "")

@Serializable
data class AiRoutePrefs(
    val mode: AiMode = AiMode.AUTO,
    val primary: AiModelRef? = null,
    val modelBackups: List<String> = emptyList(),
    val fallbacks: List<AiModelRef> = emptyList(),
    val reviewer: AiModelRef? = null,
    val allowFallback: Boolean = true
)

@Serializable
data class AiConfiguration(
    val profiles: List<AiProfile> = builtInAiProfiles(),
    val routes: Map<AiTask, AiRoutePrefs> = emptyMap()
)

fun builtInAiProfiles() = listOf(
    AiProfile("gemini", "Gemini", AiProviderKind.GEMINI, "https://generativelanguage.googleapis.com/v1beta/",
        ApiFormat.GEMINI, useAsFallback = true, textModel = "gemini-3.8-flash",
        textBackups = listOf("gemini-3.7-flash", "gemini-3.5-flash-lite"),
        speechModel = "gemini-3.8-flash-tts", speechBackups = listOf("gemini-3.8-flash-lite-tts"),
        liveModel = "gemini-3.8-live"),
    AiProfile("nvidia", "NVIDIA", AiProviderKind.NVIDIA, "https://integrate.api.nvidia.com/v1/",
        useAsFallback = true, textModel = "z-ai/glm-5.3-flash",
        textBackups = listOf("deepseek-ai/deepseek-v4.1-flash", "z-ai/glm-5.3", "moonshotai/kimi-k3")),
    AiProfile("openai", "OpenAI", AiProviderKind.OPENAI, "https://api.openai.com/v1/",
        textModel = "gpt-6-luna", textBackups = listOf("gpt-6.1-sol"),
        speechModel = "gpt-4o-mini-tts", transcribeModel = "gpt-transcribe", audioEndpoints = true),
    AiProfile("claude", "Claude", AiProviderKind.CLAUDE, "https://api.anthropic.com/v1/",
        ApiFormat.CLAUDE_MESSAGES, textModel = "claude-sonnet-5-5",
        textBackups = listOf("claude-haiku-4-5-20251001")),
    AiProfile("deepseek", "DeepSeek", AiProviderKind.DEEPSEEK, "https://api.deepseek.com/",
        textModel = "deepseek-flash", textBackups = listOf("deepseek-v4-pro"))
)

/** One profile owns one credential and endpoint. Text models never become speech models. */
object AiPlans {
    fun models(profile: AiProfile, task: AiTask, requested: String = "", backups: List<String> = emptyList()): List<String> {
        val defaults = when (task) {
            AiTask.SPEECH -> listOf(profile.speechModel) + profile.speechBackups
            AiTask.TRANSCRIBE -> if (profile.format == ApiFormat.GEMINI)
                listOf(profile.textModel) + profile.textBackups else listOf(profile.transcribeModel)
            AiTask.LIVE -> listOf(profile.liveModel, "gemini-3.8-live-extended-thinking")
            else -> when {
                profile.id == "gemini" && task in setOf(AiTask.TRANSLATE, AiTask.READER) ->
                    listOf("gemini-3.5-flash-lite", profile.textModel) + profile.textBackups
                profile.id == "nvidia" && task in setOf(AiTask.BOOK, AiTask.COACH, AiTask.DOCUMENT) ->
                    listOf("z-ai/glm-5.3", "moonshotai/kimi-k3", profile.textModel) + profile.textBackups
                else -> listOf(profile.textModel) + profile.textBackups
            }
        }
        return (listOf(requested) + backups + defaults).map(String::trim).filter(String::isNotBlank).distinct()
    }

    fun candidates(config: AiConfiguration, task: AiTask, ready: (String) -> Boolean,
        override: AiRoutePrefs? = null): List<AiModelRef> {
        val route = override ?: config.routes[task] ?: AiRoutePrefs()
        fun eligible(p: AiProfile) = p.enabled && p.supports(task) && ready(p.id)
        val primary = route.primary?.let { ref -> config.profiles.firstOrNull { it.id == ref.profileId && eligible(it) } }
            ?: if (route.primary == null) config.profiles.firstOrNull { eligible(it) && it.useAsFallback } else null
        val result = mutableListOf<AiModelRef>()
        if (primary != null) models(primary, task, route.primary?.model.orEmpty(), route.modelBackups).forEach {
            result += AiModelRef(primary.id, it)
        }
        if (route.allowFallback) {
            val refs = route.fallbacks + config.profiles.filter { it.id != primary?.id && eligible(it) && it.useAsFallback }
                .map { AiModelRef(it.id) }
            refs.distinctBy { it.profileId }.forEach { ref ->
                config.profiles.firstOrNull { it.id == ref.profileId && eligible(it) && it.useAsFallback }?.let { profile ->
                    models(profile, task, ref.model).forEach { result += AiModelRef(profile.id, it) }
                }
            }
        }
        return result.distinct()
    }

    fun reviewer(config: AiConfiguration, task: AiTask, primaryId: String, ready: (String) -> Boolean,
        route: AiRoutePrefs): AiModelRef? {
        val explicit = route.reviewer
        val profile = if (explicit != null) config.profiles.firstOrNull {
            it.id == explicit.profileId && it.id != primaryId && it.enabled && it.supports(task) && ready(it.id)
        } else config.profiles.firstOrNull {
            it.id != primaryId && it.enabled && it.supports(task) && it.useAsFallback && ready(it.id)
        }
        return profile?.let {
            AiModelRef(it.id, explicit?.model?.takeIf(String::isNotBlank) ?:
                if (it.kind == AiProviderKind.NVIDIA) "z-ai/glm-5.3" else models(it, task).first())
        }
    }
}

object VoiceText {
    /** Burmese explanations stay on screen; only Hangul phrases are sent to Korean TTS. */
    fun korean(text: String): String = Regex("[\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7AF0-9\\s.,!?…:;()~·-]+")
        .findAll(text).map { it.value.trim() }.filter { chunk ->
            chunk.any { it in '\uAC00'..'\uD7AF' || it in '\u3130'..'\u318F' || it in '\u1100'..'\u11FF' }
        }.joinToString(" ").replace(Regex("\\s+"), " ").trim().take(12000)
}
