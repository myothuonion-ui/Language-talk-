package com.myothuonion.languagetalk.model

import java.text.Normalizer
import kotlinx.serialization.Serializable

@Serializable
data class BookCourse(val id: String, val title: String, val level: String, val version: String,
    val pdfAsset: String, val pdfSha256: String, val pageCount: Int, val chapters: List<BookChapter>)

@Serializable
data class BookChapter(val number: Int, val title: String, val category: String,
    val startPage: Int, val endPage: Int, val sections: List<BookSection>)

@Serializable
data class BookSection(val id: String, val label: String, val kind: String, val sourceText: String,
    val pages: List<Int>, val turns: List<BookTurn> = emptyList(),
    val exercises: List<BookExercise> = emptyList(), val answerText: String = "", val sourceNote: String = "") {
    val roles: List<String> get() = turns.flatMap { it.speaker.split(',').map(String::trim) }.distinct()
}

@Serializable
data class BookTurn(val speaker: String, val text: String, val english: String, val page: Int)

@Serializable
data class BookExercise(val number: Int, val prompt: String, val answer: String, val page: Int)

@Serializable
data class BookMistake(val key: String, val target: String, val heard: String, val feedback: String,
    val page: Int, val attempts: Int = 1, val successes: Int = 0)

@Serializable
data class BookCheckpoint(val chapter: Int, val section: Int, val activity: Int, val role: String,
    val awaitingNext: Boolean = false, val studied: Boolean = false, val heard: String = "",
    val feedback: String = "", val lastAssistantText: String = "", val lastAssistantCaption: String = "")

@Serializable
data class BookProgress(val bookId: String, val version: String = "user-pdf-1", val chapter: Int = 1,
    val section: Int = 0, val activity: Int = 0, val role: String = "", val slow: Boolean = true,
    val completedSections: List<String> = emptyList(), val completedChapters: List<Int> = emptyList(),
    val lastAssistantText: String = "", val lastAssistantCaption: String = "", val heard: String = "",
    val feedback: String = "", val mistakes: List<BookMistake> = emptyList(),
    val awaitingNext: Boolean = false, val studied: Boolean = false,
    val checkpoints: List<BookCheckpoint> = emptyList())

@Serializable
data class BookExplanation(val key: String, val text: String)

@Serializable
data class BookShelfState(val progress: List<BookProgress> = emptyList(),
    val explanations: List<BookExplanation> = emptyList(), val lastBookId: String = "ttmik-beginner")

data class BookOpening(val progress: BookProgress, val text: String, val caption: String)
data class BookTarget(val text: String, val prompt: String, val page: Int)

/** The source, turn owner and progression belong to the app, never to a generated reply. */
object BookEngine {
    fun chapter(book: BookCourse, state: BookProgress) = book.chapters.first { it.number == state.chapter }
    fun section(book: BookCourse, state: BookProgress) = chapter(book, state).sections[state.section]
    fun key(state: BookProgress, section: BookSection) = "${state.bookId}/${state.version}/${state.chapter}/${section.id}"
    fun role(section: BookSection, state: BookProgress) = state.role.takeIf { it in section.roles }
        ?: section.roles.getOrElse(1) { section.roles.firstOrNull().orEmpty() }
    fun owns(turn: BookTurn, role: String) = role in turn.speaker.split(',').map(String::trim)

    fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    fun command(text: String): String = when (normalize(text)) {
        "repeat", normalize("ထပ်ပြော"), normalize("ပြန်ပြောပါ"), normalize("다시 말해 주세요") -> "REPEAT"
        "slow", normalize("ဖြည်းဖြည်း"), normalize("ဖြည်းဖြည်းပြော"), "천천히", normalize("천천히 말해 주세요") -> "SLOW"
        "normal", normalize("ပုံမှန်အမြန်နှုန်း"), normalize("보통 속도로") -> "NORMAL"
        "explain", normalize("မြန်မာလိုရှင်းပြ"), normalize("မြန်မာလိုရှင်းပြပါ"), normalize("설명해 주세요") -> "EXPLAIN"
        else -> ""
    }

