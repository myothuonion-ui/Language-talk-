package com.myothuonion.languagetalk.util

import android.media.MediaPlayer
import android.media.PlaybackParams
import com.myothuonion.languagetalk.data.ReplayClip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class ReplayPlayback(val positionMs: Long = 0, val durationMs: Long = 0, val clipId: Long = 0,
    val playing: Boolean = false, val preparing: Boolean = false, val speed: Float = 1f,
    val loopStart: Long? = null, val loopEnd: Long? = null, val error: String? = null)

/** Seekable local playlist; each speaker's actual saved audio remains a separate file. */
class ReplayPlayer(private val file: (ReplayClip) -> File?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ReplayPlayback())
    val state = _state.asStateFlow()
    private var clips = emptyList<ReplayClip>()
    private var media: MediaPlayer? = null
    private var index = 0
    private var prepared = false
    private var shouldPlay = false
    private fun offset(index: Int) = clips.take(index).sumOf { it.durationMs }
    init { scope.launch {
        while (isActive) {
            delay(80)
            if (prepared && media != null) {
                val position = offset(index) + runCatching { media!!.currentPosition.toLong() }.getOrDefault(0)
                _state.update { it.copy(positionMs = position.coerceIn(0, it.durationMs)) }
                val end = state.value.loopEnd; val start = state.value.loopStart
                if (shouldPlay && end != null && start != null && position >= end) seek(start)
            }
        }
    } }
    fun load(values: List<ReplayClip>) {
        stop(); clips = values.filter { it.durationMs > 0 && file(it) != null }
        _state.value = ReplayPlayback(durationMs = clips.sumOf { it.durationMs }, speed = state.value.speed)
    }
    fun play() {
        if (clips.isEmpty()) { _state.update { it.copy(error = "သိမ်းထားတဲ့ အသံဖိုင် မရှိပါ။") }; return }
        shouldPlay = true
        if (prepared && media != null && state.value.positionMs < state.value.durationMs) {
            media?.start(); _state.update { it.copy(playing = true) }
        } else openAt(if (state.value.positionMs >= state.value.durationMs) 0 else state.value.positionMs)
    }
    fun pause() { shouldPlay = false; if (prepared) runCatching { media?.pause() }; _state.update { it.copy(playing = false) } }
    fun seek(position: Long) {
        if (clips.isEmpty()) return
        val desired = position.coerceIn(0, maxOf(0, state.value.durationMs - 1))
        val target = clips.indices.firstOrNull { desired < offset(it) + clips[it].durationMs } ?: clips.lastIndex
        if (target == index && prepared) {
            media?.seekTo((desired - offset(index)).toInt()); _state.update { it.copy(positionMs = desired) }
        } else openAt(desired)
    }
    private fun openAt(position: Long) {
        media?.release(); prepared = false
        index = clips.indices.firstOrNull { position < offset(it) + clips[it].durationMs } ?: 0
        val clip = clips[index]; val source = file(clip) ?: return
        val created = MediaPlayer(); media = created
        _state.update { it.copy(clipId = clip.id, positionMs = position, playing = false, preparing = true, error = null) }
        try {
            created.setDataSource(source.absolutePath)
            created.setOnPreparedListener { player ->
                if (media !== player) return@setOnPreparedListener
                prepared = true
                player.seekTo((position - offset(index)).coerceAtLeast(0).toInt())
                player.playbackParams = PlaybackParams().setSpeed(state.value.speed).setPitch(1f)
                if (shouldPlay) player.start() else player.pause()
                _state.update { it.copy(preparing = false, playing = shouldPlay) }
            }
            created.setOnCompletionListener {
                if (media !== it) return@setOnCompletionListener
                val start = state.value.loopStart; val end = state.value.loopEnd
                if (start != null && end != null && offset(index) + clip.durationMs >= end) openAt(start)
                else if (index + 1 < clips.size) openAt(offset(index + 1))
                else { shouldPlay = false; _state.update { s -> s.copy(positionMs = s.durationMs, playing = false) } }
            }
            created.setOnErrorListener { player, _, _ ->
                if (media === player) { shouldPlay = false; prepared = false; _state.update { it.copy(playing = false, preparing = false, error = "ဒီအသံဖိုင်ကို ဖွင့်မရပါ။") } }
                true
            }
            created.prepareAsync()
        } catch (_: Exception) { shouldPlay = false; _state.update { it.copy(preparing = false, playing = false, error = "အသံဖိုင်ကို ဖွင့်မရပါ။") } }
    }
    fun speed(value: Float) {
        val speed = value.coerceIn(.5f, 1.5f)
        _state.update { it.copy(speed = speed) }
        if (prepared) runCatching { media?.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f); if (!shouldPlay) media?.pause() }
    }
    fun markA() { _state.update { it.copy(loopStart = it.positionMs, loopEnd = null) } }
    fun markB() { _state.update { if (it.loopStart != null && it.positionMs > it.loopStart + 500)
        it.copy(loopEnd = it.positionMs) else it.copy(error = "B ကို A နောက်မှာ ရွေးပါ။") } }
    fun clearLoop() { _state.update { it.copy(loopStart = null, loopEnd = null) } }
    fun stop() { shouldPlay = false; prepared = false; media?.release(); media = null; _state.update { it.copy(playing = false, preparing = false, positionMs = 0, clipId = 0, loopStart = null, loopEnd = null) } }
    fun close() { stop(); scope.cancel() }
}
