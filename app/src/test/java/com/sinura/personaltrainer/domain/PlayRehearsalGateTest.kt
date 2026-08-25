package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayRehearsalGateTest {
    @Test
    fun publicCandidateAndPlayStayBlocked() {
        assertTrue(AccessibilityMatrix.pages.any { it.id == "custom-week" })
        assertTrue(AccessibilityMatrix.pages.any { it.id == "activity-composer" })
        assertFalse(AccessibilityMatrix.publicCandidateReady())
        assertTrue(AccessibilityMatrix.pages.none { it.physicalTalkBack })
    }
}
