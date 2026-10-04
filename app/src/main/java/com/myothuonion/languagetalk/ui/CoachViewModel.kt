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

data class CoachUiState(val open: Boolean = false, val unitId: String = "", val reviewId: String? = null,
    val placement: Boolean = false, val busy: Boolean = false, val recording: Boolean = false,
    val slow: Boolean = true, val heard: String = "", val feedback: String = "", val error: String? = null)

class CoachViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LanguageTalkApplication).learning
    val curriculum = repository.curriculum
    val learning = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LearningState())
    private val _ui = MutableStateFlow(CoachUiState())
    val ui = _ui.asStateFlow()
    private val _live = MutableStateFlow(LiveState())
    val live = _live.asStateFlow()
    private val recorder = VoiceRecorder(application)
    private val player = GeminiAudioPlayer(application)
    private var operation: Job? = null
    private var collector: Job? = null
    private var session: HandsFreeRestSession? = null
    private var generation = 0L

    private fun work(block: suspend () -> Unit) {
        val token = ++generation
        operation?.cancel()
        operation = viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _ui.update { it.copy(error = failure.message ?: "ထပ်စမ်းပါ။") } }
            finally { if (generation == token) _ui.update { it.copy(busy = false) } }
        }
    }
    fun openToday() { stopVoice(); work {
        val state = repository.store.export()
        val card = CoachEngine.due(state, System.currentTimeMillis()).firstOrNull()
        val unit = repository.currentUnit()
        repository.select(unit.id)
        _ui.value = CoachUiState(open = true, unitId = unit.id, reviewId = card?.id, slow = state.level < 3)
    } }
    fun openUnit(id: String) { stopVoice(); work {
        repository.select(id)
        val unit = curriculum.first { it.id == id }
        _ui.value = CoachUiState(open = true, unitId = id, slow = unit.level < 3)
    } }
    fun level(value: Int) { stopVoice(); work { repository.chooseLevel(value) } }
    fun placement() { stopVoice(); work {
        repository.beginPlacement()
        _ui.value = CoachUiState(open = true, placement = true, unitId = curriculum.first().id)
    } }
    fun openReview(id: String) { stopVoice(); work {
        _ui.value = CoachUiState(open = true, unitId = repository.currentUnit().id, reviewId = id)
    } }
    fun next() { stopVoice(); work {
        val unit = repository.currentUnit()
        _ui.value = CoachUiState(open = true, unitId = unit.id, slow = unit.level < 3)
        repository.select(unit.id)
    } }
    fun restart() { stopVoice(); work { repository.restart(ui.value.unitId); _ui.update { it.copy(feedback = "", heard = "") } } }
    fun closeLesson() { operation?.cancel(); generation++; stopVoice(); _ui.update { it.copy(open = false, busy = false, placement = false, reviewId = null) } }
    fun slow(value: Boolean) { stopVoice(); _ui.update { it.copy(slow = value) } }
    fun denyMicrophone() { _ui.update { it.copy(error = "Microphone permission ဖွင့်ပေးပါ။") } }
    private suspend fun assess(text: String = "", audio: AudioPayload? = null) {
        val current = ui.value
        val result = repository.answer(text, audio, current.reviewId, current.placement, current.unitId.takeIf { it.isNotBlank() })
        _ui.update { it.copy(heard = result.heard, feedback = result.feedback) }
    }
    fun send(text: String) {
        if (text.isBlank() || ui.value.busy || live.value.connected || ui.value.recording) return
        work { assess(text) }
    }
    fun record() {
        if (ui.value.busy || live.value.connected) return
        if (!ui.value.recording) {
            player.stop()
            runCatching { recorder.start() }.onSuccess { _ui.update { it.copy(recording = true, error = null) } }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        } else {
            val audio = recorder.stop()
            _ui.update { it.copy(recording = false) }
            if (audio == null) { denyMicrophone(); return }
            work { assess(audio = audio) }
        }
    }
    fun listen(text: String, alternate: Boolean = false) {
        if (ui.value.busy || live.value.connected || ui.value.recording) return
        player.stop()
        work { player.play(repository.speech(text, ui.value.slow, alternate),
            onError = { failure -> _ui.update { it.copy(error = failure.message) } }) {} }
    }
    fun explain(task: CoachTask) {
        if (ui.value.busy || live.value.connected) return
        val unit = curriculum.firstOrNull { it.id == ui.value.unitId } ?: return
        _ui.update { it.copy(feedback = unit.explanation + "\n" + task.hint) }
    }
    fun playRecording(attempt: CoachAttempt) = work {
        player.stop()
        repository.recording(attempt)?.let { player.play(it, onError = { e -> _ui.update { it.copy(error = e.message) } }) {} }
            ?: error("အသံဖိုင် မရှိတော့ပါ။")
    }
    fun deleteRecording(attempt: CoachAttempt) = work { player.stop(); repository.deleteRecording(attempt) }
    fun deleteCard(id: String) = work { repository.removeCard(id) }
    fun study(seconds: Long) { viewModelScope.launch { repository.study(seconds) } }
    fun minutes(value: Int) = work { repository.store.update { it.copy(dailyMinutes = value.coerceIn(5, 60)) } }
    private suspend fun finished(): Boolean {
        val state = repository.store.export()
        return when {
            ui.value.placement -> state.placementDone
            ui.value.reviewId != null -> state.cards.firstOrNull { it.id == ui.value.reviewId }?.dueAt?.let { it > System.currentTimeMillis() } ?: true
            else -> CoachEngine.progress(state, ui.value.unitId).completed
        }
    }
    fun startVoice(silenceMs: Int) {
        if (ui.value.busy || live.value.connected) return
        stopVoice()
        val current = ui.value
        val created = HandsFreeRestSession(getApplication(), silenceMs,
            opening = { repository.opening(current.slow, current.reviewId, current.placement, current.unitId) }) { audio ->
            assess(audio = audio)
            if (finished()) HandsFreeTurn(ui.value.heard, ui.value.feedback,
                repository.speech("수고하셨습니다.", current.slow))
            else {
                val opening = repository.opening(current.slow, current.reviewId, current.placement, current.unitId, ui.value.feedback)
                opening.copy(heardText = ui.value.heard, replyText = ui.value.feedback + "\n" + opening.replyText)
            }
        }
        session = created
        collector = viewModelScope.launch { created.state.collect { value ->
            if (value.phase == LivePhase.LISTENING && finished()) stopVoice()
            else _live.value = value.copy(activeModel = "Korean Coach", diagnostic = "သင်ခန်းစာအစီအစဉ်အတိုင်း")
        } }
        created.start()
    }
    fun stopVoice() {
        collector?.cancel(); collector = null; session?.stop(); session = null
        recorder.stopSilently(); player.stop()
        _live.value = LiveState()
        _ui.update { it.copy(recording = false) }
    }
    fun stopAll() { operation?.cancel(); generation++; stopVoice(); _ui.update { it.copy(busy = false) } }
    override fun onCleared() { stopAll(); super.onCleared() }
}
