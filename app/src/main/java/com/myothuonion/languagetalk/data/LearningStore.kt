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

private val Context.learningDataStore by preferencesDataStore(name = "language_talk_learning")

class LearningStore(private val context: Context) {
    private val key = stringPreferencesKey("learning")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val state = context.learningDataStore.data.map { prefs ->
        prefs[key]?.let { json.decodeFromString<LearningState>(it) } ?: LearningState()
    }
    val curriculum: List<LearningUnit> by lazy {
        context.assets.open("learning/curriculum.json").bufferedReader().use { json.decodeFromString(it.readText()) }
    }
    suspend fun update(transform: (LearningState) -> LearningState) {
        context.learningDataStore.edit { prefs ->
            val old = prefs[key]?.let { json.decodeFromString<LearningState>(it) } ?: LearningState()
            prefs[key] = json.encodeToString(transform(old))
        }
    }
    suspend fun export() = state.first()
    fun validateRestore(value: LearningState) {
        val ids = curriculum.map { it.id }.toSet()
        require(value.level in 0..5 && value.placementLevel in 0..5 && value.placementIndex in 0..2 &&
            value.placementPasses in 0..2 && value.dailyMinutes in 5..60) { "Invalid learning level" }
        require(value.activeUnit.isBlank() || value.activeUnit in ids)
        require(value.units.size <= ids.size && value.units.map { it.id }.distinct().size == value.units.size)
        require(value.units.all { it.id in ids && it.step in CoachStep.entries.indices })
        require(value.cards.size <= 5000 && value.cards.map { it.id }.distinct().size == value.cards.size &&
            value.cards.all { it.id.length <= 160 && it.prompt.length <= 4000 && it.criterion.length <= 6000 &&
                it.meaning.length <= 4000 && it.context.length <= 4000 && it.page >= 0 })
        require(value.books.size <= 300 && value.books.map { it.id }.distinct().size == value.books.size &&
            value.books.all { it.id.matches(Regex("[a-zA-Z0-9-]{1,90}")) && it.pageCount in 1..5000 &&
                it.page in 0 until it.pageCount && it.bookmarks.all { page -> page in 0 until it.pageCount } &&
                (it.fileName.isBlank() || it.fileName == it.id + ".pdf") &&
                it.assetId in setOf("", "ttmik-beginner", "ttmik-intermediate") })
        require(value.attempts.size <= 500 && value.attempts.all { it.heard.length <= 4000 && it.feedback.length <= 4000 })
        require(value.translations.size <= 500 && value.translations.all { it.meaning.length <= 4000 && it.sentence.length <= 4000 })
        require(value.studySeconds.size <= 370 && value.studySeconds.all { it.key.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && it.value in 0..86400 })
    }
    suspend fun restore(value: LearningState) {
        validateRestore(value)
        update { old -> value.copy(
            cards = (old.cards + value.cards).associateBy { it.id }.values.toList().takeLast(5000),
            books = (old.books + value.books).associateBy { it.id }.values.toList().takeLast(300),
            translations = (old.translations + value.translations).associateBy { it.key }.values.toList().takeLast(500))
        }
    }
}
