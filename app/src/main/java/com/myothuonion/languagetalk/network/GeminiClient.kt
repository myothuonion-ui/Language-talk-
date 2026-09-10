package com.myothuonion.languagetalk.network

import android.util.Base64
import com.myothuonion.languagetalk.data.MessageEntity
import com.myothuonion.languagetalk.model.KoreanNameCandidate
import com.myothuonion.languagetalk.model.KoreanNameResult
import com.myothuonion.languagetalk.model.TranslationResult
import com.myothuonion.languagetalk.model.TutorReply
import com.myothuonion.languagetalk.model.decorateKoreanNameCandidates
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
                            put("text", JsonPrimitive("Listen carefully to my audio. Put the exact transcript in heardText, then treat it as my message. $userText"))
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
            Gemini draft: ${replyToJson(draft)}
            Independent NVIDIA review: $review

            Produce the final tutoring response. Keep what is correct, fix only genuine issues,
            and return the required JSON fields. Never mention model names or the review process.
        """.trimIndent()
        return tutorReply(apiKey, model, systemInstruction, emptyList(), prompt)
    }

    suspend fun listModels(apiKey: String): List<String> {
        requireKey(apiKey)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey&pageSize=100")
            .get()
            .build()
        http.newCall(request).await().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw apiException(responseBody, response.code, "Gemini key check failed")
            }
            return json.parseToJsonElement(responseBody).jsonObject["models"]?.jsonArray
                ?.mapNotNull { model ->
                    model.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
                }.orEmpty()
        }
    }

    suspend fun createKoreanNames(apiKey: String, model: String, originalName: String): KoreanNameResult {
        requireKey(apiKey)
        val prompt = """
            Convert the Myanmar personal name below into exactly 3 useful Korean Hangul name spellings.
            Original Myanmar name: $originalName

            Mandatory rules:
            - The `hangul` value must contain Korean Hangul only. Never copy Myanmar script into `hangul`.
            - Preserve pronunciation rather than translating the person's identity.
            - Explain plain, tense, and aspirated consonant differences precisely when relevant (for example 두 / 뚜 / 투).
            - Make all 3 candidates meaningfully different and rank the most sound-faithful first.
            - Hangul spelling alone does not guarantee a literal meaning. `hanjaInspiredMeaning` must be clearly phrased as a possible creative Hanja-inspired identity meaning, not a factual/legal translation.
            - Write explanations, pronunciation and meaning in Myanmar. Keep romanization in Latin characters.
            - Keep every field concise. Do not design cards; the app creates all card visuals locally.
        """.trimIndent()
        val body = structuredRequest(prompt, koreanNameSchema(), temperature = 0.82)
        val raw = postGenerate(apiKey, model, body)
        val root = parseObject(raw)
        val candidates = root["candidates"]?.jsonArray?.mapNotNull { element ->
            runCatching {
                val item = element.jsonObject
                KoreanNameCandidate(
                    hangul = item.string("hangul").replace(" ", "").trim(),
                    romanization = item.string("romanization"),
                    myanmarPronunciation = item.string("myanmarPronunciation"),
                    soundNotes = item.string("soundNotes"),
                    naturalnessScore = item["naturalnessScore"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
                    hanjaInspiredMeaning = item.string("hanjaInspiredMeaning"),
                    vibe = "",
                    animal = "",
                    animalSymbolism = "",
                    primaryColor = "",
                    secondaryColor = "",
                    lightPattern = "",
                    layoutStyle = "",
                    motto = "",
                    externalImagePrompt = ""
                )
            }.getOrNull()
        }.orEmpty().filter { HANGUL_NAME.matches(it.hangul) }
        if (candidates.size < 3) {
            throw AiApiException("Gemini returned an invalid Hangul name result")
        }
        return KoreanNameResult(
            originalName = originalName,
            candidates = decorateKoreanNameCandidates(
                originalName,
                candidates.sortedByDescending { it.naturalnessScore }.take(3)
            )
        )
    }

    suspend fun quickTranslate(
        apiKey: String,
        model: String,
        text: String,
        audio: AudioPayload? = null
    ): TranslationResult {
        requireKey(apiKey)
        val instruction = """
            Translate the supplied Korean, English, or Myanmar utterance quickly and accurately.
            Put the direct Myanmar meaning first. Preserve the original wording.
            For Korean, include an easy Myanmar pronunciation, useful word/particle breakdown, and one concise grammar note.
            For English or Myanmar, leave pronunciation/grammar fields empty when they add no value.
            Never add a greeting, follow-up question, or unrelated teaching filler.
        """.trimIndent()
        val parts = buildJsonArray {
            if (audio != null) {
                add(buildJsonObject {
                    put("inlineData", buildJsonObject {
                        put("mimeType", JsonPrimitive(audio.mimeType))
                        put("data", JsonPrimitive(Base64.encodeToString(audio.bytes, Base64.NO_WRAP)))
                    })
                })
                add(buildJsonObject { put("text", JsonPrimitive("$instruction\nTranscribe and translate this voice recording.")) })
            } else {
                add(buildJsonObject { put("text", JsonPrimitive("$instruction\nText to translate:\n$text")) })
            }
        }
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("parts", parts)
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", JsonPrimitive(0.15))
                put("responseMimeType", JsonPrimitive("application/json"))
                put("responseSchema", translationSchema())
            })
        }
        val root = parseObject(postGenerate(apiKey, model, body))
        val result = TranslationResult(
            detectedLanguage = root.string("detectedLanguage"),
            originalText = root.string("originalText").ifBlank { text },
            myanmarMeaning = root.string("myanmarMeaning"),
            naturalTranslation = root.string("naturalTranslation"),
            pronunciation = root.string("pronunciation"),
            wordBreakdown = root.string("wordBreakdown"),
            grammarNote = root.string("grammarNote")
        )
        if (result.myanmarMeaning.isBlank()) throw AiApiException("Gemini returned an empty translation")
        return result
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
                throw apiException(responseBody, response.code, "Gemini request failed")
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
                heardText = obj.string("heardText"),
                translation = obj.string("translation"),
                correction = obj.string("correction"),
                explanation = obj.string("explanation"),
                followUpQuestion = obj.string("followUpQuestion")
            )
        }.getOrElse { TutorReply(reply = raw) }
    }

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun parseObject(raw: String): JsonObject {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { json.parseToJsonElement(clean).jsonObject }
            .getOrElse { throw AiApiException("Gemini returned invalid structured data") }
    }

    private fun structuredRequest(prompt: String, schema: JsonObject, temperature: Double): JsonObject = buildJsonObject {
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("parts", buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(prompt)) }) })
            })
        })
        put("generationConfig", buildJsonObject {
            put("temperature", JsonPrimitive(temperature))
            put("responseMimeType", JsonPrimitive("application/json"))
            put("responseSchema", schema)
        })
    }

    private fun apiException(body: String, statusCode: Int, fallback: String): AiApiException {
        val apiMessage = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return AiApiException(apiMessage ?: "$fallback ($statusCode)", statusCode)
    }

    private fun replyToJson(reply: TutorReply) = buildJsonObject {
        put("reply", JsonPrimitive(reply.reply))
        put("heardText", JsonPrimitive(reply.heardText))
        put("translation", JsonPrimitive(reply.translation))
        put("correction", JsonPrimitive(reply.correction))
        put("explanation", JsonPrimitive(reply.explanation))
        put("followUpQuestion", JsonPrimitive(reply.followUpQuestion))
    }

    private fun tutorReplySchema() = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            listOf("reply", "heardText", "translation", "correction", "explanation", "followUpQuestion").forEach { key ->
                put(key, buildJsonObject { put("type", JsonPrimitive("string")) })
            }
        })
        put("required", buildJsonArray {
            listOf("reply", "heardText", "translation", "correction", "explanation", "followUpQuestion")
                .forEach { add(JsonPrimitive(it)) }
        })
    }

    private fun koreanNameSchema() = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            put("candidates", buildJsonObject {
                put("type", JsonPrimitive("array"))
                put("minItems", JsonPrimitive(3))
                put("maxItems", JsonPrimitive(3))
                put("items", buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        listOf(
                            "hangul", "romanization", "myanmarPronunciation", "soundNotes", "hanjaInspiredMeaning"
                        ).forEach { put(it, buildJsonObject { put("type", JsonPrimitive("string")) }) }
                        put("naturalnessScore", buildJsonObject { put("type", JsonPrimitive("integer")) })
                    })
                    put("required", buildJsonArray {
                        listOf(
                            "hangul", "romanization", "myanmarPronunciation", "soundNotes", "naturalnessScore",
                            "hanjaInspiredMeaning"
                        ).forEach { add(JsonPrimitive(it)) }
                    })
                })
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("candidates")) })
    }

    private fun translationSchema() = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            listOf(
                "detectedLanguage", "originalText", "myanmarMeaning", "naturalTranslation",
                "pronunciation", "wordBreakdown", "grammarNote"
            ).forEach { put(it, buildJsonObject { put("type", JsonPrimitive("string")) }) }
        })
        put("required", buildJsonArray {
            listOf(
                "detectedLanguage", "originalText", "myanmarMeaning", "naturalTranslation",
                "pronunciation", "wordBreakdown", "grammarNote"
            ).forEach { add(JsonPrimitive(it)) }
        })
    }

    private fun requireKey(key: String) {
        if (key.isBlank()) throw AiApiException("Settings ထဲတွင် Gemini API key ထည့်ပါ")
    }

    companion object {
        private val HANGUL_NAME = Regex("^[가-힣·-]+$")
        fun defaultHttpClient() = OkHttpClient.Builder()
            .callTimeout(35, TimeUnit.SECONDS)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .build()
    }
}
