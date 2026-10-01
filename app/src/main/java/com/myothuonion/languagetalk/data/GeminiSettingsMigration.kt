package com.myothuonion.languagetalk.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.myothuonion.languagetalk.network.GeminiModels
import com.myothuonion.languagetalk.network.GeminiTask

class GeminiSettingsMigration : DataMigration<Preferences> {
    private val keys = listOf("gemini_model" to GeminiTask.TEXT, "gemini_tts_model" to GeminiTask.SPEECH, "live_model" to GeminiTask.LIVE)
    override suspend fun shouldMigrate(currentData: Preferences) = keys.any { (key, task) ->
        currentData[stringPreferencesKey(key)]?.let { it != GeminiModels.normalize(it, task) } == true
    }
    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        keys.forEach { (key, task) ->
            val preference = stringPreferencesKey(key)
            currentData[preference]?.let { this[preference] = GeminiModels.normalize(it, task) }
        }
    }
    override suspend fun cleanUp() = Unit
}
