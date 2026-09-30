package com.myothuonion.languagetalk.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myothuonion.languagetalk.model.BrainMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "language_talk_settings")

data class AppSettings(
    val displayName: String = "Myo Min Thu",
    val explanationLanguage: String = "မြန်မာ",
    val brainMode: BrainMode = BrainMode.GEMINI_ONLY,
    val geminiModel: String = "gemini-3.8-flash",
    val geminiTtsModel: String = "gemini-3.8-flash-tts",
    val nvidiaModel: String = "nvidia/nemotron-3-ultra-550b-a55b",
    val liveModel: String = "gemini-3.8-live",
    val globalBehavior: String = DEFAULT_GLOBAL_BEHAVIOR,
    val learnerProfile: String = DEFAULT_LEARNER_PROFILE,
    val autoLearningMemory: Boolean = true,
    val recordPractice: Boolean = true,
    val defaultVoiceName: String = "Kore",
    val defaultVoiceStyle: String = "Warm, clear tutor. Short sentences and patient pauses.",
    val defaultPace: String = "SLOW",
    val defaultSilenceMs: Int = 2000,
    val defaultSpeakCorrections: Boolean = true,
    val autoSpeak: Boolean = true,
    val darkTheme: Boolean = true
)

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            displayName = prefs[DISPLAY_NAME] ?: "Myo Min Thu",
            explanationLanguage = prefs[EXPLANATION_LANGUAGE] ?: "မြန်မာ",
            brainMode = runCatching { BrainMode.valueOf(prefs[BRAIN_MODE] ?: "") }
                .getOrDefault(BrainMode.GEMINI_ONLY),
            geminiModel = prefs[GEMINI_MODEL] ?: "gemini-3.8-flash",
            geminiTtsModel = prefs[GEMINI_TTS_MODEL] ?: "gemini-3.8-flash-tts",
            nvidiaModel = prefs[NVIDIA_MODEL] ?: "nvidia/nemotron-3-ultra-550b-a55b",
            liveModel = prefs[LIVE_MODEL] ?: "gemini-3.8-live",
            globalBehavior = prefs[GLOBAL_BEHAVIOR] ?: DEFAULT_GLOBAL_BEHAVIOR,
            learnerProfile = prefs[LEARNER_PROFILE] ?: DEFAULT_LEARNER_PROFILE,
            autoLearningMemory = prefs[AUTO_LEARNING_MEMORY] ?: true,
            recordPractice = prefs[RECORD_PRACTICE] ?: true,
            defaultVoiceName = prefs[DEFAULT_VOICE_NAME] ?: "Kore",
            defaultVoiceStyle = prefs[DEFAULT_VOICE_STYLE] ?: "Warm, clear tutor. Short sentences and patient pauses.",
            defaultPace = prefs[DEFAULT_PACE] ?: "SLOW",
            defaultSilenceMs = (prefs[DEFAULT_SILENCE_MS]?.toIntOrNull() ?: 2000).coerceIn(800, 4000),
            defaultSpeakCorrections = prefs[DEFAULT_SPEAK_CORRECTIONS] ?: true,
            autoSpeak = prefs[AUTO_SPEAK] ?: true,
            darkTheme = prefs[DARK_THEME] ?: true
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val value = transform(settings.first())
        context.dataStore.edit { prefs ->
            prefs[DISPLAY_NAME] = value.displayName
            prefs[EXPLANATION_LANGUAGE] = value.explanationLanguage
            prefs[BRAIN_MODE] = value.brainMode.name
            prefs[GEMINI_MODEL] = value.geminiModel
            prefs[GEMINI_TTS_MODEL] = value.geminiTtsModel
            prefs[NVIDIA_MODEL] = value.nvidiaModel
            prefs[LIVE_MODEL] = value.liveModel
            prefs[GLOBAL_BEHAVIOR] = value.globalBehavior
            prefs[LEARNER_PROFILE] = value.learnerProfile.take(4000)
            prefs[AUTO_LEARNING_MEMORY] = value.autoLearningMemory
            prefs[RECORD_PRACTICE] = value.recordPractice
            prefs[DEFAULT_VOICE_NAME] = value.defaultVoiceName
            prefs[DEFAULT_VOICE_STYLE] = value.defaultVoiceStyle.take(1000)
            prefs[DEFAULT_PACE] = value.defaultPace
            prefs[DEFAULT_SILENCE_MS] = value.defaultSilenceMs.coerceIn(800, 4000).toString()
            prefs[DEFAULT_SPEAK_CORRECTIONS] = value.defaultSpeakCorrections
            prefs[AUTO_SPEAK] = value.autoSpeak
            prefs[DARK_THEME] = value.darkTheme
        }
    }

    companion object {
        private val LEARNER_PROFILE = stringPreferencesKey("learner_profile")
        private val AUTO_LEARNING_MEMORY = booleanPreferencesKey("auto_learning_memory")
        private val RECORD_PRACTICE = booleanPreferencesKey("record_practice")
        private val DEFAULT_VOICE_NAME = stringPreferencesKey("default_voice_name")
        private val DEFAULT_VOICE_STYLE = stringPreferencesKey("default_voice_style")
        private val DEFAULT_PACE = stringPreferencesKey("default_pace")
        private val DEFAULT_SILENCE_MS = stringPreferencesKey("default_silence_ms")
        private val DEFAULT_SPEAK_CORRECTIONS = booleanPreferencesKey("default_speak_corrections")
        private val DISPLAY_NAME = stringPreferencesKey("display_name")
        private val EXPLANATION_LANGUAGE = stringPreferencesKey("explanation_language")
        private val BRAIN_MODE = stringPreferencesKey("brain_mode")
        private val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val GEMINI_TTS_MODEL = stringPreferencesKey("gemini_tts_model")
        private val NVIDIA_MODEL = stringPreferencesKey("nvidia_model")
        private val LIVE_MODEL = stringPreferencesKey("live_model")
        private val GLOBAL_BEHAVIOR = stringPreferencesKey("global_behavior")
        private val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        private val DARK_THEME = booleanPreferencesKey("dark_theme")
    }
}

const val DEFAULT_GLOBAL_BEHAVIOR = """You are my Korean and English language teacher.
If I speak or write in Korean, answer in Korean first. Stay directly on the topic I asked about and avoid unnecessary filler.
When teaching a Korean sentence, show the natural Korean sentence first, then explain its meaning clearly in Myanmar.
Correct grammar, particles, tense, honorifics, politeness, spelling, pronunciation, meaning, and naturalness only when useful.
Keep the conversation moving by asking one short relevant question when appropriate."""


const val DEFAULT_LEARNER_PROFILE = """I am a Myanmar-speaking learner living in Korea. I need practical Korean for factory work, coworkers, team leaders, greetings, manners, safety, dormitory life, shops and food ordering. I have 30 minutes a day. Focus on understanding and speaking, about five useful phrases per session. Explain briefly in Myanmar when needed. Do not assume my employer, shift, or exact language ability."""
