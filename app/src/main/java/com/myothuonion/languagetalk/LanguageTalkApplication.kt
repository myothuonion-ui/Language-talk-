package com.myothuonion.languagetalk

import android.app.Application
import com.myothuonion.languagetalk.data.AppDatabase
import com.myothuonion.languagetalk.data.SecretStore
import com.myothuonion.languagetalk.data.SettingsStore
import com.myothuonion.languagetalk.data.TutorRepository
import com.myothuonion.languagetalk.data.BookStore
import com.myothuonion.languagetalk.data.BookRepository
import com.myothuonion.languagetalk.data.LearningStore
import com.myothuonion.languagetalk.data.LearningRepository
import com.myothuonion.languagetalk.data.ReaderRepository
import com.myothuonion.languagetalk.network.GeminiClient
import com.myothuonion.languagetalk.network.NvidiaClient

class LanguageTalkApplication : Application() {
    val learningStore by lazy { LearningStore(this) }
    val learning by lazy { LearningRepository(this, learningStore, repository) }
    val reader by lazy { ReaderRepository(this, learningStore, books, repository) }
    val bookStore by lazy { BookStore(this) }
    val books by lazy { BookRepository(this, bookStore, repository) }
    val repository: TutorRepository by lazy {
        TutorRepository(
            dao = AppDatabase.get(this).dao(),
            database = AppDatabase.get(this),
            recordingDirectory = java.io.File(filesDir, "practice-recordings"),
            settingsStore = SettingsStore(this),
            secrets = SecretStore(this),
            gemini = GeminiClient(),
            nvidia = NvidiaClient(),
            bookStore = bookStore,
            learningStore = learningStore
        )
    }
}
