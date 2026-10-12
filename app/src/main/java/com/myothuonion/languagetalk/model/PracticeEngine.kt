package com.myothuonion.languagetalk.model

import kotlinx.serialization.Serializable

enum class PracticeMode(val label: String) {
    GUIDED("Guided Practice"), ROLEPLAY("Roleplay"), FREE_TALK("Free Talk")
}

enum class PracticeStage { INTRO, REPEAT, APPLY, REVIEW }

@Serializable
data class PracticeState(
    val goal: String = "Practical Korean for work and daily life",
    val stage: String = PracticeStage.INTRO.name,
    val targetSentence: String = "",
    val completed: Int = 0,
    val summary: String = "",
    val lastCorrection: String = "",
    val readyToContinue: Boolean = false, val nextStage: String = ""
)

data class PracticeAssessment(
    val targetSentence: String = "",
    val assessment: String = "NONE",
    val correction: String = "",
    val note: String = "",
    val command: String = "",
    val nextTargetSentence: String = ""
)

object PracticeEngine {
    fun ground(state: PracticeState, result: PracticeAssessment, heard: String,
        assisted: Boolean, mode: PracticeMode): PracticeAssessment = when {
        heard.isBlank() && state.stage != "INTRO" && result.assessment == "PASSED" ->
            result.copy(assessment = "UNSURE")
        mode == PracticeMode.GUIDED && state.stage == "APPLY" && assisted && result.assessment == "PASSED" ->
            result.copy(assessment = "RETRY",
                note = "အကူအညီနဲ့ ပြောနိုင်ပြီ။ အခု အကူအညီမပါဘဲ နောက်တစ်ကြိမ် ပြောကြည့်မယ်။")
        else -> result
    }

    fun advance(state: PracticeState, result: PracticeAssessment, mode: PracticeMode): PracticeState {
        val note = result.note.trim().take(500)
        val updated = state.copy(summary = if (note.isBlank()) state.summary else
            (state.summary + "\n" + note).trim().takeLast(1600),
            lastCorrection = result.correction.trim().take(500).ifBlank { state.lastCorrection })
        if (mode != PracticeMode.GUIDED || result.command.isNotBlank() || state.readyToContinue) return updated
        val stage = runCatching { PracticeStage.valueOf(state.stage) }.getOrDefault(PracticeStage.INTRO)
        return when (stage) {
            PracticeStage.INTRO -> if (result.targetSentence.isNotBlank()) updated.copy(
                stage = "REPEAT",targetSentence = result.targetSentence.trim().take(300)) else updated
            PracticeStage.REPEAT -> if (result.assessment == "PASSED") updated.copy(
                readyToContinue = true,nextStage = "APPLY") else updated
            PracticeStage.APPLY -> if (result.assessment == "PASSED") updated.copy(
                completed = (state.completed+1).coerceAtMost(5),readyToContinue = true,
                nextStage = if (state.completed+1 >= 5) "REVIEW" else "INTRO") else updated
            PracticeStage.REVIEW -> updated
        }
    }

    fun continueStep(state: PracticeState): PracticeState {
        require(state.readyToContinue && state.nextStage in PracticeStage.entries.map { it.name }) {
            "ဒီအပိုင်းကို အရင်လေ့ကျင့်ပါ။"
        }
        return state.copy(stage = state.nextStage,readyToContinue = false,nextStage = "",
            targetSentence = if (state.nextStage == "INTRO") "" else state.targetSentence)
    }

    fun instruction(state: PracticeState, mode: PracticeMode): String = """
        Practice mode: ${mode.name}. Current goal: ${state.goal}.
        Persisted stage: ${state.stage}; waiting for explicit Continue: ${state.readyToContinue}; current sentence: ${state.targetSentence}; independently used phrases: ${state.completed}/5.
        Last lesson notes: ${state.summary}. Last useful correction: ${state.lastCorrection}.
        GUIDED: INTRO introduces ONE useful sentence and asks the learner to repeat it.
        REPEAT checks that SAME sentence; do not replace it. A successful repeat waits for the learner to press Continue before APPLY.
        APPLY uses a new real-life situation without giving the answer first. Only successful independent use counts as mastery.
        After successful APPLY, give feedback only and STOP. Never introduce another sentence or question automatically.
        The app changes stage only after the learner presses Continue; leave nextTargetSentence empty.
        When readyToContinue is true, practise the same target only; do not choose another task.
        If nextTargetSentence is blank, ask whether the learner is ready; do not introduce a new phrase until INTRO.
        RETRY or UNSURE stays on the same step. REVIEW briefly recalls the five phrases and asks whether to review or start another goal.
        ROLEPLAY: stay in the chosen character and situation; correct one important mistake after the learner finishes.
        FREE_TALK: follow the learner's chosen topic, with optional brief corrections. Never choose unrelated topics.
        Wait for the learner. Never answer your own question or imitate their response. Use at most two short spoken sentences and ONE request/question.
        Voice commands REPEAT, SLOW, NORMAL, EXPLAIN, KEEP_TOPIC do not advance the lesson.
        Grammar can be checked from text. Pronunciation, 받침, and intonation require actual audio; if uncertain say so and ask for a repeat. Never invent a numerical pronunciation score.
        Save observations, not guesses. Roleplay identities and fictional facts are not personal memories.
    """.trimIndent()
}
