package com.sinura.personaltrainer.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two invariants that make editing history safe.
 *
 * Both calculators read a set's own `completedAt` before anything else, so an edit that
 * rewrote it — or an added set stamped "now" — would silently move training from the day it
 * happened onto today: heating the wrong week, and re-dating a personal record.
 */
class EditedSessionAttributionTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L
    private val oldStarted = now - 20 * day
    private val oldFinished = oldStarted + 3_600_000

    private fun benchSet(id: String, weightKg: Double, reps: Int, at: Long) = set(
        id = id,
        sessionId = "old",
        exerciseId = "bench",
        name = "Bench Press",
        weightKg = weightKg,
        reps = reps,
        at = at,
    )

    private fun oldSession(sets: List<SetLog>) = session(
        id = "old",
        finishedAt = oldFinished,
        sets = sets,
        exercises = listOf(sessionExercise("bench", "Bench Press", "Chest")),
        date = oldStarted,
    )

    @Test
    fun addedSetHeatsTheOriginalDayNotToday() {
        val addedAt = FinishedSessionEdits.timestampForAddedSet(
            startedAt = oldStarted,
            finishedAt = oldFinished,
            lastCompletedAt = oldStarted + 60_000,
        )
        val edited = oldSession(
            listOf(
                benchSet("a", 100.0, 5, oldStarted + 60_000),
                benchSet("b", 100.0, 5, addedAt),
            ),
        )

        val thisWeek = MuscleLoadCalculator.snapshot(
            sessions = listOf(edited),
            window = HeatWindow.CURRENT_WEEK,
            nowMs = now,
            zone = zone,
        )
        // The set was added today, but it happened 20 days ago — this week must not see it.
        assertEquals(0.0, thisWeek.load(CanonicalMuscle.CHEST).volumeKg, 0.001)

        val lifetime = MuscleLoadCalculator.snapshot(
            sessions = listOf(edited),
            window = HeatWindow.CURRENT_MONTH,
            nowMs = oldFinished,
            zone = zone,
        )
        assertTrue(lifetime.load(CanonicalMuscle.CHEST).volumeKg > 0.0)
    }

    @Test
    fun editedSetKeepsPrChronologyOnOriginalDay() {
        val oldAt = oldStarted + 60_000
        val newerAt = now - 2 * day
        // The old set is edited upward, past a more recent lighter set.
        val history = listOf(
            ExerciseSetRecord("a", "old", weightKg = 140.0, reps = 5, completedAt = oldAt),
            ExerciseSetRecord("b", "recent", weightKg = 120.0, reps = 5, completedAt = newerAt),
        )

        val bests = PersonalRecords.bests(history, LoadClass.LOADED)
        val weight = bests.getValue(PersonalRecordKind.WEIGHT)
        // The record belongs to the day it was actually lifted, not to the day it was typed.
        assertEquals(oldAt, weight.achievedAt)
    }

    @Test
    fun durationIsNeverRecomputedByEdits() {
        val before = oldSession(listOf(benchSet("a", 100.0, 5, oldStarted + 60_000)))
        val afterEdit = before.copy(
            sets = before.sets.map { it.copy(weightKg = 120.0) },
        )
        // Editing set fields cannot touch the stored duration: finishSession is its only
        // computed writer, and nothing in the edit path recomputes it.
        assertEquals(before.durationMinutes, afterEdit.durationMinutes)
        assertEquals(before.startedAt, afterEdit.startedAt)
        assertEquals(before.finishedAt, afterEdit.finishedAt)
    }
}
