package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.data.isGeminiFallbackEligible
import com.myothuonion.languagetalk.model.AppLanguage
import com.myothuonion.languagetalk.model.DefaultVoicePresets
import com.myothuonion.languagetalk.model.TutorConfig
import com.myothuonion.languagetalk.model.TutorReply
import com.myothuonion.languagetalk.model.knownKoreanNameResult
import com.myothuonion.languagetalk.network.AiApiException
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

    @Test
    fun chatCanStartWithScopedMemory() {
        val config = TutorConfig(initialMemory = "My contract shift starts at 8 AM")
        assertEquals("My contract shift starts at 8 AM", config.initialMemory)
    }

    @Test
    fun geminiFallsBackForUnavailableModelsAndQuota() {
        assertTrue(isGeminiFallbackEligible(AiApiException("Model not found", 404)))
        assertTrue(isGeminiFallbackEligible(AiApiException("Quota reached", 429)))
        assertTrue(isGeminiFallbackEligible(AiApiException("Service unavailable", 503)))
    }

    @Test
    fun geminiDoesNotHideInvalidCredentialsBehindFallback() {
        assertTrue(!isGeminiFallbackEligible(AiApiException("Invalid API key", 401)))
        assertTrue(!isGeminiFallbackEligible(AiApiException("Forbidden", 403)))
    }

    @Test
    fun myoMinThuHasInstantVerifiedKoreanPronunciationChoices() {
        val result = knownKoreanNameResult("မျိုးမင်းသူ")
        assertEquals(listOf("묘민뚜", "묘민투", "묘민두"), result?.candidates?.map { it.hangul })
        assertEquals(3, result?.candidates?.map { it.animal }?.distinct()?.size)
        assertTrue(result?.candidates?.all { it.externalImagePrompt.contains(it.hangul) } == true)
    }
}
