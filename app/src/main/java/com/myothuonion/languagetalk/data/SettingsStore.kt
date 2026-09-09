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
    val geminiTtsModel: String = "gemini-3.1-flash-tts-preview",
    val nvidiaModel: String = "nvidia/nemotron-3-ultra-550b-a55b",
    val liveModel: String = "gemini-3.1-flash-live-preview",
    val globalBehavior: String = DEFAULT_GLOBAL_BEHAVIOR,
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
            geminiTtsModel = prefs[GEMINI_TTS_MODEL] ?: "gemini-3.1-flash-tts-preview",
            nvidiaModel = prefs[NVIDIA_MODEL] ?: "nvidia/nemotron-3-ultra-550b-a55b",
            liveModel = prefs[LIVE_MODEL] ?: "gemini-3.1-flash-live-preview",
            globalBehavior = prefs[GLOBAL_BEHAVIOR] ?: DEFAULT_GLOBAL_BEHAVIOR,
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
            prefs[AUTO_SPEAK] = value.autoSpeak
            prefs[DARK_THEME] = value.darkTheme
        }
    }

    companion object {
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
