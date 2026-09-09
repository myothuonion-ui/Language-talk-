package com.myothuonion.languagetalk.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myothuonion.languagetalk.LanguageTalkApplication
import com.myothuonion.languagetalk.data.AppSettings
import com.myothuonion.languagetalk.data.ChatEntity
import com.myothuonion.languagetalk.data.KnowledgeSourceEntity
import com.myothuonion.languagetalk.data.MemoryEntity
import com.myothuonion.languagetalk.data.MessageEntity
import com.myothuonion.languagetalk.model.BrainMode
import com.myothuonion.languagetalk.model.TutorConfig
import com.myothuonion.languagetalk.util.GeminiAudioPlayer
import com.myothuonion.languagetalk.util.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkState(
    val isSending: Boolean = false,
    val isRecording: Boolean = false,
    val isSpeaking: Boolean = false,
    val isImporting: Boolean = false,
    val status: String = "Ready",
    val error: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LanguageTalkApplication).repository
    private val recorder = VoiceRecorder(application)
    private val player = GeminiAudioPlayer(application)

    val chats: StateFlow<List<ChatEntity>> = repository.chats.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val memories: StateFlow<List<MemoryEntity>> = repository.memories.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val sources: StateFlow<List<KnowledgeSourceEntity>> = repository.sources.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings()
    )

    private val _currentChatId = MutableStateFlow<Long?>(null)
    val currentChatId: StateFlow<Long?> = _currentChatId
    val messages: StateFlow<List<MessageEntity>> = _currentChatId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.messages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _work = MutableStateFlow(WorkState())
    val work: StateFlow<WorkState> = _work

    fun openChat(id: Long) { _currentChatId.value = id }
    fun closeChat() {
        recorder.stopSilently()
        player.stop()
        _currentChatId.value = null
        _work.value = WorkState()
    }

    fun createChat(config: TutorConfig, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            runWork("Creating conversation…") {
                val id = repository.createChat(config)
                _currentChatId.value = id
                onCreated(id)
            }
        }
    }

    fun send(text: String) {
        val id = _currentChatId.value ?: return
        if (text.isBlank() || _work.value.isSending) return
        viewModelScope.launch {
            _work.update { it.copy(isSending = true, status = "Thinking…", error = null) }
            try {
                val reply = repository.sendMessage(id, text.trim())
                _work.update { it.copy(status = "Answer ready") }
                if (settings.value.autoSpeak) speak(id, reply.spokenText)
            } catch (e: Exception) {
                _work.update { it.copy(error = friendlyError(e), status = "Request failed") }
            } finally {
                _work.update { it.copy(isSending = false) }
            }
        }
    }

    fun toggleRecording() {
        if (_work.value.isSending) return
        if (!_work.value.isRecording) {
            try {
                player.stop()
                recorder.start()
                _work.update { it.copy(isRecording = true, status = "Listening…", error = null) }
            } catch (e: Exception) {
                _work.update { it.copy(error = "Microphone စတင်၍မရပါ: ${e.message}") }
            }
        } else {
            val audio = recorder.stop()
            _work.update { it.copy(isRecording = false) }
            if (audio == null) {
                _work.update { it.copy(error = "အသံမရပါ။ ထပ်မံစမ်းကြည့်ပါ", status = "Ready") }
                return
            }
            val id = _currentChatId.value ?: return
            viewModelScope.launch {
                _work.update { it.copy(isSending = true, status = "Understanding voice…", error = null) }
                try {
                    val reply = repository.sendMessage(id, "", audio)
                    _work.update { it.copy(status = "Speaking…") }
                    speak(id, reply.spokenText)
                } catch (e: Exception) {
                    _work.update { it.copy(error = friendlyError(e), status = "Voice request failed") }
                } finally {
                    _work.update { it.copy(isSending = false) }
                }
            }
        }
    }

    fun speak(chatId: Long, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _work.update { it.copy(isSpeaking = true, status = "Generating Gemini voice…", error = null) }
            try {
                val audio = repository.synthesize(chatId, text)
                _work.update { it.copy(status = "Speaking…") }
                player.play(audio) {
                    _work.update { it.copy(isSpeaking = false, status = "Ready") }
                }
            } catch (e: Exception) {
                _work.update { it.copy(isSpeaking = false, status = "Voice failed", error = friendlyError(e)) }
            }
        }
    }

    fun previewVoice(voice: String, style: String, text: String) {
        viewModelScope.launch {
            _work.update { it.copy(isSpeaking = true, status = "Generating voice preview…", error = null) }
            try {
                val audio = repository.previewVoice(voice, style, text)
                player.play(audio) {
                    _work.update { it.copy(isSpeaking = false, status = "Ready") }
                }
            } catch (e: Exception) {
                _work.update { it.copy(isSpeaking = false, error = friendlyError(e), status = "Preview failed") }
            }
        }
    }

    fun stopSpeaking() {
        player.stop()
        _work.update { it.copy(isSpeaking = false, status = "Ready") }
    }

    fun addMemory(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        viewModelScope.launch { runWork("Saving memory…") { repository.addMemory(title.trim(), content.trim()) } }
    }

    fun toggleMemory(item: MemoryEntity) = viewModelScope.launch { repository.toggleMemory(item) }
    fun deleteMemory(item: MemoryEntity) = viewModelScope.launch { repository.deleteMemory(item) }
    fun toggleSource(item: KnowledgeSourceEntity) = viewModelScope.launch { repository.toggleSource(item) }
    fun deleteSource(item: KnowledgeSourceEntity) = viewModelScope.launch { repository.deleteSource(item) }
    fun deleteChat(id: Long) = viewModelScope.launch { repository.deleteChat(id) }

    fun importSource(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            _work.update { it.copy(isImporting = true, status = "Reading and analyzing file…", error = null) }
            try {
                val metadata = withContext(Dispatchers.IO) {
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    var name = "Imported context"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read file")
                    Triple(name, mime, bytes)
                }
                repository.analyzeAndAddSource(metadata.first, metadata.second, uri.toString(), metadata.third)
                _work.update { it.copy(status = "Context imported") }
            } catch (e: Exception) {
                _work.update { it.copy(error = friendlyError(e), status = "Import failed") }
            } finally {
                _work.update { it.copy(isImporting = false) }
            }
        }
    }

    fun saveSettings(value: AppSettings, geminiKey: String?, nvidiaKey: String?) {
        viewModelScope.launch {
            runWork("Saving settings…") { repository.saveSettings(value, geminiKey, nvidiaKey) }
        }
    }

    fun hasGeminiKey() = repository.hasGeminiKey()
    fun hasNvidiaKey() = repository.hasNvidiaKey()

    fun clearError() = _work.update { it.copy(error = null) }

    private suspend fun runWork(status: String, block: suspend () -> Unit) {
        _work.update { it.copy(status = status, error = null) }
        try {
            block()
            _work.update { it.copy(status = "Ready") }
        } catch (e: Exception) {
            _work.update { it.copy(status = "Failed", error = friendlyError(e)) }
        }
    }

    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("API key", ignoreCase = true) -> message
            message.contains("401") || message.contains("403") -> "API key သို့မဟုတ် model access ကိုစစ်ပါ"
            message.contains("429") || message.contains("quota", ignoreCase = true) -> "API limit ပြည့်နေသည်။ ခဏစောင့်ပြီးပြန်စမ်းပါ"
            message.contains("Unable to resolve host", ignoreCase = true) -> "Internet connection ကိုစစ်ပါ"
            else -> message.ifBlank { "မသိသောအမှားဖြစ်နေသည်" }
        }
    }

    override fun onCleared() {
        recorder.stopSilently()
        player.stop()
        super.onCleared()
    }
}
