package com.myothuonion.languagetalk

import androidx.datastore.preferences.core.*
import com.myothuonion.languagetalk.data.GeminiSettingsMigration
import com.myothuonion.languagetalk.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GeminiCompatibilityTest {
    @Test fun savedLegacyModelsAreMigratedWithoutChangingPersonalContext() = runBlocking<Unit> {
        val old = preferencesOf(
            stringPreferencesKey("gemini_model") to "gemini-2.5-flash",
            stringPreferencesKey("gemini_tts_model") to "gemini-2.5-flash-preview-tts",
            stringPreferencesKey("live_model") to "gemini-3.1-flash-live-preview",
            stringPreferencesKey("learner_profile") to "ညဆိုင်း · Korean beginner",
            stringPreferencesKey("default_voice_name") to "Charon",
            stringPreferencesKey("nvidia_model") to "my/custom-model",
            booleanPreferencesKey("record_practice") to false
        )
        val migration = GeminiSettingsMigration()
        assertTrue(migration.shouldMigrate(old))
        val result = migration.migrate(old)
        assertEquals(GeminiModels.TEXT, result[stringPreferencesKey("gemini_model")])
        assertEquals(GeminiModels.SPEECH, result[stringPreferencesKey("gemini_tts_model")])
        assertEquals(GeminiModels.LIVE, result[stringPreferencesKey("live_model")])
        listOf("learner_profile", "default_voice_name", "nvidia_model").forEach {
            assertEquals(old[stringPreferencesKey(it)], result[stringPreferencesKey(it)])
        }
        assertEquals(false, result[booleanPreferencesKey("record_practice")])
        assertFalse(migration.shouldMigrate(result))
    }

    @Test fun compatibleCustomModelsAndFreshPreferencesArePreserved() = runBlocking<Unit> {
        val migration = GeminiSettingsMigration()
        val preferences = preferencesOf(stringPreferencesKey("gemini_model") to "gemini-3.5-flash-lite")
        assertFalse(migration.shouldMigrate(preferences))
        assertEquals(preferences, migration.migrate(preferences))
        assertFalse(migration.shouldMigrate(emptyPreferences()))
    }

    @Test fun malformedAndWrongTaskModelNamesCannotBePersisted() {
        assertEquals(GeminiModels.TEXT, GeminiModels.normalize("gemini-3.8-flash-tts", GeminiTask.TEXT))
        assertEquals(GeminiModels.SPEECH, GeminiModels.normalize("gemini-3.8-live", GeminiTask.SPEECH))
        assertEquals(GeminiModels.LIVE, GeminiModels.normalize("../../bad", GeminiTask.LIVE))
        assertEquals("gemini-3.5-flash-lite", GeminiModels.normalize(" models/gemini-3.5-flash-lite ", GeminiTask.TEXT))
    }

    @Test fun onlyListedModernModelsForTheRightCapabilityAreRouted() {
        val models = listOf(
            GeminiModel("models/gemini-2.5-flash", setOf("generateContent")),
            GeminiModel("models/gemini-3.8-flash", setOf("countTokens")),
            GeminiModel("models/gemini-3.5-flash-lite", setOf("generateContent")),
            GeminiModel("models/gemini-3.8-flash-tts", setOf("generateContent")),
            GeminiModel("models/gemini-3.8-live", setOf("bidiGenerateContent"))
        )
        assertEquals(listOf("gemini-3.5-flash-lite"), GeminiModels.candidates("gemini-2.5-flash", GeminiTask.TEXT, models))
        assertEquals(listOf(GeminiModels.SPEECH), GeminiModels.candidates("gemini-2.5-flash-preview-tts", GeminiTask.SPEECH, models))
        assertEquals(listOf(GeminiModels.LIVE), GeminiModels.candidates("", GeminiTask.LIVE, models))
        assertTrue(GeminiModels.candidates("", GeminiTask.TEXT, emptyList()).isEmpty())
    }

    @Test fun modelListingAloneNeverClaimsVoiceReadiness() {
        assertFalse(GeminiKeyCheck(61, null, null, GeminiModels.LIVE, listOf("Voice: quota exhausted")).ready)
        assertFalse(GeminiKeyCheck(61, GeminiModels.TEXT, null, null, listOf("Voice: quota exhausted")).ready)
        assertTrue(GeminiKeyCheck(61, GeminiModels.TEXT, GeminiModels.SPEECH, null, emptyList()).ready)
    }

    @Test fun laterMissingModelCannotHideOriginalQuotaError() = runBlocking<Unit> {
        val attempted = mutableListOf<String>()
        val failure = runCatching { routeGemini("Text", listOf("modern-a", "modern-b")) {
            attempted += it
            if (it == "modern-a") throw AiApiException("Daily quota exhausted", 429)
            else throw AiApiException("Model not found", 404)
        } }.exceptionOrNull() as AiApiException
        assertEquals(429, failure.statusCode)
        assertTrue(failure.message!!.contains("Daily quota exhausted"))
        assertTrue(failure.message!!.contains("modern-a"))
        assertEquals(listOf("modern-a", "modern-b"), attempted)
    }

    @Test fun authorizationErrorsStopWithoutTryingOtherModels() = runBlocking<Unit> {
        var calls = 0
        val failure = runCatching { routeGemini("Voice", listOf("a", "b")) { calls++; throw AiApiException("Key denied", 403) } }.exceptionOrNull()
        assertEquals(403, (failure as AiApiException).statusCode)
        assertEquals(1, calls)
    }

    @Test fun cancellationNeverBecomesAFallbackRequest() = runBlocking<Unit> {
        var calls = 0
        assertTrue(runCatching { routeGemini("Text", listOf("a", "b")) { calls++; throw CancellationException() } }.exceptionOrNull() is CancellationException)
        assertEquals(1, calls)
    }
}
