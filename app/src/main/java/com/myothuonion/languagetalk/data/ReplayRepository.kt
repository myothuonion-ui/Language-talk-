package com.myothuonion.languagetalk.data

import android.content.Context
import android.media.MediaMetadataRetriever
import com.myothuonion.languagetalk.network.AudioPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Durable original input/output files, independent of disposable TTS caches and API access. */
class ReplayRepository(context: Context, private val dao: LanguageTalkDao, private val settings: SettingsStore) {
    private val directory = File(context.filesDir, "replay-audio").apply { mkdirs() }
    val sessions = dao.observeReplaySessions()
    fun clips(id: String) = dao.observeReplayClips(id)
    suspend fun contents(id: String) = dao.replayClips(id)
    suspend fun begin(kind: String, sourceId: String, title: String, bookId: String = "", page: Int = -1): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        val id = "$kind:$sourceId:$day"
        if (!settings.settings.first().recordPractice) return id
        if (dao.replaySession(id) == null) dao.saveReplaySession(ReplaySession(id, title.take(300), kind, sourceId, bookId, page))
        return id
    }
    suspend fun save(sessionId: String, speaker: String, text: String, audio: AudioPayload? = null,
        origin: String = if (audio == null) "TEXT_ONLY" else if (speaker == "USER") "USER" else "GENERATED"): Long? {
        if (!settings.settings.first().recordPractice) return null
        require(speaker in setOf("USER", "AI"))
        if ((text.isBlank() && audio == null) || dao.replaySession(sessionId) == null) return null
        val clip = ReplayClip(sessionId = sessionId, speaker = speaker, text = text.take(6000), origin = origin)
        return withContext(NonCancellable + Dispatchers.IO) {
            val saved = if (audio == null) clip else withAudio(clip, audio)
            try {
                val id = dao.insertReplayClip(saved)
                dao.touchReplay(sessionId)
                id
            } catch (failure: Exception) { file(saved)?.delete(); throw failure }
        }
    }
    suspend fun transcript(id: Long?, text: String) { if (id != null) dao.updateReplayTranscript(id, text.take(6000)) }
    suspend fun attachAI(sessionId: String, text: String, audio: AudioPayload, origin: String = "GENERATED") {
        if (!settings.settings.first().recordPractice) return
        val pending = dao.replayClips(sessionId).lastOrNull { it.speaker == "AI" && it.audioName.isBlank() && it.text == text.take(6000) }
        if (pending == null) save(sessionId, "AI", text, audio, origin)
        else withContext(NonCancellable + Dispatchers.IO) {
            val saved = withAudio(pending.copy(origin = origin), audio)
            try { dao.updateReplayClip(saved); dao.touchReplay(sessionId) }
            catch (failure: Exception) { file(saved)?.delete(); throw failure }
        }
    }
    private suspend fun withAudio(clip: ReplayClip, payload: AudioPayload): ReplayClip = withContext(Dispatchers.IO) {
        require(payload.bytes.isNotEmpty() && payload.bytes.size <= 32 * 1024 * 1024) { "အသံဖိုင် အရွယ်အစား မမှန်ပါ။" }
        val suffix = when {
            payload.mimeType.contains("wav") -> ".wav"
            payload.mimeType.contains("mpeg") -> ".mp3"
            payload.mimeType.contains("ogg") -> ".ogg"
            else -> ".m4a"
        }
        val name = UUID.randomUUID().toString() + suffix
        val file = File(directory, name); val temporary = File(directory, name + ".part")
        try { temporary.writeBytes(payload.bytes); require(temporary.renameTo(file)) { "အသံမှတ်တမ်း သိမ်းမရပါ။" } }
        finally { temporary.delete() }
        val metadata = MediaMetadataRetriever()
        val duration = try { metadata.setDataSource(file.absolutePath)
            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L } finally { metadata.release() }
        clip.copy(audioName = name, mimeType = payload.mimeType, durationMs = duration)
    }
    fun file(clip: ReplayClip): File? {
        if (clip.audioName.isBlank() || !clip.audioName.matches(Regex("[a-zA-Z0-9._-]{1,100}"))) return null
        return File(directory, clip.audioName).takeIf { it.isFile }
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val clips = dao.replayClips(id)
        dao.deleteReplaySession(id)
        clips.forEach { file(it)?.delete() }
    }
    suspend fun export(id: String, output: OutputStream) = withContext(Dispatchers.IO) {
        val session = requireNotNull(dao.replaySession(id))
        val clips = dao.replayClips(id)
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("session.json")); zip.write(Json.encodeToString(session).toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("transcript.json")); zip.write(Json.encodeToString(clips).toByteArray()); zip.closeEntry()
            clips.forEach { clip -> file(clip)?.let { file ->
                zip.putNextEntry(ZipEntry("audio/" + file.name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            } }
        }
    }
}
