package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.*
import org.junit.Assert.*
import org.junit.Test

class PracticeEngineTest {
    private fun advanceWithContinue(state: PracticeState, result: PracticeAssessment, mode: PracticeMode): PracticeState {
        val assessed = PracticeEngine.advance(state, result, mode)
        return if (assessed.readyNext) PracticeEngine.continuePractice(assessed) else assessed
    }
    @Test fun successWaitsForNextAndRepeatedRepliesCannotAdvanceOrDoubleCount() {
        val state = PracticeState(stage = "APPLY", targetSentence = "안녕하세요.")
        val assessed = PracticeEngine.advance(state, PracticeAssessment(assessment = "PASSED"), PracticeMode.GUIDED)
        assertEquals(state.stage, assessed.stage)
        assertEquals(0, assessed.completed)
        assertTrue(assessed.readyNext)
        val repeated = PracticeEngine.advance(assessed, PracticeAssessment(assessment = "PASSED", nextTargetSentence = "different"), PracticeMode.GUIDED)
        assertEquals(assessed, repeated)
        assertEquals(1, PracticeEngine.continuePractice(assessed).completed)
        assertThrows(IllegalArgumentException::class.java) { PracticeEngine.continuePractice(state) }
    }

    @Test fun introductionChoosesOneSentenceWithoutClaimingMastery() {
        val next = advanceWithContinue(PracticeState(), PracticeAssessment(targetSentence = "다시 설명해 주세요."), PracticeMode.GUIDED)
        assertEquals("REPEAT", next.stage); assertEquals(0, next.completed)
    }
    @Test fun successfulRepetitionStillRequiresIndependentUse() {
        val state = PracticeState(stage = "REPEAT", targetSentence = "안녕하세요.")
        val next = advanceWithContinue(state, PracticeAssessment(assessment = "PASSED"), PracticeMode.GUIDED)
        assertEquals("APPLY", next.stage); assertEquals(0, next.completed)
    }
    @Test fun uncertainAudioCannotAdvance() {
        val state = PracticeState(stage = "APPLY", targetSentence = "안녕하세요.")
        assertEquals("APPLY", advanceWithContinue(state, PracticeAssessment(assessment = "UNSURE"), PracticeMode.GUIDED).stage)
    }
    @Test fun aDifferentTargetCannotReplaceTheCurrentSentenceDuringRetry() {
        val state = PracticeState(stage = "REPEAT", targetSentence = "안녕하세요.")
        val next = advanceWithContinue(state, PracticeAssessment("잘 가요.", "RETRY"), PracticeMode.GUIDED)
        assertEquals("안녕하세요.", next.targetSentence); assertEquals("REPEAT", next.stage)
    }
    @Test fun independentUseCountsOnceThenIntroducesTheNextSentence() {
        val state = PracticeState(stage = "APPLY", targetSentence = "안녕하세요.")
        val next = advanceWithContinue(state, PracticeAssessment(assessment = "PASSED"), PracticeMode.GUIDED)
        assertEquals(1, next.completed); assertEquals("INTRO", next.stage)
        assertEquals(1, advanceWithContinue(next, PracticeAssessment(assessment = "PASSED"), PracticeMode.GUIDED).completed)
    }
    @Test fun nextPhraseIsPersistedBeforeTheLearnerRepeatsIt() {
        val state = PracticeState(stage = "APPLY", targetSentence = "안녕하세요.")
        val next = advanceWithContinue(state, PracticeAssessment(assessment = "PASSED", nextTargetSentence = "감사합니다."), PracticeMode.GUIDED)
        assertEquals("REPEAT", next.stage); assertEquals("감사합니다.", next.targetSentence); assertEquals(1, next.completed)
    }
    @Test fun fiveUsefulPhrasesLeadToReview() {
        val state = PracticeState(stage = "APPLY", completed = 4, targetSentence = "감사합니다.")
        val next = advanceWithContinue(state, PracticeAssessment(assessment = "PASSED"), PracticeMode.GUIDED)
        assertEquals("REVIEW", next.stage); assertEquals(5, next.completed)
    }
    @Test fun repeatAndSlowCommandsDoNotCountAsAnswers() {
        val state = PracticeState(stage = "APPLY")
        for (command in listOf("REPEAT", "SLOW", "NORMAL", "EXPLAIN", "KEEP_TOPIC"))
            assertEquals(0, advanceWithContinue(state, PracticeAssessment(assessment = "PASSED", command = command), PracticeMode.GUIDED).completed)
    }
    @Test fun freeTalkAndRoleplayDoNotForceARepetitionLesson() {
        for (mode in listOf(PracticeMode.ROLEPLAY, PracticeMode.FREE_TALK)) {
            val next = advanceWithContinue(PracticeState(), PracticeAssessment("안녕하세요.", "PASSED", note = "Useful greeting"), mode)
            assertEquals("INTRO", next.stage); assertTrue(next.summary.contains("Useful greeting"))
        }
    }
    @Test fun disabledCorrectionSpeechDoesNotReadTheCorrection() {
        val reply = TutorReply("안녕하세요.", correction = "Correction", speechText = "Correction")
        assertEquals("안녕하세요.", reply.speech(false))
    }
    @Test fun anAssistedAnswerRequiresAnotherIndependentAttempt() {
        val state = PracticeState(stage = "APPLY", targetSentence = "물 주세요.")
        val claimedPass = PracticeAssessment(assessment = "PASSED")
        val assisted = PracticeEngine.ground(state, claimedPass, "물 주세요.", true, PracticeMode.GUIDED)
        assertEquals(0, advanceWithContinue(state, assisted, PracticeMode.GUIDED).completed)
        val independent = PracticeEngine.ground(state, claimedPass, "물 주세요.", false, PracticeMode.GUIDED)
        assertEquals(1, advanceWithContinue(state, independent, PracticeMode.GUIDED).completed)
    }
    @Test fun aClaimedPassWithoutAnyLearnerResponseCannotAdvance() {
        val state = PracticeState(stage = "REPEAT", targetSentence = "안녕하세요.")
        val grounded = PracticeEngine.ground(state, PracticeAssessment(assessment = "PASSED"), "", false, PracticeMode.GUIDED)
        assertEquals("UNSURE", grounded.assessment)
        assertEquals("REPEAT", advanceWithContinue(state, grounded, PracticeMode.GUIDED).stage)
    }
}
