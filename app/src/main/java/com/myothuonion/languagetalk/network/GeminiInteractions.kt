package com.myothuonion.languagetalk.network

import kotlinx.serialization.json.*
import java.util.Base64

/** Fresh, non-stored interactions; Room remains the source of lesson/history state. */
object GeminiInteractions {
    fun textRequest(model: String, source: JsonObject): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(GeminiModels.name(model)))
        put("store", JsonPrimitive(false))
        val instruction = source["systemInstruction"]?.jsonObject?.get("parts")?.jsonArray
            ?.joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }.orEmpty()
        if (instruction.isNotBlank()) put("system_instruction", JsonPrimitive(instruction))
        val turns = source["contents"]!!.jsonArray
        put("input", buildJsonArray {
            if (turns.size > 1) add(buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive("Previous dialogue for context only; respond to the current learner input, do not simulate the learner:\n" +
                    turns.dropLast(1).joinToString("\n") { turn ->
                        val item = turn.jsonObject
                        item["role"]!!.jsonPrimitive.content + ": " + item["parts"]!!.jsonArray.joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                    }))
            })
            turns.last().jsonObject["parts"]!!.jsonArray.forEach { item ->
                val part = item.jsonObject
                part["text"]?.let { text -> add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", text) }) }
                part["inlineData"]?.jsonObject?.let { inline ->
                    val rawMime = inline["mimeType"]!!.jsonPrimitive.content
                    val mime = if (rawMime == "audio/mp4") "audio/m4a" else rawMime
                    if (mime.startsWith("text/") && mime != "text/csv") {
                        add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(Base64.getDecoder().decode(inline["data"]!!.jsonPrimitive.content).decodeToString())) })
                    } else add(buildJsonObject {
                        put("type", JsonPrimitive(when { mime.startsWith("audio/") -> "audio"; mime.startsWith("image/") -> "image"; else -> "document" }))
                        put("mime_type", JsonPrimitive(mime)); put("data", inline["data"]!!)
                    })
                }
            }
        })
        source["generationConfig"]?.jsonObject?.let { config ->
            config["temperature"]?.let { temperature -> put("generation_config", buildJsonObject { put("temperature", temperature) }) }
            config["responseSchema"]?.let { schema -> put("response_format", buildJsonObject {
                put("type", JsonPrimitive("text")); put("mime_type", JsonPrimitive("application/json")); put("schema", schema)
            }) }
        }
    }

    fun speechRequest(model: String, text: String, voice: String, style: String) = buildJsonObject {
        put("model", JsonPrimitive(GeminiModels.name(model)))
        put("store", JsonPrimitive(false))
        put("input", buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("user_input"))
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(text))
                        put("annotations", buildJsonArray {
                            add(buildJsonObject {
                                put("type", JsonPrimitive("speech_metadata"))
                                put("style", JsonPrimitive(style))
                            })
                        })
                    })
                })
            })
        })
        // Gemini TTS rejects an explicitly selected delivery mode. Unary audio is inline by default.
        put("response_format", buildJsonObject { put("type", JsonPrimitive("audio")); put("mime_type", JsonPrimitive("audio/wav")) })
        put("generation_config", buildJsonObject { put("speech_config", buildJsonArray { add(buildJsonObject { put("voice", JsonPrimitive(voice)) }) }) })
    }

    fun output(root: JsonObject): List<JsonObject> {
        val status = root["status"]?.jsonPrimitive?.contentOrNull
        if (status != "completed") {
            val error = root["error"] as? JsonObject
            throw AiApiException(error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Gemini interaction did not complete: $status",
                error?.get("code")?.jsonPrimitive?.contentOrNull?.toIntOrNull())
        }
        return root["steps"]?.jsonArray.orEmpty().flatMap { step ->
            val item = step.jsonObject
            if (item["type"]?.jsonPrimitive?.contentOrNull == "model_output") item["content"]?.jsonArray.orEmpty().map { it.jsonObject } else emptyList()
        }
    }
}
