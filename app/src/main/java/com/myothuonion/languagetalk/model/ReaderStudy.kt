package com.myothuonion.languagetalk.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun valid() = listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f } && left < right && top < bottom
}

@Serializable
data class SourceBlock(val id: String, val text: String, val rectangles: List<PageRect> = emptyList())

@Serializable
data class TeachingSentence(val sourceId: String, val korean: String, val meaning: String)

@Serializable
data class TeachingWord(val sourceId: String, val word: String, val lemma: String, val meaning: String, val usage: String = "")

@Serializable
data class TeachingGrammar(val sourceId: String, val pattern: String, val meaning: String, val construction: String,
    val originalExample: String = "", val explanation: String = "", val commonError: String = "", val practice: String = "")

@Serializable
data class TeachingExercise(val sourceId: String, val question: String, val hint: String, val answer: String)

@Serializable
data class AddedPractice(val question: String, val hint: String = "", val answer: String = "")

@Serializable
data class PageTeaching(val overview: String, val sentences: List<TeachingSentence> = emptyList(),
    val vocabulary: List<TeachingWord> = emptyList(), val grammar: List<TeachingGrammar> = emptyList(),
    val notes: List<String> = emptyList(), val exercises: List<TeachingExercise> = emptyList(),
    val addedPractice: List<AddedPractice> = emptyList(), val unreadable: List<String> = emptyList())

/** Reject invented source anchors; additional exercises have their own explicitly labelled field. */
object PageTeachingCodec {
    const val VERSION = 1
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun decode(raw: String, blocks: List<SourceBlock>): PageTeaching {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        require(clean.length <= 60000) { "စာမျက်နှာရှင်းပြချက် ရှည်လွန်းပါတယ်။" }
        val value = json.decodeFromString<PageTeaching>(clean)
        require(blocks.size in 1..160 && blocks.all { it.id.length <= 80 && it.text.length <= 18000 &&
            it.rectangles.size <= 250 && it.rectangles.all { region -> region.valid() } })
        val sources = blocks.associateBy { it.id }
        fun source(id: String) = sources[id]?.text ?: error("AI source နေရာ မမှန်ပါ။ ပြန်စမ်းပါ။")
        require(value.overview.isNotBlank() && value.overview.length <= 4000)
        require(value.sentences.size <= 160 && value.vocabulary.size <= 120 && value.grammar.size <= 24 &&
            value.exercises.size <= 60 && value.addedPractice.size <= 10 && value.notes.size <= 30 && value.unreadable.size <= 30)
        value.sentences.forEach { require(it.korean.trim() == source(it.sourceId).trim() && it.meaning.length <= 4000) }
        value.vocabulary.forEach { require(source(it.sourceId).contains(it.word) && it.word.isNotBlank() && it.meaning.length <= 2000) }
        value.grammar.forEach { require(it.originalExample.isBlank() || source(it.sourceId).contains(it.originalExample)); require(it.pattern.length <= 200) }
        value.exercises.forEach { require(source(it.sourceId).contains(it.question) && it.question.isNotBlank()) }
        val covered = (value.sentences.map { it.sourceId } + value.vocabulary.map { it.sourceId } +
            value.grammar.map { it.sourceId } + value.exercises.map { it.sourceId }).toSet()
        require(blocks.all { it.id in covered }) { "စာမျက်နှာထဲက အချို့စာသားတွေ မရှင်းပြရသေးပါ။ ပြန်စမ်းပါ။" }
        require(value.sentences.isNotEmpty() || value.vocabulary.isNotEmpty() || value.grammar.isNotEmpty() || value.exercises.isNotEmpty()) {
            "မူရင်းစာသားနဲ့ ချိတ်ထားတဲ့ ရှင်းပြချက် မရပါ။ ထပ်စမ်းပါ။"
        }
        return value
    }
}

/** Keep quote anchors usable when native text and OCR differ only in whitespace. */
object ReaderAnchors {
    fun range(text: String, quote: String, preferred: Int = -1): IntRange? {
        if (preferred >= 0 && preferred + quote.length <= text.length && text.substring(preferred, preferred + quote.length) == quote)
            return preferred until preferred + quote.length
        val positions = text.indices.filter { !text[it].isWhitespace() }
        val compact = positions.map { text[it] }.joinToString("")
        val needle = quote.filterNot(Char::isWhitespace)
        if (needle.isBlank()) return null
        val offset = if (preferred < 0) compact.indexOf(needle) else Regex(Regex.escape(needle)).findAll(compact)
            .minByOrNull { kotlin.math.abs(positions[it.range.first] - preferred) }?.range?.first ?: -1
        if (offset < 0) return null
        return positions[offset]..positions[offset + needle.length - 1]
    }
}
