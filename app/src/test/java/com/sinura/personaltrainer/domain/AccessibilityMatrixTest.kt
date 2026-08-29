package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityMatrixTest {
    @Test
    fun everyPagePacketIsInventoried() {
        val ids = AccessibilityMatrix.pages.map { it.id }
        assertTrue(ids.containsAll(
            listOf(
                "home", "body", "plan", "plan-day", "history", "library", "settings",
                "active-strength", "active-cardio", "active-mixed",
                "routine-editor", "custom-week", "summary", "session-detail",
                "exercise-detail", "activity-detail", "activity-composer",
                "goals", "onboarding", "annual-analytics",
            ),
        ))
        assertEquals(ids.toSet().size, ids.size)
    }

    @Test
    fun everyPageListsRequiredStatesAndOneVoltAction() {
        AccessibilityMatrix.pages.forEach { page ->
            assertTrue(page.id, AccessibilityMatrix.missingRequiredState(page).isEmpty())
            assertTrue(page.id, page.voltAction.isNotBlank())
            assertTrue(page.id, page.talkBackNotes.isNotBlank())
            assertTrue(page.id, page.automatedEvidence)
            assertFalse("physical TalkBack is still outstanding", page.physicalTalkBack)
        }
        assertEquals(
            "Start today's planned session when a week is pinned. Generate a schedule on a first visit. Free workout stays quiet.",
            AccessibilityMatrix.page("home").voltAction,
        )
        assertEquals(
            "None. History is a readout; Start lives on Home.",
            AccessibilityMatrix.page("history").voltAction,
        )
        assertEquals(
            "None. Body is a readout; Start lives on Home.",
            AccessibilityMatrix.page("body").voltAction,
        )
        assertEquals(
            "Settings is a tab. Back is gone. Reminders and export stay reachable. Share diagnostics is quiet.",
            AccessibilityMatrix.page("settings").talkBackNotes,
        )
        assertEquals(
            "Export to file — backup is never the page's Volt gym act",
            AccessibilityMatrix.page("settings").voltAction,
        )
        assertEquals("Add session", AccessibilityMatrix.page("plan-day").voltAction)
        assertEquals(
            "Tune, Library, and Add session are named. Start is Home, not Plan. Lighter stays behind Tune. Settings is a tab, not a header gear.",
            AccessibilityMatrix.page("plan").talkBackNotes,
        )
    }

    @Test
    fun publicCandidateStaysClosedUntilPhysicalTalkBack() {
        assertFalse(AccessibilityMatrix.publicCandidateReady())
        assertEquals(listOf(360, 412, 600), AccessibilityMatrix.widthsDp)
        assertEquals(listOf(1.0, 1.6, 2.0), AccessibilityMatrix.fontScales)
        assertEquals("Body", AccessibilityMatrix.page("body").title)
    }
}
