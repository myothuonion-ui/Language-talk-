package com.myothuonion.languagetalk

import android.app.Application
import com.myothuonion.languagetalk.data.AppDatabase
import com.myothuonion.languagetalk.data.SecretStore
import com.myothuonion.languagetalk.data.SettingsStore
import com.myothuonion.languagetalk.data.TutorRepository
import com.myothuonion.languagetalk.network.GeminiClient
import com.myothuonion.languagetalk.network.NvidiaClient

class LanguageTalkApplication : Application() {
    val repository: TutorRepository by lazy {
        TutorRepository(
            dao = AppDatabase.get(this).dao(),
            settingsStore = SettingsStore(this),
            secrets = SecretStore(this),
            gemini = GeminiClient(),
            nvidia = NvidiaClient()
        )
    }
}
