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
                "onboarding", "annual-analytics",
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
            "Start a workout (freestyle). Planned rows confirm, then start.",
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
            "Settings is a tab. Back is gone. Weight and hours sit at the top. Reminders are per-day workout alarms. The week generator lives here. Export is the page's Volt. Restore is never the gym act. Share diagnostics is quiet.",
            AccessibilityMatrix.page("settings").talkBackNotes,
        )
        assertEquals(
            "Export to file",
            AccessibilityMatrix.page("settings").voltAction,
        )
        assertEquals("Add session", AccessibilityMatrix.page("plan-day").voltAction)
        assertEquals(
            "Library, Add session, and Log or start cardio are named. Planned Start is Home. The sheet is not a second Volt. Routines start collapsed behind Show routines. Generator lives on Settings. Settings is a tab, not a header gear. Reminders live on Settings.",
            AccessibilityMatrix.page("plan").talkBackNotes,
        )
        assertEquals(
            "Settings is a tab. Home week strip picks the day. Selected day is filled, not faint type. Planned rows open a start confirm, and a row skipped today still opens one. A day block is one button: title, order and count, then its state or Start on the foot; Skip and Up / Down sit inside it. The filled Volt is Start a workout. There is no Get started sheet. Day blocks have no clocks; Up / Down rearranges them. Add sits under Today and asks just-today vs every this weekday. This week, Library, Goals and the ready-to-progress list stay off this screen.",
            AccessibilityMatrix.page("home").talkBackNotes,
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
