package com.myothuonion.languagetalk.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myothuonion.languagetalk.LanguageTalkApplication
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.util.PdfPageReader
import com.myothuonion.languagetalk.util.ReadablePdfPage
import com.myothuonion.languagetalk.util.GeminiAudioPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReaderUiState(val bookId: String = "", val page: Int = 0, val pageData: ReadablePdfPage? = null,
    val loading: Boolean = false, val translating: Boolean = false, val selection: String = "", val sentence: String = "",
    val translation: ReaderTranslation? = null, val saved: Boolean = false, val error: String? = null)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LanguageTalkApplication
    private val repository = app.reader
    private val engine = PdfPageReader(application)
    private val player = GeminiAudioPlayer(application)
    val learning = repository.store.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LearningState())
    private val _ui = MutableStateFlow(ReaderUiState())
    val ui = _ui.asStateFlow()
    private var renderJob: Job? = null
    private var translateJob: Job? = null
    init { viewModelScope.launch { runCatching { repository.initialize() }.onFailure { e -> _ui.update { it.copy(error = e.message) } } } }
    fun import(uri: Uri) = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null) }
        try { val book = repository.import(uri); open(book.id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { _ui.update { it.copy(error = failure.message, loading = false) } }
    }
    fun open(id: String, page: Int? = null) {
        renderJob?.cancel(); translateJob?.cancel(); player.stop()
        renderJob = viewModelScope.launch {
            _ui.value = ReaderUiState(bookId = id, loading = true)
            try {
                val book = repository.store.export().books.first { it.id == id }
                val index = (page ?: book.page).coerceIn(0, book.pageCount - 1)
                _ui.update { it.copy(page = index) }
                val data = engine.render(repository.file(book), book.id, index)
                _ui.update { it.copy(pageData = data, loading = false) }
                repository.page(id, index)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _ui.update { it.copy(error = failure.message, loading = false) } }
        }
    }
    fun page(index: Int) = open(ui.value.bookId, index)
    fun close() { renderJob?.cancel(); translateJob?.cancel(); player.stop(); _ui.value = ReaderUiState() }
    fun dismissLookup() { translateJob?.cancel(); player.stop(); _ui.update { it.copy(selection = "", translation = null, translating = false, saved = false) } }
    fun lookup(text: String, sentence: String) {
        translateJob?.cancel(); player.stop()
        if (text.isBlank()) return
        val id = ui.value.bookId; val page = ui.value.page
        _ui.update { it.copy(selection = text.take(1200), sentence = sentence.take(4000), translating = true, translation = null, saved = false, error = null) }
        translateJob = viewModelScope.launch {
            try {
                val result = repository.translate(text.take(1200), sentence)
                if (ui.value.bookId == id && ui.value.page == page && ui.value.selection == text.take(1200))
                    _ui.update { it.copy(translation = result, translating = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _ui.update { it.copy(error = failure.message, translating = false) } }
        }
    }
    fun saveWord() = viewModelScope.launch {
        val current = ui.value
        val value = current.translation ?: return@launch
        val book = repository.store.export().books.first { it.id == current.bookId }
        repository.saveWord(book, current.page, value)
        if (ui.value.selection == current.selection) _ui.update { it.copy(saved = true) }
    }
    fun bookmark() = viewModelScope.launch { repository.bookmark(ui.value.bookId, ui.value.page) }
    fun theme(value: String) = viewModelScope.launch { repository.theme(value) }
    fun remove(book: ReaderBook) = viewModelScope.launch { repository.remove(book) }
    fun listen() {
        val text = ui.value.selection
        if (text.isBlank()) return
        viewModelScope.launch {
            try { player.play(app.learning.speech(text, true), onError = { e -> _ui.update { it.copy(error = e.message) } }) {} }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _ui.update { it.copy(error = failure.message) } }
        }
    }
    override fun onCleared() { close(); super.onCleared() }
}
