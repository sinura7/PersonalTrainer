package com.sinura.personaltrainer.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The cache keeps the session's notes once per session, as saved state keeps them, beside the
 * per-lift entries whose own [WorkoutDraft.notes] copies fall behind when another lift is
 * selected while the notes grow (N1). A reopened workout reads these, never a lift's copy.
 */
class WorkoutDraftCacheSessionNotesTest {
    @Test
    fun aLiftsEntryStagesTheSessionsNotesAndAnOlderEntryIsKeptAsWritten() {
        val cache = WorkoutDraftCache()
        val squat = entry(exerciseId = "squat", notes = "felt good")
        cache.put(squat)
        cache.put(entry(exerciseId = "row", notes = "felt good, left knee sore"))

        assertEquals(
            "the last entry's notes are the session's",
            "felt good, left knee sore",
            cache.sessionNotes("s1"),
        )
        assertEquals("the squat's entry is kept exactly as it was written", squat, cache.getLift("s1", "squat"))
    }

    @Test
    fun notesStagedWithNoEntryAreTheSessionsAndLeaveTheEntriesAndSelectionAlone() {
        val cache = WorkoutDraftCache()
        val squat = entry(exerciseId = "squat", notes = "felt good")
        cache.put(squat)
        cache.select("s1", "press")

        cache.putSessionNotes("s1", "typed while the press loads")

        assertEquals(
            "notes staged on their own are the session's",
            "typed while the press loads",
            cache.sessionNotes("s1"),
        )
        assertEquals("the squat's entry is untouched", squat, cache.getLift("s1", "squat"))
        assertNull("staging notes makes no entry for the press", cache.getLift("s1", "press"))
        assertEquals("staging notes keeps the selection", "press", cache.selectedExerciseId("s1"))
    }

    @Test
    fun replacingTheEntriesKeepsTheSessionsNotes() {
        val cache = WorkoutDraftCache()
        cache.put(entry(exerciseId = "row", notes = "felt good, left knee sore"))

        cache.replaceAll(
            sessionId = "s1",
            lifts = mapOf("squat" to entry(exerciseId = "squat", notes = "felt good")),
            selectedExerciseId = "squat",
        )

        assertEquals(
            "a restore's replaceAll keeps the notes it did not write",
            "felt good, left knee sore",
            cache.sessionNotes("s1"),
        )
    }

    @Test
    fun removingALiftKeepsTheSessionsNotes() {
        val cache = WorkoutDraftCache()
        cache.put(entry(exerciseId = "squat", notes = "felt good"))
        cache.put(entry(exerciseId = "press", notes = "felt good, left knee sore"))

        cache.removeLift("s1", "press")

        assertEquals(
            "the notes outlive the lift they were typed on",
            "felt good, left knee sore",
            cache.sessionNotes("s1"),
        )
    }

    @Test
    fun theSessionsNotesAreUnknownUntilStagedAndGoWithTheSession() {
        val cache = WorkoutDraftCache()
        assertNull("nothing staged yet", cache.sessionNotes("s1"))
        cache.select("s1", "squat")
        assertNull("a selection alone stages no notes", cache.sessionNotes("s1"))
        cache.putSessionNotes("s1", "")
        assertEquals("cleared notes are staged as empty, not unknown", "", cache.sessionNotes("s1"))

        cache.putSessionNotes("s1", "felt good")
        cache.clear("s1")
        assertNull("clearing the session drops its notes", cache.sessionNotes("s1"))

        cache.putSessionNotes("s1", "felt good")
        cache.clearAll()
        assertNull("clearing every session drops its notes", cache.sessionNotes("s1"))
    }

    @Test
    fun eachSessionKeepsItsOwnNotes() {
        val cache = WorkoutDraftCache()
        cache.put(entry(exerciseId = "squat", notes = "morning", sessionId = "a"))
        cache.putSessionNotes("b", "evening")

        assertEquals("session a keeps its notes", "morning", cache.sessionNotes("a"))
        assertEquals("session b keeps its notes", "evening", cache.sessionNotes("b"))
    }

    @Test
    fun confirmedReopenRetiresSettledOwnersButNeverAnOlderActiveWriter() {
        val cache = WorkoutDraftCache()
        val older = Any()
        val newer = Any()
        cache.putSessionNotes("s1", "latest")
        cache.markNotesPending("s1", older, pending = true, busy = true, notes = "older")
        cache.markNotesPending("s1", newer, pending = true, busy = false, notes = "latest")
        cache.markNotesPending("s1", newer, pending = false, busy = false, notes = "latest")
        assertTrue("confirming new text does not forget an older write", cache.hasPendingNotes("s1"))
        cache.markNotesPending("s1", older, pending = true, busy = false, notes = "older")
        cache.markNotesPending("s1", newer, pending = false, busy = false, notes = "latest")
        assertFalse("the reopened confirmed draft retires settled protection", cache.hasPendingNotes("s1"))
    }

    @Test
    fun unrelatedStoredTextCannotClearTheCurrentFailedDraftsProtection() {
        val cache = WorkoutDraftCache()
        cache.putSessionNotes("s1", "authored")
        cache.markNotesPending("s1", Any(), pending = true, busy = false, notes = "authored")
        cache.markNotesPending("s1", Any(), pending = false, busy = false, notes = "stored")
        assertTrue(cache.hasPendingNotes("s1"))
        cache.replaceAll("s1", emptyMap(), null)
        assertTrue("restoring lift entries keeps the session's notes barrier", cache.hasPendingNotes("s1"))
        cache.clear("s1")
        assertFalse(cache.hasPendingNotes("s1"))
    }

    @Test
    fun restoringAnEarlierLiveBackupAllowsItsPreviouslyFinishedSessionIdToBeEdited() {
        val cache = WorkoutDraftCache()
        cache.put(entry(exerciseId = "squat", notes = "before finish"))
        assertTrue(cache.beginFinish("s1"))
        cache.finishConfirmed("s1")
        cache.endFinish("s1")
        cache.editLiveNotes("s1") { cache.putSessionNotes("s1", "stale editor") }
        assertNull("the old editor cannot recreate the finished draft", cache.sessionNotes("s1"))

        // Restore clears process drafts before opening the earlier live row with this ID.
        cache.clearAll()
        cache.put(entry(exerciseId = "squat", notes = "restored live note"))
        cache.editLiveNotes("s1") { cache.putSessionNotes("s1", "new words after restore") }
        assertFalse("a prior finish does not lock a restored live row", cache.liveEntryLocked("s1"))
        assertEquals("new words after restore", cache.sessionNotes("s1"))
        assertEquals("squat", cache.selectedExerciseId("s1"))
    }

    private fun entry(exerciseId: String, notes: String, sessionId: String = "s1") = WorkoutDraft(
        sessionId = sessionId,
        exerciseId = exerciseId,
        weightKg = 100.0,
        reps = 5,
        rpe = null,
        isWarmup = false,
        notes = notes,
    )
}
