package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPreviewCopyTest {
    @Test
    fun fullWeekWarningNamesTheChoice() {
        assertEquals("No rest day this week.", OnboardingPreviewCopy.FULL_WEEK_TITLE)
        assertEquals("Use this plan", OnboardingPreviewCopy.FULL_WEEK_CONFIRM_PLAN)
        assertEquals("Use this week", OnboardingPreviewCopy.FULL_WEEK_CONFIRM_WEEK)
    }
}
