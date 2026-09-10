package com.myothuonion.languagetalk.network

import android.content.Context
import com.myothuonion.languagetalk.util.GeminiAudioPlayer
import com.myothuonion.languagetalk.util.LivePcmRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

data class HandsFreeTurn(
    val heardText: String,
    val replyText: String,
    val speech: AudioPayload
)

/**
 * Reliable hands-free mode used automatically if Gemini Live cannot establish a socket.
 * Local VAD ends each utterance; Gemini then transcribes, answers, and speaks without a send tap.
 */
class HandsFreeRestSession(
    context: Context,
    private val onTurn: suspend (AudioPayload) -> HandsFreeTurn
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorder = LivePcmRecorder()
    private val player = GeminiAudioPlayer(context.applicationContext)
    private val _state = MutableStateFlow(
        LiveState(
            phase = LivePhase.CONNECTING,
            activeModel = "Gemini reliable voice",
            fallbackUsed = true,
            diagnostic = "Preparing voice"
        )
    )
    val state: StateFlow<LiveState> = _state

    private val stopped = AtomicBoolean(false)
    private val processing = AtomicBoolean(false)
    private val captureLock = Any()
    private val preRoll = ArrayDeque<ByteArray>()
    private val speechChunks = mutableListOf<ByteArray>()
    private var speechDetected = false
    private var loudChunkCount = 0
    private var lastVoiceAt = 0L
    private var noiseFloor = .012f

    fun start() {
        if (stopped.get()) return
        _state.update {
            it.copy(
                phase = LivePhase.LISTENING,
                connected = true,
                diagnostic = "Listening",
                error = null
            )
        }
        startRecorder()
    }

    fun setMicEnabled(enabled: Boolean) {
        _state.update {
            it.copy(
                micEnabled = enabled,
                inputLevel = if (enabled) it.inputLevel else 0f,
                diagnostic = if (enabled) "Listening" else "Microphone off"
            )
        }
        if (!enabled) {
            recorder.stop()
            resetCapture()
        } else if (!processing.get()) {
            startRecorder()
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        recorder.stop()
        player.stop()
        scope.cancel()
        _state.value = _state.value.copy(
            phase = LivePhase.DISCONNECTED,
            connected = false,
            inputLevel = 0f,
            outputLevel = 0f
        )
    }

    private fun startRecorder() {
        if (stopped.get() || processing.get() || !_state.value.micEnabled || recorder.isRunning) return
        runCatching {
            recorder.start(::onAudioChunk)
        }.onFailure { failure ->
            _state.update {
                it.copy(
                    phase = LivePhase.ERROR,
                    connected = false,
                    diagnostic = "Microphone unavailable",
                    error = failure.message ?: "Microphone စတင်၍မရပါ"
                )
            }
        }
    }

    private fun onAudioChunk(bytes: ByteArray, level: Float) {
        if (stopped.get() || processing.get() || !_state.value.micEnabled) return
        _state.update { it.copy(inputLevel = level) }
        val now = System.currentTimeMillis()
        var completedSpeech: ByteArray? = null

        synchronized(captureLock) {
            val threshold = maxOf(.035f, noiseFloor * 2.7f)
            if (!speechDetected) {
                noiseFloor = noiseFloor * .94f + min(level, .07f) * .06f
                preRoll.addLast(bytes)
                while (preRoll.size > 4) preRoll.removeFirst()
                loudChunkCount = if (level >= threshold) loudChunkCount + 1 else 0
                if (loudChunkCount >= 2) {
                    speechDetected = true
                    speechChunks.addAll(preRoll)
                    preRoll.clear()
                    lastVoiceAt = now
                    _state.update { it.copy(diagnostic = "Hearing you") }
                }
            } else {
                speechChunks += bytes
                if (level >= threshold * .82f) lastVoiceAt = now
                val endedBySilence = now - lastVoiceAt >= 850 && speechChunks.size >= 7
                val endedByLimit = speechChunks.size >= 180
                if (endedBySilence || endedByLimit) {
                    completedSpeech = joinChunks(speechChunks)
                    speechChunks.clear()
                    preRoll.clear()
                    speechDetected = false
                    loudChunkCount = 0
                    processing.set(true)
                }
            }
        }

        completedSpeech?.let { pcm ->
            scope.launch {
                recorder.stop()
                processTurn(pcm)
            }
        }
    }

    private suspend fun processTurn(pcm: ByteArray) {
        _state.update {
            it.copy(
                phase = LivePhase.THINKING,
                inputLevel = 0f,
                userCaption = "အသံကို နားလည်နေပါတယ်…",
                aiCaption = "",
                diagnostic = "Understanding"
            )
        }
        try {
            val turn = onTurn(AudioPayload(pcm16Wav(pcm), "audio/wav"))
            val newLines = buildList {
                addAll(_state.value.lines)
                if (turn.heardText.isNotBlank()) add(LiveLine(LiveSpeaker.USER, turn.heardText))
                if (turn.replyText.isNotBlank()) add(LiveLine(LiveSpeaker.AI, turn.replyText))
            }.takeLast(40)
            _state.update {
                it.copy(
                    phase = LivePhase.SPEAKING,
                    userCaption = turn.heardText,
                    aiCaption = turn.replyText,
                    lines = newLines,
                    outputLevel = .48f,
                    diagnostic = "Speaking",
                    error = null
                )
            }
            player.play(turn.speech) {
                scope.launch { resumeListening() }
            }
        } catch (failure: Throwable) {
            processing.set(false)
            _state.update {
                it.copy(
                    phase = LivePhase.ERROR,
                    connected = false,
                    inputLevel = 0f,
                    outputLevel = 0f,
                    diagnostic = "Voice request failed",
                    error = failure.message ?: "Voice request မအောင်မြင်ပါ"
                )
            }
        }
    }

    private fun resumeListening() {
        if (stopped.get()) return
        processing.set(false)
        resetCapture()
        _state.update {
            it.copy(
                phase = LivePhase.LISTENING,
                connected = true,
                userCaption = "",
                aiCaption = "",
                inputLevel = 0f,
                outputLevel = 0f,
                diagnostic = if (it.micEnabled) "Listening" else "Microphone off"
            )
        }
        startRecorder()
    }

    private fun resetCapture() = synchronized(captureLock) {
        preRoll.clear()
        speechChunks.clear()
        speechDetected = false
        loudChunkCount = 0
        lastVoiceAt = 0L
    }

    private fun joinChunks(chunks: List<ByteArray>): ByteArray {
        val output = ByteArray(chunks.sumOf(ByteArray::size))
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(output, offset)
            offset += chunk.size
        }
        return output
    }

    private fun pcm16Wav(pcm: ByteArray): ByteArray {
        val headerSize = 44
        return ByteBuffer.allocate(headerSize + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(LivePcmRecorder.SAMPLE_RATE)
            putInt(LivePcmRecorder.SAMPLE_RATE * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
            put(pcm)
        }.array()
    }
}
