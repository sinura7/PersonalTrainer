package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPreviewCopyTest {
    @Test
    fun fullWeekWarningNamesTheChoice() {
        assertEquals("No rest day this week.", OnboardingPreviewCopy.FULL_WEEK_TITLE)
        assertEquals("Use this plan", OnboardingPreviewCopy.FULL_WEEK_CONFIRM_PLAN)
        assertEquals("Use this week", OnboardingPreviewCopy.FULL_WEEK_CONFIRM_WEEK)
    }

    @Test
    fun cardioFocusHeadlineDoesNotInventALiftWeek() {
        val answers = OnboardingAnswers(focus = TrainingFocus.CARDIO)
        val plan = RoutineGenerator.generate(answers, emptyList())
        assertEquals(
            "Cardio logging is ready. A lift week is not generated.",
            OnboardingPreviewCopy.headline(answers, plan),
        )
        assertEquals("", OnboardingPreviewCopy.why(answers, plan))
    }

    @Test
    fun aLiftWeekHeadlineNamesTheSplitWhy() {
        val answers = OnboardingAnswers(daysPerWeek = 3, goal = TrainingGoal.GENERAL)
        val plan = RoutineGenerator.generate(answers, emptyList())
        val why = OnboardingPreviewCopy.why(answers, plan)
        assertTrue(why.contains("full body"))
    }
}