    fun target(book: BookCourse, state: BookProgress): BookTarget? {
        val section = section(book, state)
        if (section.turns.isNotEmpty()) return section.turns.getOrNull(state.activity)
            ?.takeIf { owns(it, role(section, state)) }?.let { BookTarget(it.text, it.text, it.page) }
        return section.exercises.getOrNull(state.activity)?.let { BookTarget(it.answer, it.prompt, it.page) }
    }

    fun opening(book: BookCourse, state: BookProgress): BookOpening {
        val section = section(book, state)
        if (state.awaitingNext) return BookOpening(state, "잘했어요. 다음을 눌러 주세요.", state.feedback)
        val learner = role(section, state)
        var index = state.activity
        val lines = mutableListOf<BookTurn>()
        while (index < section.turns.size && !owns(section.turns[index], learner)) {
            lines += section.turns[index++]
        }
        val text = when {
            lines.isNotEmpty() -> lines.joinToString("\n") { it.text }
            finished(book, state) -> "ဒီအပိုင်းပြီးပါပြီ။ နောက်အပိုင်းကို ဆက်နိုင်ပါတယ်။"
            section.exercises.isNotEmpty() -> "လေ့ကျင့်ခန်း ${state.activity + 1} ကို ဖြေပါ။"
            section.turns.isNotEmpty() -> "မင်းအလှည့်ပါ။"
            else -> "ဒီအပိုင်းကို ဖတ်ပြီး မြန်မာလိုရှင်းပြချက်ကို ကြည့်နိုင်ပါတယ်။"
        }
        val caption = if (lines.isEmpty()) text else lines.joinToString("\n") { "${it.speaker}: ${it.text}" }
        return BookOpening(state.copy(activity = index, role = learner,
            lastAssistantText = text, lastAssistantCaption = caption), text, caption)
    }

    fun finished(book: BookCourse, state: BookProgress): Boolean {
        val section = section(book, state)
        return state.activity >= when {
            section.turns.isNotEmpty() -> section.turns.size
            section.exercises.isNotEmpty() -> section.exercises.size
            else -> return state.studied || key(state,section) in state.completedSections
        }
    }

    fun assessed(book: BookCourse, state: BookProgress, heard: String, assessment: String,
        feedback: String, isCommand: Boolean = false): BookProgress {
        if (state.awaitingNext) return state
        val target = target(book, state) ?: return state
        if (isCommand || command(heard).isNotEmpty()) return state
        val passed = heard.isNotBlank() && assessment == "PASSED"
        val mistakeKey = key(state, section(book, state)) + "/${state.activity}"
        val previous = state.mistakes.firstOrNull { it.key == mistakeKey }
        val mistakes = if (!passed && heard.isNotBlank()) {
            (state.mistakes.filterNot { it.key == mistakeKey } + BookMistake(mistakeKey, target.text,
                heard.take(600), feedback.take(1000), target.page, (previous?.attempts ?: 0) + 1, previous?.successes ?: 0)).takeLast(100)
        } else state.mistakes.map { if (it.key == mistakeKey && passed) it.copy(successes = it.successes + 1) else it }
        return state.copy(awaitingNext = passed,
            heard = heard.take(600), feedback = feedback.take(1600), mistakes = mistakes)
    }

    fun continueActivity(book: BookCourse, state: BookProgress): BookProgress {
        require(state.awaitingNext) { "ဒီအဖြေကို အရင်လေ့ကျင့်ပါ။" }
        return state.copy(activity = state.activity + 1, awaitingNext = false, heard = "", feedback = "",
            lastAssistantText = "", lastAssistantCaption = "")
    }

    private fun checkpoint(state: BookProgress): List<BookCheckpoint> =
        (state.checkpoints.filterNot { it.chapter == state.chapter && it.section == state.section } +
            BookCheckpoint(state.chapter, state.section, state.activity, state.role, state.awaitingNext,
                state.studied, state.heard, state.feedback, state.lastAssistantText, state.lastAssistantCaption)).takeLast(2000)

