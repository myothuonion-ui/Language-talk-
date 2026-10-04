package com.myothuonion.languagetalk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myothuonion.languagetalk.LanguageTalkApplication
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.network.*
import com.myothuonion.languagetalk.util.GeminiAudioPlayer
import com.myothuonion.languagetalk.util.VoiceRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BookUiState(val courses: List<BookCourse> = emptyList(), val bookId: String = "ttmik-beginner",
    val busy: Boolean = false, val recording: Boolean = false, val explanation: String = "",
    val error: String? = null)

class BookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LanguageTalkApplication).books
    val shelf = repository.shelf.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookShelfState())
    private val _state = MutableStateFlow(BookUiState())
    val state: StateFlow<BookUiState> = _state
    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live
    private val recorder = VoiceRecorder(application)
    private val player = GeminiAudioPlayer(application)
    private var session: HandsFreeRestSession? = null
    private var collector: Job? = null
    private var operation: Job? = null
    private var generation = 0L

    init {
        work {
            val courses = repository.courses()
            val id = repository.shelf.first().lastBookId.takeIf { candidate -> courses.any { it.id == candidate } } ?: courses.first().id
            _state.update { it.copy(courses = courses, bookId = id) }
            repository.open(id)
            prepareText(id)
        }
    }

    private fun work(block: suspend () -> Unit) {
        val token = ++generation
        operation?.cancel()
        operation = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(error = failure.message ?: "ထပ်စမ်းပါ") } }
            finally { if (token == generation) _state.update { it.copy(busy = false) } }
        }
    }

    private suspend fun prepareText(id: String) {
        val progress = repository.progress(id)
        val section = BookEngine.section(repository.book(id), progress)
        if (section.turns.isNotEmpty() || section.exercises.isNotEmpty()) repository.prepare(id, false)
        val cached = repository.shelf.first().explanations.firstOrNull { it.key == BookEngine.key(progress, section) + "/myanmar" }?.text.orEmpty()
        _state.update { it.copy(explanation = cached) }
    }

    private fun change(block: suspend (String) -> Unit) {
        stopVoice()
        val id = state.value.bookId
        _state.update { it.copy(explanation = "") }
        work { block(id); prepareText(id) }
    }

    fun selectBook(id: String) {
        stopVoice()
        _state.update { it.copy(bookId = id, explanation = "") }
        work { repository.open(id); prepareText(id) }
    }
    fun selectChapter(number: Int) = change { repository.chooseChapter(it, number) }
    fun resume() = change { repository.open(it) }
    fun restart(role: String? = null) = change { repository.restart(it, role) }
    fun next() = change { repository.next(it) }
    fun previous() = change { repository.previous(it) }
    fun pace(slow: Boolean) = change { repository.pace(it, slow) }

    fun explain(current: Boolean = false) {
        if (state.value.busy || live.value.connected) return
        val id = state.value.bookId
        work { val explanation = repository.explain(id, current); _state.update { it.copy(explanation = explanation) } }
    }

    fun send(text: String) {
        if (text.isBlank() || state.value.busy || live.value.connected) return
        val id = state.value.bookId
        work { repository.answer(id, text); prepareText(id) }
    }

    fun speak(text: String) {
        if (text.isBlank() || state.value.busy || live.value.connected) return
        val id = state.value.bookId
        player.stop()
        work { player.play(repository.speech(id, text)) {} }
    }

    fun toggleRecording() {
        if (state.value.busy || live.value.connected) return
        if (!state.value.recording) {
            player.stop()
            runCatching { recorder.start() }.onSuccess { _state.update { it.copy(recording = true, error = null) } }
                .onFailure { failure -> _state.update { it.copy(error = failure.message ?: "Microphone စတင်၍မရပါ") } }
        } else {
            val audio = recorder.stop()
            _state.update { it.copy(recording = false) }
            if (audio == null) { _state.update { it.copy(error = "အသံမရပါ။ ထပ်စမ်းပါ") }; return }
            val id = state.value.bookId
            work { repository.answer(id, audio = audio); prepareText(id) }
        }
    }

    fun startVoice(silenceMs: Int) {
        if (state.value.busy || live.value.connected) return
        stopVoice()
        val id = state.value.bookId
        val created = HandsFreeRestSession(getApplication(), silenceMs,
            opening = { repository.prepare(id, true) ?: error("အသံမရပါ") }) { audio ->
            repository.answer(id, audio = audio, voice = true) ?: error("အသံမရပါ")
        }
        session = created
        collector = viewModelScope.launch {
            created.state.collect { value ->
                if (value.phase == LivePhase.LISTENING && BookEngine.finished(repository.book(id), repository.progress(id))) stopVoice()
                else _live.value = value.copy(activeModel = "Book conversation", diagnostic = "စာအုပ်အလှည့်အတိုင်း")
            }
        }
        created.start()
    }

    fun toggleMic() { session?.setMicEnabled(!live.value.micEnabled) }
    fun microphoneDenied() { _state.update { it.copy(error = "အသံလေ့ကျင့်ဖို့ Microphone permission ဖွင့်ပေးပါ") } }
    fun stopVoice() {
        collector?.cancel(); collector = null
        session?.stop(); session = null
        recorder.stopSilently(); player.stop()
        _live.value = LiveState()
        _state.update { it.copy(recording = false) }
    }
    fun close() { operation?.cancel(); stopVoice() }
    suspend fun sourcePdf(id: String) = repository.sourcePdf(id)

    fun applicationPractice(): TutorConfig? {
        val book = state.value.courses.firstOrNull { it.id == state.value.bookId } ?: return null
        val progress = shelf.value.progress.firstOrNull { it.bookId == book.id } ?: return null
        val chapter = BookEngine.chapter(book, progress)
        return TutorConfig(topic = "${book.level} · ${chapter.number} ${chapter.title} · ကိုယ့်ဘဝနဲ့ အသုံးချခြင်း",
            practiceMode = PracticeMode.ROLEPLAY, level = book.level,
            customPrompt = """Practice applying only the grammar and vocabulary from this completed book chapter to the learner's real life.
                Use their explicit profile/context; do not assume textbook characters are their personal identity.
                Ask one short Korean question, wait, and explain a useful correction in Myanmar. Do not recite both roles.
                This is additional application practice; never claim generated sentences are the original textbook.
                Source grammar:
                ${chapter.sections.filter { it.kind == "GRAMMAR" }.joinToString("\n") { it.sourceText }.take(11000)}""".trimIndent())
    }

    override fun onCleared() { close(); super.onCleared() }
}
