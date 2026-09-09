package com.myothuonion.languagetalk.network

import com.myothuonion.languagetalk.data.MessageEntity
import kotlinx.serialization.json.Json
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

class NvidiaClient(private val http: OkHttpClient = GeminiClient.defaultHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun review(
        apiKey: String,
        model: String,
        systemInstruction: String,
        history: List<MessageEntity>,
        userText: String,
        draft: String? = null
    ): String {
        if (apiKey.isBlank()) throw AiApiException("Settings ထဲတွင် NVIDIA API key ထည့်ပါ")
        val reviewPrompt = buildString {
            append(userText)
            if (!draft.isNullOrBlank()) {
                append("\n\nReview this proposed tutor answer for Korean/English accuracy and teaching value:\n")
                append(draft)
            }
            append("\n\nReturn a concise final answer or review. Do not expose hidden reasoning.")
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", JsonPrimitive(systemInstruction))
                })
                history.sortedBy { it.createdAt }.takeLast(12).forEach { message ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(if (message.role == "USER") "user" else "assistant"))
                        put("content", JsonPrimitive(message.content))
                    })
                }
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(reviewPrompt))
                })
            })
            put("temperature", JsonPrimitive(1.0))
            put("top_p", JsonPrimitive(0.95))
            put("max_tokens", JsonPrimitive(4096))
            put("chat_template_kwargs", buildJsonObject {
                put("enable_thinking", JsonPrimitive(true))
            })
        }
        val request = Request.Builder()
            .url("https://integrate.api.nvidia.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).await().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    json.parseToJsonElement(responseBody).jsonObject["detail"]?.jsonPrimitive?.content
                }.getOrNull()
                throw AiApiException(message ?: "NVIDIA request failed (${response.code})", response.code)
            }
            return json.parseToJsonElement(responseBody).jsonObject["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull
                ?: throw AiApiException("NVIDIA returned an empty response")
        }
    }
}
