package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The assisted mirror of the back-off bug (audit finding N1).
 *
 * For an assisted lift the stored `weightKg` is machine ASSISTANCE — weight taken OFF the
 * lifter — so MORE kilograms is an EASIER set. The old selector picked `max(weightKg)`, which
 * for assistance is the set with the most help: the easiest set became the progression basis,
 * and [ProgressionCalculator] (which correctly inverts the assisted DIRECTION) was then fed the
 * wrong reference. These tests pin that the hardest — least-assisted — set is the basis, and
 * that pure bodyweight is chosen on reps.
 */
class ProgressionBasisAssistedTest {
    @Test
    fun assistedTopSetIsTheLeastAssistedSetNotTheMost() {
        // One session, same reps, differing help: 10 kg assist (hard) then 20 kg assist (easy).
        val session = listOf(
            WorkingSetCandidate(weightKg = 10.0, reps = 8, completedAt = 1_000L),
            WorkingSetCandidate(weightKg = 20.0, reps = 8, completedAt = 2_000L),
        )
        val top = ProgressionBasis.topWorkingSet(session, WeightMeaning.ASSISTANCE)!!
        assertEquals(10.0, top.weightKg, 0.001) // least assistance = hardest

        // Pin the regression: the old rule (max weightKg) would have picked the 20 kg-assist
        // set, the easiest one — this test is worthless if it still agrees with that.
        val oldRuleWouldPick = session.maxByOrNull { it.weightKg }!!
        assertEquals(20.0, oldRuleWouldPick.weightKg, 0.001)
        assertNotEquals(oldRuleWouldPick.weightKg, top.weightKg, 0.001)
    }

    @Test
    fun theBasisFeedsSuggestFromTheHardestAssistedSet() {
        // Assisted pull-up: 10 kg assist × 8 (hit target) then a 20 kg-assist back-off × 8.
        // The basis must be the 10 kg set, and the suggestion must DROP assist from there
        // (10 -> 7.5), not from the easier 20 kg back-off (which would read as 17.5).
        val session = listOf(
            WorkingSetCandidate(10.0, 8, 1_000L),
            WorkingSetCandidate(20.0, 8, 2_000L),
        )
        val top = ProgressionBasis.topWorkingSet(session, WeightMeaning.ASSISTANCE)!!
        val hint = ProgressionCalculator.hint(
            exerciseId = "ex-assisted-pullup",
            exerciseName = "Assisted Pull-Up",
            lastWeightKg = top.weightKg,
            lastWorkingReps = top.reps,
            targetReps = 8,
            displayStep = IncrementTable.STEP_KG,
            loadType = LoadType.ASSISTED,
            unit = WeightUnit.KG,
        )
        assertEquals(ProgressionAction.INCREASE, hint.action)
        assertEquals(10.0, hint.lastWeightKg, 0.001) // basis is the hardest set
        assertEquals(7.5, hint.suggestedWeightKg, 0.001) // less help next time, not more
    }

    @Test
    fun assistanceRisingWithFatigueStillPicksTheLeastAssistedSet() {
        // Help climbs set to set as the lifter tires: 5 -> 12.5 -> 20 kg of assist.
        // The first, least-assisted set is the hardest and must be the basis, whatever order
        // it was logged in.
        val session = listOf(
            WorkingSetCandidate(20.0, 6, 3_000L),
            WorkingSetCandidate(5.0, 8, 1_000L),
            WorkingSetCandidate(12.5, 7, 2_000L),
        )
        val top = ProgressionBasis.topWorkingSet(session, WeightMeaning.ASSISTANCE)!!
        assertEquals(5.0, top.weightKg, 0.001)
        assertEquals(8, top.reps)
    }

    @Test
    fun aGenuineAssistedMissStillGivesHelpBack() {
        // Every set well short of target, all at 20 kg assist: the fix must not turn a miss
        // into "take help away". The basis is still the least-assisted set; here they tie on
        // weight so the most-reps set wins, and the direction gives assistance back.
        val session = listOf(
            WorkingSetCandidate(20.0, 4, 1_000L),
            WorkingSetCandidate(20.0, 3, 2_000L),
        )
        val top = ProgressionBasis.topWorkingSet(session, WeightMeaning.ASSISTANCE)!!
        assertEquals(20.0, top.weightKg, 0.001)
        assertEquals(4, top.reps)
        assertEquals(ProgressionAction.DECREASE, ProgressionCalculator.action(top.reps, 8))
        val suggestion = ProgressionCalculator.suggestWeightKg(
            lastWeightKg = top.weightKg,
            lastWorkingReps = top.reps,
            targetReps = 8,
            displayStep = IncrementTable.STEP_KG,
            weightMeaning = WeightMeaning.ASSISTANCE,
            unit = WeightUnit.KG,
        )
        assertEquals(22.5, suggestion, 0.001) // more help for someone who is failing
    }

    @Test
    fun assistedTieOnWeightBreaksToMoreRepsThenLaterTime() {
        // Same (least) assistance across sets: the stronger set is the one with more reps, and
        // an exact tie there resolves to the later completedAt.
        val byReps = listOf(
            WorkingSetCandidate(10.0, 6, 1_000L),
            WorkingSetCandidate(10.0, 9, 2_000L),
            WorkingSetCandidate(10.0, 7, 3_000L),
        )
        assertEquals(9, ProgressionBasis.topWorkingSet(byReps, WeightMeaning.ASSISTANCE)!!.reps)

        val byTime = listOf(
            WorkingSetCandidate(10.0, 8, 1_000L),
            WorkingSetCandidate(10.0, 8, 9_000L),
        )
        assertEquals(9_000L, ProgressionBasis.topWorkingSet(byTime, WeightMeaning.ASSISTANCE)!!.completedAt)
    }

    @Test
    fun assistedChoiceIsIndependentOfLoggingOrder() {
        val ascending = listOf(
            WorkingSetCandidate(20.0, 8, 2_000L),
            WorkingSetCandidate(10.0, 8, 1_000L),
        )
        assertEquals(
            ProgressionBasis.topWorkingSet(ascending, WeightMeaning.ASSISTANCE),
            ProgressionBasis.topWorkingSet(ascending.reversed(), WeightMeaning.ASSISTANCE),
        )
    }

    // ---- pure bodyweight: reps are the measure ----

    @Test
    fun bodyweightTopSetIsTheMostRepsSet() {
        // Push-ups: no external load, so weightKg is not the measure. The 15-rep set is the
        // basis, not the (irrelevant) weight column.
        val session = listOf(
            WorkingSetCandidate(0.0, 12, 1_000L),
            WorkingSetCandidate(0.0, 15, 2_000L),
            WorkingSetCandidate(0.0, 10, 3_000L),
        )
        val top = ProgressionBasis.topWorkingSet(session, WeightMeaning.NONE)!!
        assertEquals(15, top.reps)
    }

    @Test
    fun bodyweightTieOnRepsBreaksToLaterTime() {
        val session = listOf(
            WorkingSetCandidate(0.0, 12, 1_000L),
            WorkingSetCandidate(0.0, 12, 4_000L),
        )
        assertEquals(4_000L, ProgressionBasis.topWorkingSet(session, WeightMeaning.NONE)!!.completedAt)
    }
}
