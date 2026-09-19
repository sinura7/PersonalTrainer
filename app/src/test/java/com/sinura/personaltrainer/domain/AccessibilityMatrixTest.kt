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
            "Start a workout (sheet: free / routine / cardio / Extra). Planned rows confirm, then start.",
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
            "Settings is a tab. The root is a short index of rows. Display, Reminders, Week generator, Rest timer, Backup, and About open focused screens. Back is gone on the index. Reminders are per-day workout alarms with a scroll time and AM/PM. The week generator lives on its own Settings screen. Export is Backup's Volt. Restore is never the gym act. Share diagnostics is quiet.",
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
            "Settings is a tab. Home week strip picks the day. Selected day is filled, not faint type. Planned rows open a start confirm, and a row skipped today still opens one. A day block is one button: title, order and count, then its state or Start on the foot; Skip and Up / Down sit inside it. The filled Volt is Start a workout and opens a sheet: free, a Plan routine, cardio, or Extra. Extra asks what equipment is here, then shows matching warm-up and mobility pictures. There is no Get started sheet and no Add row on Home. Day blocks have no clocks; Up / Down rearranges them. This week, Library, Goals and the ready-to-progress list stay off this screen.",
            AccessibilityMatrix.page("home").talkBackNotes,
        )
    }

    @Test
    fun activeStrengthNotesDescribeTheRedesignedFloor() {
        // ADR-027: one filled Volt, and the notes are the TalkBack contract for the new floor.
        assertEquals("Log set", AccessibilityMatrix.page("active-strength").voltAction)
        assertEquals(ACTIVE_STRENGTH_NOTES, AccessibilityMatrix.page("active-strength").talkBackNotes)
    }

    @Test
    fun activeStrengthNotesFollowTheFloorOrder() {
        val notes = AccessibilityMatrix.page("active-strength").talkBackNotes
        val order = listOf(
            "header with its progress line",
            "exercise identity",
            "Details",
            "Working/Warm-up",
            "the stats row (Last set, Best set and Volume)",
            "weight, reps, effort",
            "next set",
            "set history chips",
            "companion, commit",
        )
        val positions = order.map { phrase ->
            val at = notes.indexOf(phrase)
            assertTrue(phrase, at >= 0)
            at
        }
        positions.zipWithNext().forEachIndexed { index, (before, after) ->
            assertTrue("${order[index]} must be spoken before ${order[index + 1]}", before < after)
        }
        // Retired premises must not creep back: no compact identity, no set context stop of its own,
        // no receipt row, and the bar is decorative rather than spoken twice.
        assertFalse(notes.contains("compact exercise identity"))
        assertFalse(notes.contains("Latest saved"))
        assertTrue(notes.contains("the segmented bar is decorative"))
        assertTrue(notes.contains("Switch exercise is explicit"))
        assertTrue(notes.contains("Apply never saves"))
        assertTrue(notes.contains("round plates say the step and unit"))
        assertTrue(notes.contains("Easy and Max effort"))
        assertTrue(notes.contains("edit/delete menus"))
        assertTrue(notes.contains("Time this set"))
        assertTrue(notes.contains("Start rest"))
        assertTrue(notes.contains("disabled reason"))
    }

    @Test
    fun publicCandidateStaysClosedUntilPhysicalTalkBack() {
        assertFalse(AccessibilityMatrix.publicCandidateReady())
        assertEquals(listOf(360, 412, 600), AccessibilityMatrix.widthsDp)
        assertEquals(listOf(1.0, 1.6, 2.0), AccessibilityMatrix.fontScales)
        assertEquals("Body", AccessibilityMatrix.page("body").title)
    }

    private companion object {
        const val ACTIVE_STRENGTH_NOTES =
            "TalkBack order is header with its progress line, exercise identity, Details, Working/Warm-up, the stats row (Last set, Best set and Volume), weight, reps, effort, next set, set history chips, companion, commit. The progress line is spoken once as words; the segmented bar is decorative. The identity is one button that says current, name, lift number, working sets done, equipment and the set context, and ends in Switch exercise; exercise pictures remain decorative. Switch exercise is explicit and Details opens the exercise; Session summary in the overflow separates elapsed time and session totals from exercise progress. Each stat cell speaks its value, and Last set is a Use last time button only while it shows last time's set. Weight, Added weight, and Assistance retain their meanings; zero external load is not called bodyweight, and a bodyweight lift has no weight column. Numeric fields expose Decrease, Increase, and Type actions and select the current value when opened; the round plates say the step and unit. Working/Warm-up and RPE have radio semantics with Easy and Max effort ends; a recommended RPE is spoken as recommended, never selected. RPE help is always available; Clear or the selected choice removes effort, and a warm-up says why the track is hidden. Warm-up presets and Use last time are value-application buttons; the next set suggestion is supporting text with Why and Apply, and Apply never saves. Saving a warm-up returns to Working. Set history chips speak ordinal, set and state and open edit/delete menus; Edit opens labeled working and warm-up rows with edit/delete menus; the current set is a ringed chip and Add set is explicit. Ordinary saves retain the entry position; edits deliberately reveal entry. Commit names its verb, payload and disabled reason. A saved receipt is announced once and the saved chip says so, while timer ticks remain silent. The rest card names its target and the minus 15, plus 15 and Skip actions; Time this set and Start rest are distinct named actions, and a finished rest announces Back to the bar once. Error/undo shares the companion with access to an active clock. Undo honors the accessibility timeout. Font 1.6 and above shrinks the identity picture, stacks weight above reps and reflows choices and values. Reduced motion snaps geometry; dwell and announcements stay."
    }
}
