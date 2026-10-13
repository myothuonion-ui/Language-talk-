package com.myothuonion.languagetalk.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*

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
    const val VERSION = 2
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun schema(blocks: List<SourceBlock>): JsonObject {
        fun string() = buildJsonObject { put("type", "string") }
        fun item(vararg names: String) = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { names.forEach { name ->
                put(name, if (name == "sourceId") buildJsonObject {
                    put("type", "string"); put("enum", buildJsonArray { blocks.forEach { add(it.id) } })
                } else string())
            } })
            put("required", buildJsonArray { names.forEach { add(it) } })
        }
        fun array(value: JsonObject) = buildJsonObject { put("type", "array"); put("items", value) }
        return buildJsonObject {
            put("type", "object"); put("properties", buildJsonObject {
                put("overview", string())
                put("sentences", array(item("sourceId", "korean", "meaning")))
                put("vocabulary", array(item("sourceId", "word", "lemma", "meaning", "usage")))
                put("grammar", array(item("sourceId", "pattern", "meaning", "construction", "originalExample", "explanation", "commonError", "practice")))
                put("exercises", array(item("sourceId", "question", "hint", "answer")))
                put("addedPractice", array(item("question", "hint", "answer")))
                listOf("notes", "unreadable").forEach { put(it, array(string())) }
            })
            put("required", buildJsonArray { listOf("overview", "sentences", "vocabulary", "grammar", "exercises", "addedPractice", "notes", "unreadable").forEach { add(it) } })
        }
    }
    fun covered(value: PageTeaching): Set<String> = (value.sentences.map { it.sourceId } + value.vocabulary.map { it.sourceId } +
        value.grammar.map { it.sourceId } + value.exercises.map { it.sourceId }).toSet()
    fun missing(value: PageTeaching, blocks: List<SourceBlock>) = blocks.filter { block ->
        value.sentences.none { it.sourceId == block.id } &&
        value.vocabulary.none { it.sourceId == block.id && it.word.trim() == block.text.trim() } &&
        value.grammar.none { it.sourceId == block.id && it.originalExample.trim() == block.text.trim() } &&
        value.exercises.none { it.sourceId == block.id && it.question.trim() == block.text.trim() }
    }
    /** Retain independently grounded sections; rejected anchors never become quotations. */
    fun decodePartial(raw: String, blocks: List<SourceBlock>): PageTeaching {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        require(clean.length <= 60000) { "AI အဖြေ ရှည်လွန်းပါတယ်။" }
        val value = try { json.decodeFromString<PageTeaching>(clean) }
            catch (_: Exception) { throw IllegalArgumentException("AI အဖြေပုံစံ မမှန်ပါ။ နောက် model နဲ့ ပြန်စမ်းမယ်။") }
        val sources = blocks.associateBy { it.id }
        fun same(a: String, b: String) = a.filterNot(Char::isWhitespace) == b.filterNot(Char::isWhitespace)
        fun original(id: String, quote: String) = sources[id]?.text?.let { text ->
            ReaderAnchors.range(text, quote)?.let { text.substring(it.first, it.last + 1) }
        }
        val result = value.copy(overview = value.overview.take(4000),
            sentences = value.sentences.take(160).mapNotNull { line -> sources[line.sourceId]?.takeIf {
                same(it.text, line.korean) && line.meaning.isNotBlank() }?.let { line.copy(korean = it.text, meaning = line.meaning.take(4000)) } },
            vocabulary = value.vocabulary.take(120).mapNotNull { word -> original(word.sourceId, word.word)?.takeIf {
                word.word.isNotBlank() && word.meaning.isNotBlank() }?.let { word.copy(word = it, meaning = word.meaning.take(2000)) } },
            grammar = value.grammar.take(24).mapNotNull { grammar -> if (grammar.sourceId !in sources || grammar.pattern.isBlank() ||
                grammar.pattern.length > 200) null else if (grammar.originalExample.isBlank()) grammar else
                original(grammar.sourceId, grammar.originalExample)?.let { grammar.copy(originalExample = it) } },
            exercises = value.exercises.take(60).mapNotNull { exercise -> original(exercise.sourceId, exercise.question)?.takeIf {
                exercise.question.isNotBlank() }?.let { exercise.copy(question = it) } },
            addedPractice = value.addedPractice.take(3), notes = value.notes.take(30).map { it.take(2000) },
            unreadable = value.unreadable.take(30).map { it.take(2000) })
        require(result.overview.isNotBlank() && covered(result).isNotEmpty()) { "မူရင်းစာနဲ့ ချိတ်ထားတဲ့ AI ရှင်းပြချက် မရသေးပါ။" }
        fun myanmar(text: String) = text.any { it in '\u1000'..'\u102A' }
        require(myanmar(result.overview) && (result.sentences.map { it.meaning } + result.vocabulary.map { it.meaning } +
            result.grammar.map { it.meaning }).all(::myanmar)) { "AI ရှင်းပြချက်ကို မြန်မာလို မရသေးပါ။ အခြား model နဲ့ စမ်းပါ။" }
        return result
    }
    fun merge(a: PageTeaching?, b: PageTeaching): PageTeaching = if (a == null) b else a.copy(
        overview = listOf(a.overview, b.overview).distinct().joinToString("\n\n").take(4000),
        sentences = (a.sentences + b.sentences).distinctBy { it.sourceId }.take(160),
        vocabulary = (a.vocabulary + b.vocabulary).distinctBy { it.sourceId + "|" + it.word }.take(120),
        grammar = (a.grammar + b.grammar).distinctBy { it.sourceId + "|" + it.pattern }.take(24),
        exercises = (a.exercises + b.exercises).distinctBy { it.sourceId + "|" + it.question }.take(60),
        addedPractice = (a.addedPractice + b.addedPractice).distinctBy { it.question }.take(3),
        notes = (a.notes + b.notes).distinct().take(30), unreadable = (a.unreadable + b.unreadable).distinct().take(30))
    fun decode(raw: String, blocks: List<SourceBlock>, requireComplete: Boolean = true): PageTeaching {
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
        value.grammar.forEach { val original = source(it.sourceId); require(it.originalExample.isBlank() || original.contains(it.originalExample)); require(it.pattern.length <= 200) }
        value.exercises.forEach { require(source(it.sourceId).contains(it.question) && it.question.isNotBlank()) }
        val covered = (value.sentences.map { it.sourceId } + value.vocabulary.map { it.sourceId } +
            value.grammar.map { it.sourceId } + value.exercises.map { it.sourceId }).toSet()
        require(!requireComplete || blocks.all { it.id in covered }) { "စာမျက်နှာထဲက အချို့စာသားတွေ မရှင်းပြရသေးပါ။ ပြန်စမ်းပါ။" }
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
