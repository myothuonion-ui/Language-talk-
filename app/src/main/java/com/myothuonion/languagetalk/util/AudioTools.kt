package com.myothuonion.languagetalk.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.myothuonion.languagetalk.network.AudioPayload
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        stopSilently()
        val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        outputFile = file
        recorder = mediaRecorder
    }

    fun stop(): AudioPayload? {
        val file = outputFile
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            outputFile = null
            if (file != null && file.exists() && file.length() > 0) {
                AudioPayload(file.readBytes(), "audio/mp4")
            } else null
        } catch (_: Exception) {
            stopSilently()
            null
        } finally {
            file?.delete()
        }
    }

    fun stopSilently() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class GeminiAudioPlayer(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tempFile: File? = null

    fun play(payload: AudioPayload, onComplete: () -> Unit = {}) {
        stop()
        if (payload.mimeType.contains("pcm", ignoreCase = true) || payload.mimeType.contains("L16", ignoreCase = true)) {
            playPcm(payload.bytes, payload.mimeType, onComplete)
        } else {
            playEncoded(payload, onComplete)
        }
    }

    private fun playPcm(bytes: ByteArray, mimeType: String, onComplete: () -> Unit) {
        val sampleRate = Regex("rate[=;](\\d+)").find(mimeType)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24_000
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
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
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, bytes.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audioTrack = track
        track.write(bytes, 0, bytes.size)
        track.notificationMarkerPosition = bytes.size / 2
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                stop()
                onComplete()
            }
            override fun onPeriodicNotification(track: AudioTrack?) = Unit
        })
        track.play()
    }

    private fun playEncoded(payload: AudioPayload, onComplete: () -> Unit) {
        val suffix = when {
            payload.mimeType.contains("wav") -> ".wav"
            payload.mimeType.contains("mpeg") -> ".mp3"
            else -> ".audio"
        }
        val file = File.createTempFile("gemini-tts-", suffix, context.cacheDir).apply {
            writeBytes(payload.bytes)
        }
        tempFile = file
        mediaPlayer = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                stop()
                onComplete()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                onComplete()
                true
            }
            prepare()
            start()
        }
    }

    fun stop() {
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        tempFile?.delete()
        tempFile = null
    }
}
