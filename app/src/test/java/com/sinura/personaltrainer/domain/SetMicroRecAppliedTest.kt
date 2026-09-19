package com.sinura.personaltrainer.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Applied is the domain's word, not the screen's: the entry matches the suggestion exactly
 * as Apply would write it, coercions included, so the control can stand down honestly.
 */
class SetMicroRecAppliedTest {
    @Test
    fun appliedMeansTheEntryHoldsTheSuggestionAsApplyWouldWriteIt() {
        val rec = rec(nextWeightKg = 31.75, nextReps = 10, nextRpe = 9)
        assertTrue(rec.isApplied(weightKg = 31.75, reps = 10, rpe = 9))
        // A rounded pound value lands within the tolerance.
        assertTrue(rec.isApplied(weightKg = 31.751, reps = 10, rpe = 9))
        assertFalse("reps differ", rec.isApplied(weightKg = 31.75, reps = 11, rpe = 9))
        assertFalse("effort differs", rec.isApplied(weightKg = 31.75, reps = 10, rpe = 8))
        assertFalse("effort missing", rec.isApplied(weightKg = 31.75, reps = 10, rpe = null))
        assertFalse("a step away", rec.isApplied(weightKg = 34.0, reps = 10, rpe = 9))
    }

    @Test
    fun appliedFollowsTheSameCoercionsAsApply() {
        // Apply never writes fewer than one rep or a negative load, so Applied reads the same way.
        val floor = rec(nextWeightKg = -5.0, nextReps = 0, nextRpe = null)
        assertTrue(floor.isApplied(weightKg = 0.0, reps = 1, rpe = 7))
        assertFalse(floor.isApplied(weightKg = 0.0, reps = 0, rpe = null))
    }

    private fun rec(nextWeightKg: Double, nextReps: Int, nextRpe: Int?) = SetMicroRec(
        nextWeightKg = nextWeightKg,
        nextReps = nextReps,
        nextRpe = nextRpe,
        previewOnly = false,
        showApply = true,
        reasonCode = "PLUS_REP",
        trace = RuleTrace.forMicroRec(
            reasonCodes = listOf("PLUS_REP"),
            nextWeightKg = nextWeightKg,
            nextReps = nextReps,
            nextRpe = nextRpe,
            nowMs = 0L,
            todayEpochDay = 0L,
        ),
    )
}
