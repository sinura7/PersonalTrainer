package com.sinura.personaltrainer.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
