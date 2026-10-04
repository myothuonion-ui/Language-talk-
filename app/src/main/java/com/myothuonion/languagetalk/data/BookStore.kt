package com.myothuonion.languagetalk.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myothuonion.languagetalk.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.bookDataStore by preferencesDataStore(name = "language_talk_books")

class BookStore(private val context: Context) {
    private val key = stringPreferencesKey("shelf")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val state = context.bookDataStore.data.map { prefs ->
        prefs[key]?.let { json.decodeFromString<BookShelfState>(it) } ?: BookShelfState()
    }

    suspend fun update(transform: (BookShelfState) -> BookShelfState) {
        context.bookDataStore.edit { prefs ->
            val old = prefs[key]?.let { json.decodeFromString<BookShelfState>(it) } ?: BookShelfState()
            prefs[key] = json.encodeToString(transform(old))
        }
    }

    suspend fun export() = state.first()
    fun validateRestore(value: BookShelfState) {
        require(value.lastBookId in setOf("ttmik-beginner", "ttmik-intermediate")) { "Unknown book" }
        value.progress.forEach { state ->
            require(state.bookId in setOf("ttmik-beginner", "ttmik-intermediate"))
            val course = context.assets.open("books/${state.bookId}.json").bufferedReader().use { json.decodeFromString<BookCourse>(it.readText()) }
            require(BookEngine.validate(course, state)) { "Book progress does not match this edition" }
        }
    }
    suspend fun restore(value: BookShelfState) = update { old ->
        old.copy(progress = (old.progress + value.progress).associateBy { it.bookId }.values.toList(),
            explanations = (old.explanations + value.explanations).associateBy { it.key }.values.toList().takeLast(500),
            lastBookId = value.lastBookId)
    }
}
