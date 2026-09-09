package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.AppLanguage
import com.myothuonion.languagetalk.model.DefaultVoicePresets
import com.myothuonion.languagetalk.model.TutorReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelsTest {
    @Test
    fun spokenTextIncludesFollowUpQuestion() {
        val reply = TutorReply(reply = "오늘은 바빠요.", followUpQuestion = "무슨 일을 했어요?")
        assertEquals("오늘은 바빠요. 무슨 일을 했어요?", reply.spokenText)
    }

    @Test
    fun koreanAndEnglishLanguagesAreAvailable() {
        assertEquals("ko", AppLanguage.KOREAN.code)
        assertEquals("en", AppLanguage.ENGLISH.code)
    }

    @Test
    fun voicePresetsContainKoreanAndEnglishTutors() {
        assertTrue(DefaultVoicePresets.any { it.name.contains("Korean") })
        assertTrue(DefaultVoicePresets.any { it.name.contains("English") })
        assertTrue(DefaultVoicePresets.any { it.name.contains("EPS") })
    }
}
