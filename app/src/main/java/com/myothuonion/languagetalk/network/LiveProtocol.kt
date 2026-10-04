package com.myothuonion.languagetalk.network

import com.myothuonion.languagetalk.model.LiveSessionConfig
import kotlinx.serialization.json.*

object LiveProtocol {
    fun setup(config: LiveSessionConfig, model: String, handle: String? = null): JsonObject = buildJsonObject {
        put("setup", buildJsonObject {
            put("model", JsonPrimitive("models/$model"))
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray { add(JsonPrimitive("AUDIO")) })
                put("speechConfig", buildJsonObject {
                    put("voiceConfig", buildJsonObject {
                        put("prebuiltVoiceConfig", buildJsonObject { put("voiceName", JsonPrimitive(config.voiceName)) })
                    })
                })
            })
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(config.systemInstruction)) }) })
            })
            put("inputAudioTranscription", buildJsonObject {})
            put("outputAudioTranscription", buildJsonObject {})
            put("realtimeInputConfig", buildJsonObject {
                put("activityHandling", JsonPrimitive("START_OF_ACTIVITY_INTERRUPTS"))
                put("automaticActivityDetection", buildJsonObject {
                    put("silenceDurationMs", JsonPrimitive(config.silenceMs.coerceIn(800, 4000)))
                    put("prefixPaddingMs", JsonPrimitive(200))
                })
            })
            put("sessionResumption", buildJsonObject { if (!handle.isNullOrBlank()) put("handle", JsonPrimitive(handle)) })
            put("contextWindowCompression", buildJsonObject { put("slidingWindow", buildJsonObject {}) })
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("functionDeclarations", buildJsonArray {
                        add(buildJsonObject {
                            put("name", JsonPrimitive("update_learning_progress"))
                            put("behavior", JsonPrimitive("BLOCKING"))
                            put("description", JsonPrimitive("Record ONE learner assessment, return the authoritative next lesson step. Never score pronunciation from text alone."))
                            put("parameters", buildJsonObject {
                                put("type", JsonPrimitive("OBJECT"))
                                put("properties", buildJsonObject {
                                    listOf("heardText", "targetSentence", "assessment", "correction", "lessonNote", "voiceCommand", "memoryFact", "memoryEvidence", "nextTargetSentence", "answerPattern", "answerExample", "answerHint").forEach {
                                        put(it, buildJsonObject { put("type", JsonPrimitive("STRING")) })
                                    }
                                })
                                put("required", buildJsonArray {
                                    listOf("heardText", "targetSentence", "assessment", "correction", "lessonNote", "voiceCommand").forEach { add(JsonPrimitive(it)) }
                                })
                            })
                        })
                    })
                })
            })
        })
    }

    fun mergeTranscript(current: String, next: String): String = when {
        current.isBlank() -> next
        next.startsWith(current) -> next
        current == next -> current
        else -> current + next
    }

    fun continuation(turns: List<Pair<String, String>>, prompt: String): JsonObject = buildJsonObject {
        put("clientContent", buildJsonObject {
            put("turns", buildJsonArray {
                (turns.takeLast(16) + ("user" to prompt)).forEach { (role, text) ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(role))
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(text)) }) })
                    })
                }
            })
            put("turnComplete", JsonPrimitive(true))
        })
    }
}
