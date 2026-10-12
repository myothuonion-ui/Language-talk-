package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.data.BackupCodec
import com.myothuonion.languagetalk.data.LearningBackup
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ReaderToolsTest {
    @Test fun annotations_and_pending_practice_survive_a_portable_backup() {
        val note = ReaderAnnotation("note-1","ttmik-beginner",68,"깨워 주세요","NOTE",
            "မနက် နှိုးပေးပါ",rectangles = listOf(ReaderRect(.1f,.2f,.4f,.25f)))
        assertTrue(ReaderTools.valid(note))
        val backup = LearningBackup(learning = LearningState(annotations = listOf(note),
            units = listOf(UnitProgress("course-1-1",readyToContinue = true)),readerTextMode = true,readerFontSize = 25))
        val restored = BackupCodec.decode(BackupCodec.json.encodeToString(backup).encodeToByteArray())
        assertEquals(backup.learning,restored.learning)
    }
    @Test fun corrupted_annotation_geometry_and_unbounded_notes_are_rejected() {
        val note = ReaderAnnotation("1","pdf-test",0,"hello")
        assertTrue(ReaderTools.valid(note))
        assertFalse(ReaderTools.valid(note.copy(rectangles = listOf(ReaderRect(Float.NaN,0f,1f,1f)))))
        assertFalse(ReaderTools.valid(note.copy(rectangles = listOf(ReaderRect(.7f,0f,.2f,1f)))))
        assertFalse(ReaderTools.valid(note.copy(note = "x".repeat(4001))))
        assertFalse(ReaderTools.valid(note.copy(kind = "UNKNOWN")))
    }
    @Test fun teaching_decodes_separate_grammar_rules_and_keeps_answers_separate() {
        val text = """{"overview":"Grammar","grammar":[{"pattern":"보다","meaning":"comparison"},
            {"pattern":"-고 오다","meaning":"sequence"}],"exercises":[{"question":"하다 + 고 오다","hint":"stem","answer":"하고 오다"}]}"""
        val teaching = ReaderTools.teaching(text)
        assertEquals(listOf("보다","-고 오다"),teaching.grammar.map { it.pattern })
        assertEquals("하고 오다",teaching.exercises.single().answer)
        assertThrows(IllegalArgumentException::class.java) { ReaderTools.teaching("""{"overview":""}""") }
    }
    @Test fun unicode_search_preserves_the_actual_source_excerpt() {
        assertTrue(ReaderTools.excerpt("엄마! 내일 여섯 시에 깨워 주세요.","깨워")!!.contains("깨워 주세요"))
        assertNull(ReaderTools.excerpt("오늘 집에 와요.","없는 말"))
        assertNull(ReaderTools.excerpt("오늘",""))
    }
    @Test fun continue_is_rejected_without_a_practised_item() {
        assertThrows(IllegalArgumentException::class.java) { PracticeEngine.continueStep(PracticeState()) }
        assertThrows(IllegalArgumentException::class.java) { CoachEngine.continueStep(UnitProgress("course-1-1")) }
    }
}
