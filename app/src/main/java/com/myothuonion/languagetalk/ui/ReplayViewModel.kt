package com.myothuonion.languagetalk.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myothuonion.languagetalk.LanguageTalkApplication
import com.myothuonion.languagetalk.data.ReplayClip
import com.myothuonion.languagetalk.util.ReplayPlayer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReplayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LanguageTalkApplication).replays
    val sessions = repository.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _selected = MutableStateFlow<String?>(null)
    val selected = _selected.asStateFlow()
    private val _speaker = MutableStateFlow("BOTH")
    val speaker = _speaker.asStateFlow()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val clips = _selected.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.clips(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val player = ReplayPlayer(repository::file)
    val playback = player.state
    private val _notice = MutableStateFlow("")
    val notice = _notice.asStateFlow()
    init { viewModelScope.launch { combine(clips, speaker) { clips, speaker ->
        clips.filter { speaker == "BOTH" || it.speaker == speaker }
    }.collect { player.load(it) } } }
    fun open(id: String?) { player.stop(); _selected.value = id; _speaker.value = "BOTH"; _notice.value = "" }
    fun speaker(value: String) { _speaker.value = value }
    fun playPause() { if (playback.value.playing) player.pause() else player.play() }
    fun seek(value: Long) = player.seek(value)
    fun seekClip(clip: ReplayClip) {
        val list = clips.value.filter { speaker.value == "BOTH" || it.speaker == speaker.value }.filter { it.durationMs > 0 && repository.file(it) != null }
        val index = list.indexOfFirst { it.id == clip.id }
        if (index >= 0) { player.seek(list.take(index).sumOf { it.durationMs }); player.play() }
        else _notice.value = "ဒီစာကြောင်းအတွက် မူရင်းအသံ မသိမ်းထားပါ။"
    }
    fun speed(value: Float) = player.speed(value)
    fun markA() = player.markA()
    fun markB() = player.markB()
    fun clearLoop() = player.clearLoop()
    fun stop() = player.stop()
    fun delete(id: String) = viewModelScope.launch { player.stop(); repository.delete(id); if (selected.value == id) open(null) }
    fun export(uri: Uri, id: String) = viewModelScope.launch {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { repository.export(id, it) }
                ?: error("ဖိုင်သိမ်းမရပါ။")
            _notice.value = "စကားဝိုင်းနဲ့ အသံဖိုင်တွေ ZIP ထုတ်ပြီးပြီ။"
        } catch (_: Exception) { _notice.value = "အသံမှတ်တမ်း ထုတ်မရပါ။" }
    }
    override fun onCleared() { player.close(); super.onCleared() }
}