    fun selectSection(book: BookCourse, state: BookProgress, chapter: Int, section: Int): BookProgress {
        require(chapter in 1..book.chapters.size && section in book.chapters[chapter - 1].sections.indices)
        if (chapter == state.chapter && section == state.section) return state
        val saved = checkpoint(state)
        val place = saved.firstOrNull { it.chapter == chapter && it.section == section }
        return state.copy(chapter = chapter, section = section, activity = place?.activity ?: 0,
            role = place?.role.orEmpty(), awaitingNext = place?.awaitingNext ?: false, studied = place?.studied ?: false,
            heard = place?.heard.orEmpty(), feedback = place?.feedback.orEmpty(),
            lastAssistantText = place?.lastAssistantText.orEmpty(), lastAssistantCaption = place?.lastAssistantCaption.orEmpty(),
            checkpoints = saved)
    }

    fun completeSection(book: BookCourse, state: BookProgress): BookProgress {
        require(finished(book, state)) { "ဒီအပိုင်းက စကားပြော/လေ့ကျင့်ခန်းကို အရင်ပြီးအောင်လုပ်ပါ" }
        val chapter = chapter(book, state)
        val sectionKey = key(state, section(book, state))
        val complete = (state.completedSections + sectionKey).distinct()
        val chapterDone = chapter.sections.all { key(state, it) in complete }
        val chapters = if (chapterDone) (state.completedChapters + state.chapter).distinct().sorted() else state.completedChapters
        val nextSection = state.section + 1
        val nextChapter = if (chapterDone && nextSection >= chapter.sections.size && state.chapter < book.chapters.size) state.chapter + 1 else state.chapter
        val destination = if (nextSection < chapter.sections.size) nextSection else if (nextChapter != state.chapter) 0
            else if (!chapterDone) chapter.sections.indexOfFirst { key(state, it) !in complete } else state.section
        return state.copy(chapter = nextChapter, section = destination, activity = if (destination == state.section && nextChapter == state.chapter) state.activity else 0,
            role = "", completedSections = complete, completedChapters = chapters, awaitingNext = false, studied = false, checkpoints = checkpoint(state),
            lastAssistantText = "", lastAssistantCaption = "", heard = "", feedback = "")
    }

    fun frontier(book: BookCourse, state: BookProgress): Int {
        var next = 1
        while (next in state.completedChapters && next < book.chapters.size) next++
        return next
    }

    fun selectChapter(book: BookCourse, state: BookProgress, number: Int): BookProgress {
        require(number in 1..book.chapters.size)
        val resume = state.checkpoints.lastOrNull { it.chapter == number }?.section ?: 0
        return selectSection(book, state, number, resume)
    }

    fun restart(state: BookProgress, role: String = state.role) = state.copy(activity = 0, role = role, awaitingNext = false, studied = false,
        lastAssistantText = "", lastAssistantCaption = "", heard = "", feedback = "")

    fun validate(book: BookCourse, state: BookProgress): Boolean {
        if (state.bookId != book.id || state.version != book.version || state.chapter !in 1..book.chapters.size) return false
        val chapter = chapter(book, state)
        if (state.section !in chapter.sections.indices) return false
        val section = chapter.sections[state.section]
        val count = if (section.turns.isNotEmpty()) section.turns.size else section.exercises.size
        return state.activity in 0..count && state.completedChapters.all { it in 1..book.chapters.size } &&
            state.completedSections.size <= 2000 && state.mistakes.size <= 100 && state.role.length <= 80 &&
            state.checkpoints.size <= 2000 && state.checkpoints.all { place ->
                val ch = book.chapters.getOrNull(place.chapter - 1)
                val sec = ch?.sections?.getOrNull(place.section)
                val countAtPlace = sec?.let { if (it.turns.isNotEmpty()) it.turns.size else it.exercises.size } ?: -1
                sec != null && place.activity in 0..countAtPlace && place.role.length <= 80
            }
    }
}
