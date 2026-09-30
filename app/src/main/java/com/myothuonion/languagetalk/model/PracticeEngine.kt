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
    val lastCorrection: String = ""
)

data class PracticeAssessment(
    val targetSentence: String = "",
    val assessment: String = "NONE",
    val correction: String = "",
    val note: String = "",
    val command: String = ""
)

object PracticeEngine {
    fun advance(state: PracticeState, result: PracticeAssessment, mode: PracticeMode): PracticeState {
        val note = result.note.trim().take(500)
        val updated = state.copy(
            summary = if (note.isBlank()) state.summary else (state.summary + "\n" + note).trim().takeLast(1600),
            lastCorrection = result.correction.trim().take(500).ifBlank { state.lastCorrection }
        )
        if (mode != PracticeMode.GUIDED || result.command.isNotBlank()) return updated
        val stage = runCatching { PracticeStage.valueOf(state.stage) }.getOrDefault(PracticeStage.INTRO)
        return when (stage) {
            PracticeStage.INTRO -> if (result.targetSentence.isNotBlank()) updated.copy(
                stage = PracticeStage.REPEAT.name, targetSentence = result.targetSentence.trim().take(300)
            ) else updated
            PracticeStage.REPEAT -> if (result.assessment == "PASSED") updated.copy(stage = PracticeStage.APPLY.name) else updated
            PracticeStage.APPLY -> if (result.assessment == "PASSED") updated.copy(
                completed = (state.completed + 1).coerceAtMost(5),
                stage = if (state.completed + 1 >= 5) PracticeStage.REVIEW.name else PracticeStage.INTRO.name,
                targetSentence = if (state.completed + 1 >= 5) state.targetSentence else ""
            ) else updated
            PracticeStage.REVIEW -> updated
        }
    }

    fun instruction(state: PracticeState, mode: PracticeMode): String = """
        Practice mode: ${mode.name}. Current goal: ${state.goal}.
        Persisted stage: ${state.stage}; current sentence: ${state.targetSentence}; independently used phrases: ${state.completed}/5.
        Last lesson notes: ${state.summary}. Last useful correction: ${state.lastCorrection}.
        GUIDED: INTRO introduces ONE useful sentence and asks the learner to repeat it.
        REPEAT checks that SAME sentence; do not replace it. A successful repeat leads to APPLY.
        APPLY uses a new real-life situation without giving the answer first. Only successful independent use counts as mastery.
        RETRY or UNSURE stays on the same step. REVIEW briefly recalls the five phrases and asks whether to review or start another goal.
        ROLEPLAY: stay in the chosen character and situation; correct one important mistake after the learner finishes.
        FREE_TALK: follow the learner's chosen topic, with optional brief corrections. Never choose unrelated topics.
        Wait for the learner. Never answer your own question or imitate their response. Use at most two short spoken sentences and ONE request/question.
        Voice commands REPEAT, SLOW, NORMAL, EXPLAIN, KEEP_TOPIC do not advance the lesson.
        Grammar can be checked from text. Pronunciation, 받침, and intonation require actual audio; if uncertain say so and ask for a repeat. Never invent a numerical pronunciation score.
        Save observations, not guesses. Roleplay identities and fictional facts are not personal memories.
    """.trimIndent()
}
