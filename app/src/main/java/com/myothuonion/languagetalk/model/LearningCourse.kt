package com.myothuonion.languagetalk.model

import kotlinx.serialization.Serializable

@Serializable
data class LearningUnit(
    val id: String, val level: Int, val title: String, val goal: String, val topic: String,
    val pattern: String, val explanation: String, val question: String, val example: String,
    val meaning: String, val transfer: String, val criterion: String
)

enum class CoachStep(val label: String) {
    LISTEN("နားထောင်"), SHADOW("လိုက်ပြော"), GUIDED("အဖြေပြော"),
    ROLE_SWAP("အလှည့်လဲ"), NO_HINT("အကူအညီမပါဘဲ"), TRANSFER("အသုံးချ"), CHECK("စွမ်းရည်စစ်")
}

@Serializable
data class UnitProgress(val id: String, val step: Int = 0, val completed: Boolean = false,
    val attempts: Int = 0, val passed: Int = 0, val updatedAt: Long = 0)

@Serializable
data class ReviewCard(val id: String, val title: String, val prompt: String, val criterion: String,
    val example: String = "", val meaning: String = "", val grammar: String = "",
    val context: String = "", val bookId: String = "", val bookTitle: String = "", val page: Int = 0,
    val kind: String = "MISTAKE", val dueAt: Long = 0, val streak: Int = 0, val attempts: Int = 0)

@Serializable
data class CoachAttempt(val unitId: String, val step: String, val heard: String, val feedback: String,
    val passed: Boolean, val at: Long, val audioName: String = "")

@Serializable
data class ReaderBook(val id: String, val title: String, val fileName: String = "", val assetId: String = "",
    val pageCount: Int, val page: Int = 0, val bookmarks: List<Int> = emptyList(), val addedAt: Long = 0)

@Serializable
data class ReaderTranslation(val key: String, val selected: String, val lemma: String,
    val meaning: String, val grammar: String, val sentence: String)

@Serializable
data class LearningState(val level: Int = 0, val activeUnit: String = "", val dailyMinutes: Int = 20,
    val units: List<UnitProgress> = emptyList(), val cards: List<ReviewCard> = emptyList(),
    val attempts: List<CoachAttempt> = emptyList(), val books: List<ReaderBook> = emptyList(),
    val translations: List<ReaderTranslation> = emptyList(), val readerTheme: String = "DAY",
    val placementLevel: Int = 0, val placementIndex: Int = 0, val placementPasses: Int = 0,
    val placementDone: Boolean = false, val studySeconds: Map<String, Long> = emptyMap())

data class CoachTask(val spoken: String, val prompt: String, val criterion: String, val example: String,
    val hint: String, val canDo: String)

object CoachEngine {
    val levels = listOf("အစပြု · 한글", "Beginner", "Elementary", "Intermediate", "Upper-intermediate", "Advanced")
    fun progress(state: LearningState, id: String) = state.units.firstOrNull { it.id == id } ?: UnitProgress(id)
    fun task(unit: LearningUnit, step: Int): CoachTask {
        val stage = CoachStep.entries[step.coerceIn(0, CoachStep.entries.lastIndex)]
        return when (stage) {
            CoachStep.LISTEN -> CoachTask(unit.example, "နားထောင်ပြီး မြန်မာလို အဓိပ္ပာယ်ပြောပါ။",
                "Explain the meaning of this Korean sentence in Myanmar or Korean: " + unit.example + ". Meaning: " + unit.meaning,
                unit.example, unit.meaning, "နားထောင်ပြီး အဓိပ္ပာယ်နားလည်နိုင်ခြင်း")
            CoachStep.SHADOW -> CoachTask(unit.example, "နမူနာအသံကို နားထောင်ပြီး ကိုရီးယားလို လိုက်ပြောပါ။",
                "Repeat the Korean sentence accurately: " + unit.example, unit.example, unit.meaning, "နမူနာဝါကျကို ပြောနိုင်ခြင်း")
            CoachStep.ROLE_SWAP -> CoachTask(unit.example, "အခု မင်းက မေးသူပါ။ ဒီအဖြေရအောင် ကိုရီးယားလို မေးခွန်းမေးပါ။",
                "Ask an appropriate Korean question to elicit: " + unit.example + ". Model question: " + unit.question,
                unit.question, "ဖြေသူကနေ မေးသူအဖြစ် အလှည့်လဲမယ်။", "မေးခွန်းကို ကိုယ်တိုင်မေးနိုင်ခြင်း")
            CoachStep.TRANSFER, CoachStep.CHECK -> CoachTask(unit.transfer, unit.transfer,
                unit.criterion + " Answer this NEW situation in Korean: " + unit.transfer + ". Do not demand the exact model wording.",
                "", "", unit.goal)
            else -> CoachTask(unit.question, unit.question, unit.criterion + " Respond appropriately in Korean to: " + unit.question,
                unit.example, if (stage == CoachStep.GUIDED) unit.meaning else "", unit.goal)
        }
    }
    fun accept(progress: UnitProgress, assessment: String, heard: String, command: String, now: Long): UnitProgress {
        if (progress.completed || heard.isBlank() || command.isNotBlank() || assessment == "UNSURE" || assessment == "NONE") return progress
        val passed = assessment == "PASSED"
        return progress.copy(attempts = progress.attempts + 1, passed = progress.passed + if (passed) 1 else 0,
            step = if (passed) (progress.step + 1).coerceAtMost(CoachStep.entries.lastIndex) else progress.step,
            completed = passed && progress.step == CoachStep.entries.lastIndex, updatedAt = now)
    }
    fun review(card: ReviewCard, passed: Boolean, now: Long): ReviewCard {
        val streak = if (passed) (card.streak + 1).coerceAtMost(6) else 0
        val delay = if (!passed) 10 * 60_000L else listOf(1, 3, 7, 14, 30, 60)[streak - 1] * 86_400_000L
        return card.copy(streak = streak, dueAt = now + delay, attempts = card.attempts + 1)
    }
    fun due(state: LearningState, now: Long) = state.cards.filter { it.dueAt <= now }.sortedBy { it.dueAt }
    fun passed(assessment: String, heard: String, command: String) =
        assessment == "PASSED" && heard.isNotBlank() && command.isBlank()
    /** Placement needs two successful tasks at each level. App estimate, never an official certificate. */
    fun placement(state: LearningState, passed: Boolean): LearningState {
        if (state.placementDone) return state
        val checks = state.placementIndex + 1
        val successes = state.placementPasses + if (passed) 1 else 0
        if (checks < 2) return state.copy(placementIndex = checks, placementPasses = successes)
        if (successes < 2 || state.placementLevel == 5) return state.copy(level = state.placementLevel,
            placementDone = true, placementIndex = checks, placementPasses = successes, activeUnit = "")
        return state.copy(placementLevel = state.placementLevel + 1, placementIndex = 0, placementPasses = 0)
    }
}
