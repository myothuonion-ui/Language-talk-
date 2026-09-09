package com.myothuonion.languagetalk.util

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

class LivePcmRecorder {
    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    val isRunning: Boolean get() = running.get()

    @SuppressLint("MissingPermission")
    fun start(onChunk: (ByteArray, Float) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(min * 2, CHUNK_BYTES * 2)
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        audioRecord = recorder
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
        }
        recorder.startRecording()
        captureThread = thread(name = "language-talk-live-mic", isDaemon = true) {
            val buffer = ByteArray(CHUNK_BYTES)
            while (running.get()) {
                val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    val chunk = buffer.copyOf(count)
                    onChunk(chunk, pcmLevel(chunk))
                }
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { audioRecord?.stop() }
        captureThread?.interrupt()
        runCatching { captureThread?.join(250) }
        echoCanceler?.release()
        noiseSuppressor?.release()
        echoCanceler = null
        noiseSuppressor = null
        audioRecord?.release()
        audioRecord = null
        captureThread = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_BYTES = 3_200 // 100 ms, mono PCM16 at 16 kHz.

        fun pcmLevel(bytes: ByteArray): Float {
            if (bytes.size < 2) return 0f
            var peak = 0
            var i = 0
            while (i + 1 < bytes.size) {
                val value = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xff)).toShort().toInt()
                peak = maxOf(peak, abs(value))
                i += 2
            }
            return (peak / 32768f).coerceIn(0f, 1f)
        }
    }
}

class LivePcmPlayer(
    private val onLevel: (Float) -> Unit,
    private val onIdle: () -> Unit
) {
    private val running = AtomicBoolean(false)
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    fun enqueue(bytes: ByteArray) {
        ensureStarted()
        queue.offer(bytes)
    }

    private fun ensureStarted() {
        if (!running.compareAndSet(false, true)) return
        val min = AudioTrack.getMinBufferSize(24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(24_000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min * 4, 9_600))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audioTrack = track
        track.play()
        playbackThread = thread(name = "language-talk-live-speaker", isDaemon = true) {
            while (running.get()) {
                val chunk = try {
                    queue.poll(180, java.util.concurrent.TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                }
                if (chunk != null) {
                    onLevel(LivePcmRecorder.pcmLevel(chunk))
                    track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                } else if (queue.isEmpty()) {
                    onLevel(0f)
                    onIdle()
                }
            }
        }
    }

    fun interrupt() {
        queue.clear()
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.play() }
        onLevel(0f)
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        queue.clear()
        playbackThread?.interrupt()
        runCatching { playbackThread?.join(250) }
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        playbackThread = null
        onLevel(0f)
    }
}
