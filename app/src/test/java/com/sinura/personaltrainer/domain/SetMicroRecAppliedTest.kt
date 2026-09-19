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
        val rec = rec(nextWeightKg = WeightConverter.lbsToKg(70.0), nextReps = 10, nextRpe = 9)
        assertTrue(rec.isApplied(weightKg = WeightConverter.lbsToKg(70.0), reps = 10, rpe = 9, unit = WeightUnit.LBS))
        // An off-grid stored kilogram value that displays as the same pounds still counts.
        assertTrue(rec.isApplied(weightKg = WeightConverter.lbsToKg(70.0) + 0.04, reps = 10, rpe = 9, unit = WeightUnit.LBS))
        assertFalse("reps differ", rec.isApplied(weightKg = WeightConverter.lbsToKg(70.0), reps = 11, rpe = 9, unit = WeightUnit.LBS))
        assertFalse("effort differs", rec.isApplied(weightKg = WeightConverter.lbsToKg(70.0), reps = 10, rpe = 8, unit = WeightUnit.LBS))
        assertFalse("effort missing", rec.isApplied(weightKg = WeightConverter.lbsToKg(70.0), reps = 10, rpe = null, unit = WeightUnit.LBS))
        assertFalse("a step away", rec.isApplied(weightKg = WeightConverter.lbsToKg(75.0), reps = 10, rpe = 9, unit = WeightUnit.LBS))
    }

    @Test
    fun appliedFollowsTheSameCoercionsAsApply() {
        // Apply never writes fewer than one rep or a negative load, and it clears an effort
        // the suggestion does not carry, so Applied reads the same way.
        val floor = rec(nextWeightKg = -5.0, nextReps = 0, nextRpe = null)
        assertTrue(floor.isApplied(weightKg = 0.0, reps = 1, rpe = null, unit = WeightUnit.KG))
        assertFalse("a chosen effort would be cleared", floor.isApplied(weightKg = 0.0, reps = 1, rpe = 7, unit = WeightUnit.KG))
        assertFalse(floor.isApplied(weightKg = 0.0, reps = 0, rpe = null, unit = WeightUnit.KG))
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
