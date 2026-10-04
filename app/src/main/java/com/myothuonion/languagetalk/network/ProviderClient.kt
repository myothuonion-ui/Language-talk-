package com.myothuonion.languagetalk.network

import com.myothuonion.languagetalk.data.MessageEntity
import com.myothuonion.languagetalk.model.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Native Messages and OpenAI-compatible Chat APIs, with credentials scoped to one endpoint. */
class ProviderClient(
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val media = "application/json; charset=utf-8".toMediaType()

    suspend fun tutor(profile: AiProfile, key: String, model: String, instruction: String,
        history: List<MessageEntity>, input: String, allowEmptyReply: Boolean = false): TutorReply {
        val contract = """
            Return ONLY a valid JSON object, without markdown fences or reasoning text.
            All these fields are strings (empty when irrelevant): reply, heardText, translation,
            correction, explanation, followUpQuestion, targetSentence, assessment, lessonNote,
            voiceCommand, memoryFact, memoryEvidence, speechText, nextTargetSentence,
            answerPattern, answerExample, answerHint.
            assessment MUST be NONE, PASSED, RETRY, or UNSURE.
            Explanations, translation, answerHint and lessonNote use Myanmar TEXT.
            speechText contains Korean only. Never speak Myanmar explanations.
            answerPattern is a short Korean response frame with [slots] for the ONE question
            you just asked, not a question frame. answerExample is a matching Korean example.
            Give no answer hints for assessments, dictionaries, or book explanations unless instructed.
        """.trimIndent()
        val raw = text(profile, key, model, instruction + "\n" + contract, history, input)
        return GeminiClient().parseTutorReply(raw, allowEmptyReply)
    }

    suspend fun text(profile: AiProfile, key: String, model: String, instruction: String,
        history: List<MessageEntity>, input: String): String {
        require(key.isNotBlank()) { "API key ထည့်ပါ။" }
        val messages = buildJsonArray {
            if (profile.format != ApiFormat.CLAUDE_MESSAGES)
                add(buildJsonObject { put("role", "system"); put("content", instruction) })
            history.filter { it.role in setOf("USER", "ASSISTANT") }.takeLast(16).forEach {
                add(buildJsonObject { put("role", if (it.role == "USER") "user" else "assistant"); put("content", it.content.take(12000)) })
            }
            add(buildJsonObject { put("role", "user"); put("content", input.take(60000)) })
        }
        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
            if (profile.format == ApiFormat.CLAUDE_MESSAGES) {
                put("system", instruction); put("max_tokens", 8192)
            } else {
                put(if (profile.kind == AiProviderKind.OPENAI) "max_completion_tokens" else "max_tokens", 8192)
                put("stream", false)
            }
        }
        val endpoint = if (profile.format == ApiFormat.CLAUDE_MESSAGES) "messages" else "chat/completions"
        val root = parse(post(profile, key, endpoint, body.toString().toRequestBody(media)))
        val value = if (profile.format == ApiFormat.CLAUDE_MESSAGES) root["content"]?.jsonArray.orEmpty()
            .filter { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "text" }
            .joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        else root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (value.isBlank()) throw AiApiException("AI returned an empty response")
        return value
    }

    suspend fun transcribe(profile: AiProfile, key: String, model: String, audio: AudioPayload): String {
        require(profile.audioEndpoints && profile.format == ApiFormat.OPENAI_CHAT) { "ဒီ API မှာ transcription endpoint မရွေးထားပါ။" }
        require(audio.bytes.size <= 24 * 1024 * 1024) { "အသံဖိုင်ကြီးလွန်းပါတယ်။" }
        val extension = if (audio.mimeType.contains("wav")) "wav" else "m4a"
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("file", "recording.$extension", audio.bytes.toRequestBody(audio.mimeType.toMediaType()))
            .build()
        return parse(post(profile, key, "audio/transcriptions", form))["text"]?.jsonPrimitive?.contentOrNull
            ?.trim()?.takeIf(String::isNotBlank) ?: throw AiApiException("အသံမရှင်းပါ။ ထပ်ပြောပါ။")
    }

    suspend fun speech(profile: AiProfile, key: String, model: String, text: String, style: String): AudioPayload {
        require(profile.audioEndpoints && profile.format == ApiFormat.OPENAI_CHAT) { "ဒီ API မှာ speech endpoint မရွေးထားပါ။" }
        val body = buildJsonObject {
            put("model", model); put("input", text.take(4000)); put("voice", profile.voice)
            put("response_format", "wav")
            if (profile.kind == AiProviderKind.OPENAI && model.contains("mini-tts")) put("instructions", style + " Speak Korean only.")
        }
        val bytes = post(profile, key, "audio/speech", body.toString().toRequestBody(media))
        if (bytes.isEmpty()) throw AiApiException("AI returned no audio")
        return AudioPayload(bytes, "audio/wav")
    }

    suspend fun models(profile: AiProfile, key: String): List<String> {
        val request = request(profile, key, "models").get().build()
        val root = http.newCall(request).await().use {
            val bytes = it.body?.bytes() ?: byteArrayOf()
            if (!it.isSuccessful) throw failure(profile, it.code, bytes)
            parse(bytes)
        }
        return root["data"]?.jsonArray.orEmpty().mapNotNull {
            it.jsonObject["id"]?.jsonPrimitive?.contentOrNull
        }.distinct().sorted()
    }

    private fun request(profile: AiProfile, key: String, path: String): Request.Builder {
        val base = profile.baseUrl.trimEnd('/') + "/"
        val url = base.toHttpUrl().resolve(path) ?: throw AiApiException("Base URL မမှန်ပါ။")
        return Request.Builder().url(url).apply {
            if (profile.format == ApiFormat.CLAUDE_MESSAGES) {
                header("x-api-key", key); header("anthropic-version", "2023-06-01")
            } else header("Authorization", "Bearer $key")
        }
    }

    private suspend fun post(profile: AiProfile, key: String, path: String, body: okhttp3.RequestBody): ByteArray =
        http.newCall(request(profile, key, path).post(body).build()).await().use {
            val bytes = it.body?.bytes() ?: byteArrayOf()
            if (!it.isSuccessful) throw failure(profile, it.code, bytes)
            bytes
        }

    private fun parse(bytes: ByteArray): JsonObject = try { json.parseToJsonElement(bytes.decodeToString()).jsonObject }
        catch (_: Exception) { throw AiApiException("AI returned invalid structured data") }

    private fun failure(profile: AiProfile, status: Int, bytes: ByteArray): AiApiException {
        // Provider bodies may echo a key or user input. Never display or log them.
        val body = bytes.decodeToString().lowercase()
        val wholeProvider = status in setOf(401, 403) || (status == 429 &&
            listOf("insufficient_quota", "billing", "credit", "daily", "perday", "per_day").any(body::contains))
        return AiApiException("${profile.label} request failed ($status)", status, wholeProvider)
    }
}

fun validateAiProfile(profile: AiProfile) {
    require(profile.id.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "API profile ID မမှန်ပါ။" }
    require(profile.label.isNotBlank() && profile.label.length <= 80) { "API နာမည်ထည့်ပါ။" }
    val url = profile.baseUrl.toHttpUrl()
    require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
        "Base URL ကို HTTPS နဲ့ query/key မပါဘဲ ထည့်ပါ။"
    }
    if (profile.kind != AiProviderKind.CUSTOM) {
        val builtin = builtInAiProfiles().first { it.kind == profile.kind }
        require(profile.baseUrl.trimEnd('/') == builtin.baseUrl.trimEnd('/') && profile.format == builtin.format) {
            "ကိုယ်ပိုင် endpoint အတွက် Custom API ကိုရွေးပါ။"
        }
    }
    val models = listOf(profile.textModel, profile.speechModel, profile.transcribeModel, profile.liveModel) +
        profile.textBackups + profile.speechBackups
    require(models.all { it.length <= 160 && it.none(Char::isISOControl) }) { "Model ID မမှန်ပါ။" }
    require(profile.textBackups.size <= 8 && profile.speechBackups.size <= 4)
}
