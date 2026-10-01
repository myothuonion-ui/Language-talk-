package com.myothuonion.languagetalk.network

enum class GeminiTask { TEXT, SPEECH, LIVE }

data class GeminiModel(val name: String, val methods: Set<String> = emptySet())

object GeminiModels {
    const val TEXT = "gemini-3.8-flash"
    const val SPEECH = "gemini-3.8-flash-tts"
    const val LIVE = "gemini-3.8-live"

    fun defaults(task: GeminiTask): List<String> = when (task) {
        GeminiTask.TEXT -> listOf(TEXT, "gemini-3.7-flash", "gemini-3.5-flash-lite")
        GeminiTask.SPEECH -> listOf(SPEECH, "gemini-3.8-flash-lite-tts")
        GeminiTask.LIVE -> listOf(LIVE, "gemini-3.8-live-extended-thinking")
    }

    fun name(value: String) = value.trim().removePrefix("models/")

    private fun legacy(value: String) = value.startsWith("gemini-1.") || value.startsWith("gemini-2.") ||
        value == "gemini-3.1-flash-live-preview" || value == "gemini-3.1-flash-tts-preview"

    private fun matches(value: String, task: GeminiTask): Boolean = when (task) {
        GeminiTask.SPEECH -> value.contains("tts")
        GeminiTask.LIVE -> value.contains("live") && !value.contains("translate")
        GeminiTask.TEXT -> value.startsWith("gemini-") && listOf("tts", "live", "image", "embedding", "robotics", "transcribe").none(value::contains)
    }

    fun normalize(value: String, task: GeminiTask): String = name(value).takeIf {
        it.matches(Regex("[A-Za-z0-9._-]{1,160}")) && !legacy(it) && matches(it, task)
    } ?: defaults(task).first()

    fun candidates(requested: String, task: GeminiTask, catalog: List<GeminiModel>): List<String> {
        val available = catalog.filter { model ->
            val value = name(model.name)
            !legacy(value) && matches(value, task) && (model.methods.isEmpty() || when (task) {
                GeminiTask.LIVE -> model.methods.any { it.equals("bidiGenerateContent", true) }
                else -> model.methods.any { it.equals("generateContent", true) || it.contains("interaction", true) }
            })
        }.map { name(it.name) }.toSet()
        // Do not guess endpoints or fall back to an unlisted, legacy model.
        return (listOf(normalize(requested, task)) + defaults(task)).distinct().filter { it in available }
    }
}
