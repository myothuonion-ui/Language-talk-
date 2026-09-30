package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.*
import org.junit.Assert.*
import org.junit.Test

class PracticeEngineTest {
    @Test fun introductionChoosesOneSentenceWithoutClaimingMastery() {
        val next = PracticeEngine.advance(PracticeState(), PracticeAssessment(targetSentence = "다시 설명해 주세요."), PracticeMode.GUIDED)
        assertEquals("REPEAT", next.stage); assertEquals(0, next.completed)
    }
    @Test fun successfulRepetitionStillRequiresIndependentUse() {
        val state = PracticeState(stage = "REPEAT", targetSentence = "안녕하세요.")
        val next = PracticeEngine.advance(state, PracticeAssessment(assessment = "PASSED"), PracticeMode.GUIDED)
        assertEquals("APPLY", next.stage); assertEquals(0, next.completed)
    }
    @Test fun uncertainAudioCannotAdvance() {
        val state = PracticeState(stage = "APPLY", targetSentence = "안녕하세요.")
        assertEquals("APPLY", PracticeEngine.advance(state, PracticeAssessment(assessment = "UNSURE"), PracticeMode.GUIDED).stage)
    }
    @Test fun aDifferentTargetCannotReplaceTheCurrentSentenceDuringRetry() {
        val state = PracticeState(stage = "REPEAT", targetSentence = "안녕하세요.")
        val next = PracticeEngine.advance(state, PracticeAssessment("잘 가요.", "RETRY"), PracticeMode.GUIDED)
        assertEquals("안녕하세요.", next.targetSentence); assertEquals("REPEAT", next.stage)
    }
    @Test fun independentUseCountsOnceThenIntroducesTheNextSentence() {
        val state = PracticeState(stage = "APPLY", targetSentence = "안녕하세요.")
        val next = PracticeEngine.advance(state, PracticeAssessment(assessment = "PASSED"), PracticeMode.GUIDED)
        assertEquals(1, next.completed); assertEquals("INTRO", next.stage)
        assertEquals(1, PracticeEngine.advance(next, PracticeAssessment(assessment = "PASSED"), PracticeMode.GUIDED).completed)
    }
    @Test fun fiveUsefulPhrasesLeadToReview() {
        val state = PracticeState(stage = "APPLY", completed = 4, targetSentence = "감사합니다.")
        val next = PracticeEngine.advance(state, PracticeAssessment(assessment = "PASSED"), PracticeMode.GUIDED)
        assertEquals("REVIEW", next.stage); assertEquals(5, next.completed)
    }
    @Test fun repeatAndSlowCommandsDoNotCountAsAnswers() {
        val state = PracticeState(stage = "APPLY")
        for (command in listOf("REPEAT", "SLOW", "NORMAL", "EXPLAIN", "KEEP_TOPIC"))
            assertEquals(0, PracticeEngine.advance(state, PracticeAssessment(assessment = "PASSED", command = command), PracticeMode.GUIDED).completed)
    }
    @Test fun freeTalkAndRoleplayDoNotForceARepetitionLesson() {
        for (mode in listOf(PracticeMode.ROLEPLAY, PracticeMode.FREE_TALK)) {
            val next = PracticeEngine.advance(PracticeState(), PracticeAssessment("안녕하세요.", "PASSED", note = "Useful greeting"), mode)
            assertEquals("INTRO", next.stage); assertTrue(next.summary.contains("Useful greeting"))
        }
    }
    @Test fun disabledCorrectionSpeechDoesNotReadTheCorrection() {
        val reply = TutorReply("안녕하세요.", correction = "Correction", speechText = "Correction")
        assertEquals("안녕하세요.", reply.speech(false))
    }
}
