package com.myothuonion.languagetalk.network

import android.util.Base64
import com.myothuonion.languagetalk.data.MessageEntity
import com.myothuonion.languagetalk.model.TutorReply
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiClient(private val http: OkHttpClient = defaultHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun tutorReply(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<MessageEntity>,
        userText: String,
        audio: AudioPayload? = null
    ): TutorReply {
        requireKey(apiKey)
        val contents = buildJsonArray {
            history.sortedBy { it.createdAt }.takeLast(16).forEach { message ->
                add(buildJsonObject {
                    put("role", JsonPrimitive(if (message.role == "USER") "user" else "model"))
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(message.content)) })
                    })
                })
            }
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("parts", buildJsonArray {
                    if (audio != null) {
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", JsonPrimitive(audio.mimeType))
                                put("data", JsonPrimitive(Base64.encodeToString(audio.bytes, Base64.NO_WRAP)))
                            })
                        })
                        add(buildJsonObject {
                            put("text", JsonPrimitive("Listen to my audio. Treat its transcription as my message. $userText"))
                        })
                    } else {
                        add(buildJsonObject { put("text", JsonPrimitive(userText)) })
                    }
                })
            })
        }

        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", JsonPrimitive(systemInstruction)) })
                })
            })
            put("contents", contents)
            put("generationConfig", buildJsonObject {
                put("temperature", JsonPrimitive(0.7))
                put("responseMimeType", JsonPrimitive("application/json"))
                put("responseSchema", tutorReplySchema())
            })
        }

        val text = postGenerate(apiKey, model, body)
        return parseTutorReply(text)
    }

    suspend fun finalizeWithReview(
        apiKey: String,
        model: String,
        systemInstruction: String,
        userText: String,
        draft: TutorReply,
        review: String
    ): TutorReply {
        val prompt = """
            Learner message: $userText
            Gemini draft: ${json.encodeToString(JsonObject.serializer(), replyToJson(draft))}
            Independent NVIDIA review: $review

            Produce the final tutoring response. Keep what is correct, fix only genuine issues,
            and return the required JSON fields. Never mention model names or the review process.
        """.trimIndent()
        return tutorReply(apiKey, model, systemInstruction, emptyList(), prompt)
    }

    suspend fun summarizeSource(
        apiKey: String,
        model: String,
        name: String,
        mimeType: String,
        bytes: ByteArray
    ): String {
        requireKey(apiKey)
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", JsonPrimitive(mimeType))
                                put("data", JsonPrimitive(Base64.encodeToString(bytes, Base64.NO_WRAP)))
                            })
                        })
                        add(buildJsonObject {
                            put("text", JsonPrimitive("Extract and summarize '$name' for a personal Korean/English tutor. Preserve names, dates, wages, schedules, rules, vocabulary, and Korean phrases. Write a compact factual summary in Myanmar with important Korean text retained."))
                        })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", JsonPrimitive(0.2))
            })
        }
        return postGenerate(apiKey, model, body)
    }

    suspend fun synthesize(
        apiKey: String,
        model: String,
        text: String,
        voiceName: String,
        style: String
    ): AudioPayload {
        requireKey(apiKey)
        val styledText = "Voice direction: $style\n\nSpeak this text naturally:\n$text"
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(styledText)) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray { add(JsonPrimitive("AUDIO")) })
                put("speechConfig", buildJsonObject {
                    put("voiceConfig", buildJsonObject {
                        put("prebuiltVoiceConfig", buildJsonObject {
                            put("voiceName", JsonPrimitive(voiceName))
                        })
                    })
                })
            })
        }
        val root = execute(apiKey, model, body)
        val part = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.firstOrNull { it.jsonObject["inlineData"] != null }?.jsonObject
            ?.get("inlineData")?.jsonObject
            ?: throw AiApiException("Gemini TTS returned no audio")
        val data = part["data"]?.jsonPrimitive?.contentOrNull
            ?: throw AiApiException("Gemini TTS audio data is missing")
        val mime = part["mimeType"]?.jsonPrimitive?.contentOrNull
            ?: "audio/L16;codec=pcm;rate=24000"
        return AudioPayload(Base64.decode(data, Base64.DEFAULT), mime)
    }

    private suspend fun postGenerate(apiKey: String, model: String, body: JsonObject): String {
        val root = execute(apiKey, model, body)
        return root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?: throw AiApiException("Gemini returned an empty response")
    }

    private suspend fun execute(apiKey: String, model: String, body: JsonObject): JsonObject {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(mediaType))
            .build()
        http.newCall(request).await().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val apiMessage = runCatching {
                    json.parseToJsonElement(responseBody).jsonObject["error"]?.jsonObject
                        ?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                throw AiApiException(apiMessage ?: "Gemini request failed (${response.code})", response.code)
            }
            return json.parseToJsonElement(responseBody).jsonObject
        }
    }

    private fun parseTutorReply(raw: String): TutorReply {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching {
            val obj = json.parseToJsonElement(clean).jsonObject
            TutorReply(
                reply = obj.string("reply").ifBlank { clean },
                translation = obj.string("translation"),
                correction = obj.string("correction"),
                explanation = obj.string("explanation"),
                followUpQuestion = obj.string("followUpQuestion")
            )
        }.getOrElse { TutorReply(reply = raw) }
    }

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun replyToJson(reply: TutorReply) = buildJsonObject {
        put("reply", JsonPrimitive(reply.reply))
        put("translation", JsonPrimitive(reply.translation))
        put("correction", JsonPrimitive(reply.correction))
        put("explanation", JsonPrimitive(reply.explanation))
        put("followUpQuestion", JsonPrimitive(reply.followUpQuestion))
    }

    private fun tutorReplySchema() = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            listOf("reply", "translation", "correction", "explanation", "followUpQuestion").forEach { key ->
                put(key, buildJsonObject { put("type", JsonPrimitive("string")) })
            }
        })
        put("required", buildJsonArray {
            listOf("reply", "translation", "correction", "explanation", "followUpQuestion")
                .forEach { add(JsonPrimitive(it)) }
        })
    }

    private fun requireKey(key: String) {
        if (key.isBlank()) throw AiApiException("Settings ထဲတွင် Gemini API key ထည့်ပါ")
    }

    companion object {
        fun defaultHttpClient() = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}
