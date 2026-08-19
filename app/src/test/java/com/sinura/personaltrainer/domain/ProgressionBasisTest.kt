package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Encodes the audit's back-off-set failure: the engine used to judge progression on whatever
 * set was logged last, so a top set followed by a lighter back-off set quietly lowered the
 * next session's suggested weight.
 */
class ProgressionBasisTest {
    @Test
    fun backOffSetAfterATopSetDoesNotBecomeTheBasis() {
        // The canonical bug: squat 100 x 5 (target met), then a back-off 80 x 8.
        // Chronologically last = 80 x 8, which read as "target cleared, add 2.5 to 80".
        val session = listOf(
            WorkingSetCandidate(weightKg = 100.0, reps = 5, completedAt = 1_000L),
            WorkingSetCandidate(weightKg = 80.0, reps = 8, completedAt = 2_000L),
        )
        val top = ProgressionBasis.topWorkingSet(session)!!
        assertEquals(100.0, top.weightKg, 0.001)
        assertEquals(5, top.reps)

        // Pin the regression itself: the old rule (ORDER BY completedAt DESC LIMIT 1) would
        // have chosen the back-off set, and this test would be worthless if it still agreed.
        val oldRuleWouldPick = session.maxByOrNull { it.completedAt }!!
        assertEquals(80.0, oldRuleWouldPick.weightKg, 0.001)
        assertNotEquals(oldRuleWouldPick.weightKg, top.weightKg, 0.001)
    }

    @Test
    fun theWholeChainSuggestsFromTheTopSetNotTheBackOff() {
        // End to end through the calculator: 102.5, never 82.5.
        val session = listOf(
            WorkingSetCandidate(100.0, 5, 1_000L),
            WorkingSetCandidate(80.0, 8, 2_000L),
        )
        val top = ProgressionBasis.topWorkingSet(session)!!
        val hint = ProgressionCalculator.hint(
            exerciseId = "ex-squat",
            exerciseName = "Barbell Back Squat",
            lastWeightKg = top.weightKg,
            lastWorkingReps = top.reps,
            targetReps = 5,
        )
        assertEquals(ProgressionAction.INCREASE, hint.action)
        assertEquals(102.5, hint.suggestedWeightKg, 0.001)
        // The basis shown in the UI must be the set the decision was made on.
        assertEquals(100.0, hint.lastWeightKg, 0.001)
        assertEquals(5, hint.lastReps)
    }

    @Test
    fun aFatiguedFinalSetIsNotTheSoleSignalOnStraightSets() {
        // 100 x 5, 100 x 5, 100 x 4 against a target of 5. The old rule saw only the 4.
        val session = listOf(
            WorkingSetCandidate(100.0, 5, 1_000L),
            WorkingSetCandidate(100.0, 5, 2_000L),
            WorkingSetCandidate(100.0, 4, 3_000L),
        )
        val top = ProgressionBasis.topWorkingSet(session)!!
        assertEquals(100.0, top.weightKg, 0.001)
        assertEquals(5, top.reps) // best reps at the working weight, not the last
        assertEquals(ProgressionAction.INCREASE, ProgressionCalculator.action(top.reps, 5))
    }

    @Test
    fun aGenuineMissStillHolds() {
        // Every set short of target: the fix must not turn misses into progress.
        val session = listOf(
            WorkingSetCandidate(100.0, 4, 1_000L),
            WorkingSetCandidate(100.0, 3, 2_000L),
        )
        val top = ProgressionBasis.topWorkingSet(session)!!
        assertEquals(4, top.reps)
        assertEquals(ProgressionAction.HOLD, ProgressionCalculator.action(top.reps, 5))
    }

    @Test
    fun aBigMissStillDeloads() {
        val session = listOf(WorkingSetCandidate(100.0, 1, 1_000L))
        val top = ProgressionBasis.topWorkingSet(session)!!
        val suggestion = ProgressionCalculator.suggestWeightKg(top.weightKg, top.reps, 5)
        assertEquals(ProgressionAction.DECREASE, ProgressionCalculator.action(top.reps, 5))
        assertEquals(97.5, suggestion, 0.001)
    }

    @Test
    fun ascendingPyramidAnchorsOnTheHeaviestSet() {
        val session = listOf(
            WorkingSetCandidate(60.0, 10, 1_000L),
            WorkingSetCandidate(80.0, 8, 2_000L),
            WorkingSetCandidate(110.0, 3, 3_000L),
        )
        assertEquals(110.0, ProgressionBasis.topWorkingSet(session)!!.weightKg, 0.001)
    }

    @Test
    fun equalWeightAndRepsTieBreakToTheLaterSet() {
        val session = listOf(
            WorkingSetCandidate(100.0, 5, 1_000L),
            WorkingSetCandidate(100.0, 5, 5_000L),
        )
        assertEquals(5_000L, ProgressionBasis.topWorkingSet(session)!!.completedAt)
    }

    @Test
    fun orderOfLoggingDoesNotChangeTheChoice() {
        val ascending = listOf(
            WorkingSetCandidate(80.0, 8, 2_000L),
            WorkingSetCandidate(100.0, 5, 1_000L),
        )
        val descending = ascending.reversed()
        assertEquals(
            ProgressionBasis.topWorkingSet(ascending),
            ProgressionBasis.topWorkingSet(descending),
        )
    }

    @Test
    fun noWorkingSetsMeansNoBasis() {
        assertNull(ProgressionBasis.topWorkingSet(emptyList()))
    }

    @Test
    fun aSingleSetIsItsOwnTopSet() {
        val only = WorkingSetCandidate(60.0, 12, 1_000L)
        assertEquals(only, ProgressionBasis.topWorkingSet(listOf(only)))
    }
}
