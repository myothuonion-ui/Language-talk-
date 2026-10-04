package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.*
import org.junit.Assert.*
import org.junit.Test

class BookEngineTest {
    private fun dialogue(id: String = "dialogue") = BookSection(id, "စကားပြော", "DIALOGUE", "source", listOf(14), listOf(
        BookTurn("ဆရာ", "안녕하세요.", "Hello", 14),
        BookTurn("သူငယ်ချင်း", "반갑습니다.", "Nice to meet you", 14),
        BookTurn("학생", "저도 반갑습니다.", "Me too", 14),
        BookTurn("ဆရာ", "어디에 사세요?", "Where do you live?", 14),
        BookTurn("학생", "서울에 살아요.", "I live in Seoul", 14)))
    private fun course(sections: List<BookSection> = listOf(dialogue())) = BookCourse("ttmik-beginner", "Book", "Beginner", "user-pdf-1", "books/ttmik-beginner.pdf", "sha", 332,
        listOf(BookChapter(1, "Introduction", "Introductions", 12, 19, sections),
            BookChapter(2, "Next", "Introductions", 20, 27, listOf(dialogue("next")))))
    private fun state() = BookProgress("ttmik-beginner", role = "학생")

    @Test fun assistant_takes_all_other_roles_and_stops_at_learner() {
        val opening = BookEngine.opening(course(), state())
        assertEquals(2, opening.progress.activity)
        assertEquals("안녕하세요.\n반갑습니다.", opening.text)
        assertFalse(opening.text.contains("저도"))
        assertEquals("저도 반갑습니다.", BookEngine.target(course(), opening.progress)?.text)
    }

    @Test fun learner_answer_then_exact_next_source_line() {
        val book = course()
        val ready = BookEngine.opening(book, state()).progress
        val assessed = BookEngine.assessed(book, ready, "저도 반갑습니다", "PASSED", "")
        val next = BookEngine.opening(book, assessed)
        assertEquals("어디에 사세요?", next.text)
        assertEquals("서울에 살아요.", BookEngine.target(book, next.progress)?.text)
    }

    @Test fun commands_uncertainty_and_empty_transcript_do_not_advance() {
        val book = course()
        val ready = BookEngine.opening(book, state()).progress
        for (command in listOf("ထပ်ပြော", "ဖြည်းဖြည်း", "မြန်မာလိုရှင်းပြ", "다시 말해 주세요", "repeat", "normal")) {
            assertTrue(BookEngine.command(command).isNotEmpty())
            assertEquals(ready, BookEngine.assessed(book, ready, command, "PASSED", ""))
        }
        assertEquals(ready.activity, BookEngine.assessed(book, ready, "", "PASSED", "").activity)
        assertEquals(ready.activity, BookEngine.assessed(book, ready, "unclear", "UNSURE", "").activity)
        assertEquals("", BookEngine.command("천천히 일하세요"))
    }

    @Test fun failed_attempt_retains_cursor_and_records_specific_source() {
        val book = course()
        val ready = BookEngine.opening(book, state()).progress
        val retry = BookEngine.assessed(book, ready, "저 반갑", "RETRY", "다시")
        assertEquals(ready.activity, retry.activity)
        assertEquals("저 반갑", retry.mistakes.single().heard)
        assertEquals(14, retry.mistakes.single().page)
        assertEquals("저도 반갑습니다.", retry.mistakes.single().target)
        val passed = BookEngine.assessed(book, retry, "저도 반갑습니다", "PASSED", "")
        assertEquals(1, passed.mistakes.single().successes)
    }

    @Test fun cannot_complete_dialogue_or_unlock_future_chapter_early() {
        val book = course()
        assertThrows(IllegalArgumentException::class.java) { BookEngine.completeSection(book, state()) }
        assertThrows(IllegalArgumentException::class.java) { BookEngine.selectChapter(book, state(), 2) }
        val done = BookEngine.completeSection(book, state().copy(activity = 5))
        assertEquals(listOf(1), done.completedChapters)
        assertEquals(2, done.chapter)
        assertEquals(0, done.activity)
        assertEquals(2, BookEngine.frontier(book, done))
    }

    @Test fun chapter_completion_requires_every_section_even_when_last_is_reviewed_first() {
        val book = course(listOf(dialogue("first"), dialogue("last")))
        val lastOnly = BookEngine.completeSection(book, state().copy(section = 1, activity = 5))
        assertTrue(lastOnly.completedChapters.isEmpty())
        assertEquals(1, lastOnly.chapter)
        assertEquals(0, lastOnly.section)
        assertEquals(1, BookEngine.frontier(book, lastOnly))
    }

    @Test fun grammatical_exercise_uses_original_answer_and_preserves_order() {
        val section = BookSection("grammar", "Grammar", "GRAMMAR", "source", listOf(17), exercises = listOf(
            BookExercise(1, "office worker", "저는 회사원이에요.", 17),
            BookExercise(2, "engineer", "저는 엔지니어예요.", 17)))
        val book = course(listOf(section))
        assertEquals("저는 회사원이에요.", BookEngine.target(book, state())?.text)
        val after = BookEngine.assessed(book, state(), "저는회사원이에요", "PASSED", "")
        assertEquals("저는 엔지니어예요.", BookEngine.target(book, after)?.text)
        assertFalse(BookEngine.finished(book, after))
    }

    @Test fun grouped_speakers_can_include_the_learner() {
        val turn = BookTurn("성 대리, 최 대리", "네, 알겠습니다.", "Yes", 224)
        assertTrue(BookEngine.owns(turn, "최 대리"))
        assertFalse(BookEngine.owns(turn, "팀장"))
    }

    @Test fun selecting_a_new_scene_role_and_restart_keep_previous_progress() {
        val old = state().copy(activity = 4, completedChapters = listOf(1), completedSections = listOf("done"))
        val changed = BookEngine.restart(old, "ဆရာ")
        assertEquals(0, changed.activity)
        assertEquals("ဆရာ", changed.role)
        assertEquals(old.completedChapters, changed.completedChapters)
        assertEquals(old.completedSections, changed.completedSections)
    }

    @Test fun restore_rejects_wrong_book_edition_and_out_of_bounds_cursor() {
        val book = course()
        assertTrue(BookEngine.validate(book, state()))
        assertFalse(BookEngine.validate(book, state().copy(bookId = "ttmik-intermediate")))
        assertFalse(BookEngine.validate(book, state().copy(version = "unknown")))
        assertFalse(BookEngine.validate(book, state().copy(activity = 6)))
        assertFalse(BookEngine.validate(book, state().copy(section = 9)))
    }
}
