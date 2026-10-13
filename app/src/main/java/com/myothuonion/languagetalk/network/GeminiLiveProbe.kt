package com.myothuonion.languagetalk.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

object GeminiLiveProbe {
    suspend fun check(key: String, model: String, voice: String) {
        val result = CompletableDeferred<Unit>()
        val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()
        val url = "https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
            .toHttpUrl().newBuilder().addQueryParameter("key", key).build()
        val socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(buildJsonObject { put("setup", buildJsonObject {
                    put("model", "models/${GeminiModels.name(model)}")
                    put("generationConfig", buildJsonObject {
                        put("responseModalities", buildJsonArray { add("AUDIO") })
                        put("speechConfig", buildJsonObject { put("voiceConfig", buildJsonObject {
                            put("prebuiltVoiceConfig", buildJsonObject { put("voiceName", voice) })
                        }) })
                    })
                    put("outputAudioTranscription", buildJsonObject {})
                    put("systemInstruction", buildJsonObject { put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", "Speak Korean only. Say 안녕하세요 once and wait.") })
                    }) })
                }) }.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = Json.parseToJsonElement(text).jsonObject
                    if (root.containsKey("setupComplete")) webSocket.send(buildJsonObject { put("clientContent", buildJsonObject {
                        put("turns", buildJsonArray { add(buildJsonObject { put("role", "user"); put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", "Say 안녕하세요 once.") })
                        }) }) }); put("turnComplete", true)
                    }) }.toString())
                    val parts = root["serverContent"]?.jsonObject?.get("modelTurn")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
                    if (parts.any { !it.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull.isNullOrBlank() }) result.complete(Unit)
                    if (root.containsKey("error")) result.completeExceptionally(AiApiException("Gemini Live setup မအောင်မြင်ပါ။"))
                } catch (_: Exception) { result.completeExceptionally(AiApiException("Live အဖြေပုံစံ မမှန်ပါ။")) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                result.completeExceptionally(response?.let { ApiErrors.failure("Gemini Live", it.code, "") }
                    ?: AiApiException("Live ချိတ်ဆက်မှု မရပါ။ Internet ကို စစ်ပါ။"))
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) result.completeExceptionally(AiApiException("Live setup ပိတ်သွားပါတယ် ($code)။"))
            }
        })
        try { withTimeout(30_000) { result.await() } }
        finally { socket.close(1000, "Connection test finished"); http.dispatcher.executorService.shutdown() }
    }
}
