package com.myothuonion.languagetalk.data

import android.content.Context
import com.myothuonion.languagetalk.model.*
import com.myothuonion.languagetalk.network.AudioPayload
import com.myothuonion.languagetalk.network.HandsFreeTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

data class CoachResult(val heard: String, val feedback: String, val passed: Boolean, val uncertain: Boolean,
    val command: String = "")

class LearningRepository(private val context: Context, val store: LearningStore, private val tutor: TutorRepository) {
    private val mutex = Mutex()
    val curriculum get() = store.curriculum
    val state get() = store.state
    private val recordings = File(context.filesDir, "coach-recordings").apply { mkdirs() }
    private val speechCache = File(context.cacheDir, "coach-speech").apply { mkdirs() }
    suspend fun currentUnit(): LearningUnit {
        val state = store.export()
        return curriculum.firstOrNull { it.id == state.activeUnit && !CoachEngine.progress(state, it.id).completed }
            ?: curriculum.firstOrNull { it.level == state.level && !CoachEngine.progress(state, it.id).completed }
            ?: curriculum.firstOrNull { it.level > state.level && !CoachEngine.progress(state, it.id).completed }
            ?: curriculum.first { it.level == state.level }
    }
    suspend fun select(id: String) {
        val unit = curriculum.first { it.id == id }
        store.update { it.copy(activeUnit = id, level = unit.level) }
    }
    suspend fun chooseLevel(level: Int) {
        require(level in 0..5)
        store.update { it.copy(level = level, activeUnit = "") }
    }
    suspend fun restart(id: String) {
        require(curriculum.any { it.id == id })
        store.update { it.copy(units = it.units.filterNot { p -> p.id == id }, activeUnit = id) }
    }
    suspend fun beginPlacement() = store.update { it.copy(placementDone = false, placementLevel = 0,
        placementIndex = 0, placementPasses = 0) }
    suspend fun placementTask(): CoachTask {
        val state = store.export()
        val unit = curriculum.filter { it.level == state.placementLevel }[state.placementIndex]
        return if (state.placementIndex == 0) CoachEngine.task(unit, CoachStep.LISTEN.ordinal)
            else CoachEngine.task(unit, CoachStep.CHECK.ordinal)
    }
    private suspend fun assess(task: CoachTask, text: String, audio: AudioPayload?): TutorReply {
        val profile = tutor.learningContext()
        val previous = store.export().attempts.takeLast(12).joinToString("\n") {
            "Practice task " + it.unitId + "/" + it.step + ": " + it.heard + " | Feedback: " + it.feedback
        }.takeLast(5000)
        val result = tutor.bookResponse(
            """You are a bounded Korean speaking coach. Assess ONLY the current supplied task.
            Do not generate the next question or change the task. Never act out the learner's role.
            Task criterion: """ + task.criterion + """
            Accept semantically correct alternatives, actual personal names, and suitable register.
            Return assessment PASSED, RETRY or UNSURE; heardText is the faithful audio transcription.
            If there is no intelligible response, choose UNSURE, not PASSED.
            For commands REPEAT, SLOW, NORMAL, EXPLAIN, KEEP_TOPIC set voiceCommand and assessment NONE.
            Give at most one useful correction and one SHORT Myanmar explanation, never a numerical pronunciation score.
            Assess pronunciation only from supplied actual audio. If unsure about audio say so.
            reply and speechText may be empty. memoryFact and memoryEvidence MUST be empty.
            Textbook/roleplay identities are fictional and never personal memory.
            The learner may explain listening meaning in Myanmar; other stages require Korean.
            Explicit learner context for relevance only, not facts to invent:
            """ + profile + "\nPrevious practice for continuity only; it can be fictional, never assume personal facts:\n" + previous,
            text.ifBlank { "Assess the recorded response to the supplied task." }, audio, assessmentOnly = true, task = AiTask.COACH)
        return if (audio == null) result.copy(heardText = text.trim().take(4000)) else result
    }
    private fun feedback(result: TutorReply): String = listOf(result.correction, result.explanation, result.lessonNote)
        .filter { it.isNotBlank() }.distinct().joinToString("\n").take(1500).ifBlank {
            if (result.assessment == "PASSED") "ကောင်းပါတယ်။ နောက်အဆင့်ကို ဆက်လေ့ကျင့်မယ်။"
            else "သေချာမကြားရပါ။ ဒီအဆင့်ကို ထပ်စမ်းပါ။"
        }
    suspend fun answer(text: String = "", audio: AudioPayload? = null, reviewId: String? = null,
        placement: Boolean = false, unitId: String? = null): CoachResult = mutex.withLock {
        val state = store.export()
        val unit = unitId?.let { id -> curriculum.first { it.id == id } } ?: currentUnit()
        val progress = CoachEngine.progress(state, unit.id)
        require(placement || reviewId != null || !progress.completed) { "ဒီသင်ခန်းစာပြီးပါပြီ။ နောက်သင်ခန်းစာရွေးပါ။" }
        val card = reviewId?.let { id -> state.cards.first { it.id == id } }
        val task = when {
            placement -> placementTask()
            card != null -> CoachTask(card.prompt, card.prompt, card.criterion, card.example, "", card.title)
            else -> CoachEngine.task(unit, progress.step)
        }
        val result = assess(task, text, audio)
        val heard = result.heardText.trim().take(4000)
        val note = feedback(result)
        val passed = CoachEngine.passed(result.assessment, heard, result.voiceCommand)
        val unsure = heard.isBlank() || result.assessment !in setOf("PASSED", "RETRY")
        if (result.voiceCommand.isNotBlank()) {
            val response = when (result.voiceCommand) {
                "SLOW" -> "နှေးနှေး ပြန်ပြောပေးမယ်။"
                "NORMAL" -> "ပုံမှန်အရှိန်နဲ့ ပြန်ပြောပေးမယ်။"
                "EXPLAIN" -> if (!placement && (card != null || progress.step <= CoachStep.ROLE_SWAP.ordinal))
                    card?.grammar?.ifBlank { card.meaning } ?: (unit.explanation + "\n" + task.hint)
                    else "ဒီအဆင့်က အကူအညီမပါဘဲ စစ်ဆေးတဲ့အဆင့်ပါ။ အရင်အဆင့်ကို ပြန်လေ့လာချင်ရင် သင်ခန်းစာပြန်စနိုင်ပါတယ်။"
                else -> "ဒီအဆင့်ကိုပဲ ပြန်ပြောပေးမယ်။"
            }
            return@withLock CoachResult(heard, response, false, true, result.voiceCommand)
        }
        val now = System.currentTimeMillis()
        val audioName = if (audio != null && tutor.settings.first().recordPractice) withContext(Dispatchers.IO) {
            val name = UUID.randomUUID().toString() + if (audio.mimeType.contains("wav")) ".wav" else ".m4a"
            File(recordings, name).writeBytes(audio.bytes)
            name
        } else ""
        store.update { old ->
            val updated = if (placement && !unsure) CoachEngine.placement(old, passed) else old
            val next = if (!placement && card == null)
                CoachEngine.accept(CoachEngine.progress(old, unit.id), result.assessment, heard, result.voiceCommand, now) else progress
            val reviewCards = when {
                card != null && !unsure -> old.cards.map { if (it.id == card.id) CoachEngine.review(it, passed, now) else it }
                !placement && card == null && !passed && !unsure -> {
                    val id = "mistake-" + unit.id + "-" + progress.step
                    (old.cards.filterNot { it.id == id } + ReviewCard(id, unit.title, task.prompt, task.criterion,
                        task.example, task.hint, note, kind = "MISTAKE", dueAt = now)).takeLast(5000)
                }
                else -> old.cards
            }
            updated.copy(
                units = if (!placement && card == null) old.units.filterNot { it.id == unit.id } + next else old.units,
                cards = reviewCards,
                attempts = (old.attempts + CoachAttempt(if (placement) "placement" else card?.id ?: unit.id,
                    if (placement) "PLACEMENT" else if (card != null) "REVIEW" else CoachStep.entries[progress.step].name,
                    heard, note, passed, now, audioName)).takeLast(500))
        }
        withContext(Dispatchers.IO) {
            val keep = store.export().attempts.map { it.audioName }.toSet()
            recordings.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }
        CoachResult(heard, note, passed, unsure)
    }
    suspend fun speech(text: String, slow: Boolean, alternate: Boolean = false): AudioPayload = withContext(Dispatchers.IO) {
        val settings = tutor.settings.first()
        val voice = if (alternate) if (settings.defaultVoiceName == "Puck") "Kore" else "Puck" else settings.defaultVoiceName
        val signature = listOf(text, voice, settings.defaultVoiceStyle, settings.geminiTtsModel, slow).joinToString("|")
        val key = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = File(speechCache, key + ".bin")
        val type = File(speechCache, key + ".mime")
        if (file.isFile && type.isFile) return@withContext AudioPayload(file.readBytes(), type.readText())
        val audio = tutor.bookSpeech(text, slow, voice)
        file.writeBytes(audio.bytes); type.writeText(audio.mimeType)
        speechCache.listFiles()?.filter { it.extension == "bin" }?.sortedByDescending { it.lastModified() }
            ?.drop(80)?.forEach { it.delete(); File(speechCache, it.nameWithoutExtension + ".mime").delete() }
        audio
    }
    suspend fun opening(slow: Boolean, reviewId: String? = null, placement: Boolean = false, unitId: String? = null,
        correction: String = ""): HandsFreeTurn {
        val state = store.export()
        val unit = unitId?.let { id -> curriculum.first { it.id == id } } ?: currentUnit()
        val card = state.cards.firstOrNull { it.id == reviewId }
        val task = if (placement) placementTask() else if (card != null)
            CoachTask(card.prompt, card.prompt, card.criterion, card.example, "", card.title)
            else CoachEngine.task(unit, CoachEngine.progress(state, unit.id).step)
        val spoken = CoachEngine.narration(task)
        val voiceText = VoiceText.korean(spoken).ifBlank { "다시 한번 말해 주세요." }
        return HandsFreeTurn("", task.prompt, speech(voiceText, slow))
    }
    fun recording(attempt: CoachAttempt): AudioPayload? {
        if (attempt.audioName.isBlank() || attempt.audioName != File(attempt.audioName).name) return null
        val file = File(recordings, attempt.audioName)
        return if (file.isFile) AudioPayload(file.readBytes(), if (file.extension == "wav") "audio/wav" else "audio/m4a") else null
    }
    suspend fun deleteRecording(attempt: CoachAttempt) {
        if (attempt.audioName.isNotBlank() && attempt.audioName == File(attempt.audioName).name) File(recordings, attempt.audioName).delete()
        store.update { it.copy(attempts = it.attempts.map { item -> if (item.at == attempt.at && item.audioName == attempt.audioName) item.copy(audioName = "") else item }) }
    }
    suspend fun removeCard(id: String) = store.update { it.copy(cards = it.cards.filterNot { card -> card.id == id }) }
    suspend fun study(seconds: Long) {
        val date = LocalDate.now().toString()
        store.update { it.copy(studySeconds = (it.studySeconds + (date to ((it.studySeconds[date] ?: 0) + seconds.coerceIn(0, 3600)).coerceAtMost(86400))).toSortedMap().entries.toList().takeLast(370).associate { entry -> entry.toPair() }) }
    }
}
