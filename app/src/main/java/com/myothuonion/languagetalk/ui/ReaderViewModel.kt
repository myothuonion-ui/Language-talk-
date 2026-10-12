package com.myothuonion.languagetalk.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myothuonion.languagetalk.LanguageTalkApplication
import com.myothuonion.languagetalk.data.*
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// A level meter is local capture; only an actual provider transcript represents recognized speech.
data class ReaderUiState(val bookId: String = "", val page: Int = 0, val pageData: ReadablePdfPage? = null,
    val loading: Boolean = false, val translating: Boolean = false, val selection: String = "", val sentence: String = "",
    val translation: ReaderTranslation? = null, val saved: Boolean = false, val error: String? = null,
    val regions: List<PageRect> = emptyList(), val startOffset: Int = -1, val endOffset: Int = -1,
    val explanation: PageExplanation? = null, val explaining: Boolean = false, val explanationOpen: Boolean = false,
    val sourceFocus: String = "", val speaking: String = "", val preparingAudio: Boolean = false,
    val slow: Boolean = true, val audioSource: String = "", val notice: String = "", val undoId: String = "")

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LanguageTalkApplication
    private val repository = app.reader
    private val engine = PdfPageReader(application)
    private val player = GeminiAudioPlayer(application)
    private val renderLock = Mutex()
    private val pages = linkedMapOf<String, ReadablePdfPage>()
    val learning = repository.store.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LearningState())
    val annotations = repository.annotations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val audioAssets = repository.audioAssets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _ui = MutableStateFlow(ReaderUiState())
    val ui = _ui.asStateFlow()
    private var renderJob: Job? = null
    private var translateJob: Job? = null
    private var explainJob: Job? = null
    private var audioJob: Job? = null
    private var explanationGeneration = 0L
    init { viewModelScope.launch { runCatching { repository.initialize() }.onFailure { e -> _ui.update { it.copy(error = e.message) } } } }

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { _ui.update { it.copy(error = failure.message) } }
    }
    fun import(uri: Uri) = action {
        _ui.update { it.copy(loading = true, error = null) }
        try { open(repository.import(uri).id) } finally { _ui.update { it.copy(loading = false) } }
    }
    suspend fun loadPage(book: ReaderBook, index: Int): ReadablePdfPage = renderLock.withLock {
        val key = book.id + ":" + index
        pages.remove(key)?.let { pages[key] = it; return@withLock it }
        engine.render(repository.file(book), book.id, index).also { data ->
            pages[key] = data
            // References held by Compose remain valid; GC releases evicted bitmaps after those references disappear.
            while (pages.size > 5) pages.remove(pages.keys.first())
        }
    }
    fun open(id: String, page: Int? = null) {
        renderJob?.cancel(); translateJob?.cancel(); explainJob?.cancel(); stopAudio()
        renderJob = viewModelScope.launch {
            _ui.value = ReaderUiState(bookId = id, page = page ?: 0, loading = true)
            try {
                val book = repository.store.export().books.first { it.id == id }
                val index = (page ?: book.page).coerceIn(0, book.pageCount - 1)
                _ui.update { it.copy(page = index) }
                val data = loadPage(book, index)
                val cached = repository.cachedPage(id, index)?.takeIf { it.fingerprint == book.fingerprint.ifBlank { book.id } }
                _ui.update { it.copy(pageData = data, loading = false, explanation = cached) }
                repository.page(id, index)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _ui.update { it.copy(error = failure.message, loading = false) } }
        }
    }
    fun activatePage(book: ReaderBook, index: Int, data: ReadablePdfPage) {
        if (ui.value.page == index) return
        renderJob?.cancel(); translateJob?.cancel(); explainJob?.cancel(); stopAudio()
        _ui.value = ReaderUiState(bookId = book.id, page = index, pageData = data)
        action { repository.page(book.id, index) }
    }
    fun page(index: Int) { if (index != ui.value.page || ui.value.pageData == null) open(ui.value.bookId, index) }
    fun close() { renderJob?.cancel(); translateJob?.cancel(); explainJob?.cancel(); stopAudio(); pages.clear(); _ui.value = ReaderUiState() }
    fun dismissLookup() { translateJob?.cancel(); _ui.update { it.copy(selection = "", translation = null, translating = false, saved = false, regions = emptyList(), error = null) } }
    fun lookup(text: String, sentence: String, regions: List<PageRect> = emptyList(), start: Int = -1, end: Int = -1,
        translate: Boolean = true) {
        translateJob?.cancel(); stopAudio()
        if (text.isBlank()) return
        _ui.update { it.copy(selection = text.take(1200), sentence = sentence.take(4000), regions = regions,
            startOffset = start, endOffset = end, translating = false, translation = null, saved = false, error = null) }
        if (translate) translateSelection()
    }
    fun translateSelection() {
        translateJob?.cancel()
        val current = ui.value
        if (current.selection.isBlank()) return
        _ui.update { it.copy(translating = true, error = null) }
        translateJob = viewModelScope.launch {
            try {
                val result = repository.translate(current.selection, current.sentence)
                if (ui.value.bookId == current.bookId && ui.value.page == current.page && ui.value.selection == current.selection)
                    _ui.update { it.copy(translation = result, translating = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _ui.update { it.copy(error = failure.message, translating = false) } }
        }
    }
    fun annotate(kind: String, note: String = "", color: Long = 0xFFFFD666) = action {
        val current = ui.value
        val book = repository.store.export().books.first { it.id == current.bookId }
        val annotation = repository.annotate(book, current.page, current.selection, current.sentence, kind, current.regions,
            current.startOffset, current.endOffset, note, color)
        dismissLookup()
        _ui.update { it.copy(notice = "သိမ်းပြီးပြီ · စာမျက်နှာ " + (current.page + 1), undoId = annotation.id) }
    }
    fun undo() = action { repository.deleteAnnotation(ui.value.undoId); _ui.update { it.copy(notice = "", undoId = "") } }
    fun practiceSaved(value: ReaderAnnotation, onSaved: (String) -> Unit) = action {
        val book = ReaderBook(value.bookId, value.bookTitle, pageCount = value.page + 1, fingerprint = value.fingerprint)
        repository.saveWord(book, value.page, ReaderTranslation(value.id, value.quote, value.quote, value.note, "", value.context))
        repository.store.export().cards.lastOrNull { it.bookId == value.bookId && it.example == value.quote }?.let { onSaved(it.id) }
    }
    fun editAnnotation(value: ReaderAnnotation, note: String = value.note, color: Long = value.color) = action { repository.edit(value, note, color) }
    fun deleteAnnotation(id: String) = action { repository.deleteAnnotation(id) }
    fun saveWord() = action {
        val current = ui.value
        val value = current.translation ?: return@action
        val book = repository.store.export().books.first { it.id == current.bookId }
        repository.saveWord(book, current.page, value)
        if (ui.value.selection == current.selection) _ui.update { it.copy(saved = true) }
    }
    fun bookmark() = action { repository.bookmark(ui.value.bookId, ui.value.page) }
    fun theme(value: String) = action { repository.theme(value) }
    fun reading(flow: String? = null, mode: String? = null, fontSize: Float? = null) = action { repository.reading(flow, mode, fontSize) }
    fun zoom(value: Float) = action { repository.zoom(ui.value.bookId, value) }
    fun remove(book: ReaderBook) = action { repository.remove(book) }
    fun showExplanation(show: Boolean = true) { _ui.update { it.copy(explanationOpen = show) } }
    fun focusSource(id: String) { _ui.update { it.copy(sourceFocus = id) } }
    suspend fun chapters(id: String) = app.books.courses().firstOrNull { it.id == id }?.chapters.orEmpty()
    fun clearFocus() { _ui.update { it.copy(sourceFocus = "") } }
    fun explain(regenerate: Boolean = false) {
        val current = ui.value
        val data = current.pageData ?: return
        val token = ++explanationGeneration
        explainJob?.cancel()
        _ui.update { it.copy(explaining = true, explanationOpen = true, error = null) }
        explainJob = viewModelScope.launch {
            try {
                val book = repository.store.export().books.first { it.id == current.bookId }
                val value = repository.explainPage(book, current.page, data.layer, data.bitmap, regenerate)
                if (explanationGeneration == token && ui.value.bookId == current.bookId && ui.value.page == current.page) _ui.update { it.copy(explanation = value) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _ui.update { it.copy(error = failure.message) } }
            finally { if (explanationGeneration == token && ui.value.bookId == current.bookId && ui.value.page == current.page) _ui.update { it.copy(explaining = false) } }
        }
    }
    fun cancelExplanation() { explainJob?.cancel(); _ui.update { it.copy(explaining = false) } }
    fun pace(slow: Boolean) { stopAudio(); _ui.update { it.copy(slow = slow) } }
    fun stopAudio() { audioJob?.cancel(); player.stop(); _ui.update { it.copy(speaking = "", preparingAudio = false) } }
    fun listen(text: String = ui.value.selection) {
        if (text.isBlank()) return
        stopAudio()
        val current = ui.value
        _ui.update { it.copy(preparingAudio = true, audioSource = "AI အသံ", error = null) }
        audioJob = viewModelScope.launch {
            try {
                val audio = app.learning.speech(text, current.slow)
                if (ui.value.bookId == current.bookId && ui.value.page == current.page) {
                    _ui.update { it.copy(speaking = text, preparingAudio = false) }
                    player.play(audio, onError = { e -> _ui.update { it.copy(error = e.message, speaking = "") } }) { _ui.update { it.copy(speaking = "") } }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _ui.update { it.copy(error = failure.message, preparingAudio = false) } }
        }
    }
    fun importAudio(track: Int, uri: Uri) = action { repository.importAudio(ui.value.bookId, track, uri) }
    fun playOriginal(asset: BookAudioAsset) {
        stopAudio()
        audioJob = action {
            val audio = repository.originalAudio(asset)
            _ui.update { it.copy(speaking = asset.title, audioSource = "ကိုယ်တင်ထားတဲ့ Track " + asset.track) }
            player.play(audio, onError = { e -> _ui.update { it.copy(error = e.message, speaking = "") } }) { _ui.update { it.copy(speaking = "") } }
        }
    }
    fun practiceCard(text: String = ui.value.selection, onSaved: (String) -> Unit) = action {
        val current = ui.value
        val book = repository.store.export().books.first { it.id == current.bookId }
        val translation = current.translation?.takeIf { it.selected == text }
        repository.saveWord(book, current.page, translation ?: ReaderTranslation(text, text, text, "", "", current.sentence))
        val id = repository.store.export().cards.lastOrNull { it.bookId == book.id && it.example == text }?.id ?: return@action
        onSaved(id)
    }
    override fun onCleared() { close(); super.onCleared() }
}
