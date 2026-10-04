package com.myothuonion.languagetalk

import com.myothuonion.languagetalk.model.*
import org.junit.Assert.*
import org.junit.Test

class CoachEngineTest {
    @Test fun uncertainEmptyAndCommandsCannotUnlockLessons() {
        val state = UnitProgress("course-1-1", step = 3)
        assertEquals(state, CoachEngine.accept(state, "PASSED", "", "", 100))
        assertEquals(state, CoachEngine.accept(state, "PASSED", "천천히 말해 주세요", "SLOW", 100))
        assertEquals(state, CoachEngine.accept(state, "UNSURE", "hello", "", 100))
        assertEquals(3, CoachEngine.accept(state, "RETRY", "wrong", "", 100).step)
    }
    @Test fun onlySevenVerifiedResponsesCompleteAUnitAndCompletionIsStable() {
        var state = UnitProgress("course-1-1")
        repeat(6) { index ->
            state = CoachEngine.accept(state, "PASSED", "안녕하세요", "", index.toLong())
            assertFalse(state.completed)
        }
        state = CoachEngine.accept(state, "PASSED", "안녕하세요", "", 100)
        assertTrue(state.completed)
        assertEquals(7, state.passed)
        assertEquals(state, CoachEngine.accept(state, "PASSED", "안녕하세요", "", 101))
    }
    @Test fun placementRequiresTwoPassesBeforeAdvancingAndStopsAtFirstWeakLevel() {
        var state = LearningState()
        state = CoachEngine.placement(state, true)
        assertEquals(0, state.placementLevel)
        state = CoachEngine.placement(state, true)
        assertEquals(1, state.placementLevel)
        state = CoachEngine.placement(state, false)
        state = CoachEngine.placement(state, true)
        assertTrue(state.placementDone)
        assertEquals(1, state.level)
        assertEquals(state, CoachEngine.placement(state, true))
    }
    @Test fun spacedReviewExtendsOnSuccessAndResetsAfterAMistake() {
        var card = ReviewCard("1", "word", "prompt", "criterion")
        card = CoachEngine.review(card, true, 100)
        assertEquals(86_400_100L, card.dueAt)
        card = CoachEngine.review(card, true, 100)
        assertEquals(3 * 86_400_000L + 100, card.dueAt)
        card = CoachEngine.review(card, false, 100)
        assertEquals(0, card.streak)
        assertEquals(600_100L, card.dueAt)
        assertFalse(CoachEngine.passed("PASSED", "", ""))
    }
    @Test fun roleSwapUsesTheOppositeRoleAndListeningAcceptsMeaningNotRecitation() {
        val unit = LearningUnit("1", 1, "intro", "goal", "Social", "pattern", "explain",
            "이름이 뭐예요?", "저는 민수예요.", "ကျွန်တော်က မင်ဆူပါ။", "소개해 보세요.", "Introduce a name")
        assertEquals(unit.question, CoachEngine.task(unit, CoachStep.ROLE_SWAP.ordinal).example)
        assertEquals(unit.example, CoachEngine.task(unit, CoachStep.LISTEN.ordinal).spoken)
        assertTrue(CoachEngine.task(unit, CoachStep.LISTEN.ordinal).criterion.contains(unit.meaning))
        assertEquals("", CoachEngine.task(unit, CoachStep.CHECK.ordinal).hint)
    }
}
