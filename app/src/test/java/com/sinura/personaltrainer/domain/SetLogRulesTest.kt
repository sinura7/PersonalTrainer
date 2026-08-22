package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetLogRulesTest {
    @Test
    fun allowsZeroKgWarmup() {
        assertNull(SetLogRules.validate(weightKg = 0.0, reps = 8, isWarmup = true))
    }

    @Test
    fun blocksZeroKgWorkingSet() {
        assertEquals(
            SetLogRules.ZERO_WORKING_WEIGHT,
            SetLogRules.validate(weightKg = 0.0, reps = 5, isWarmup = false),
        )
    }

    @Test
    fun allowsPositiveWorkingSet() {
        assertNull(SetLogRules.validate(weightKg = 100.0, reps = 5, isWarmup = false))
    }

    @Test
    fun rejectsInvalidReps() {
        assertEquals(SetLogRules.INVALID_REPS, SetLogRules.validate(60.0, 0, false))
    }

    // ---- load types -------------------------------------------------------------------
    // The four cases above are the v1 suite and predate the loadType parameter entirely, so
    // until now both the 4-arg validate() and the whole of requiresWeight() shipped untested.

    @Test
    fun onlyExternallyLoadedLiftsRequireAWeight() {
        // The when() in requiresWeight is exhaustive over LoadType, so pinning all five plus
        // null is the whole contract, and a future entry cannot be added without landing here.
        assertTrue(SetLogRules.requiresWeight(LoadType.EXTERNAL))
        assertTrue("a pin stack still has a number to enter", SetLogRules.requiresWeight(LoadType.STACK))
        assertTrue("null is the stricter reading", SetLogRules.requiresWeight(null))

        assertFalse(SetLogRules.requiresWeight(LoadType.BODYWEIGHT))
        assertFalse("an unweighted pull-up is a complete set", SetLogRules.requiresWeight(LoadType.BODYWEIGHT_PLUS))
        assertFalse("zero assistance is the hardest version", SetLogRules.requiresWeight(LoadType.ASSISTED))
    }

    @Test
    fun zeroKgWorkingSetIsAllowedForEveryLiftThatCannotBeLoaded() {
        listOf(LoadType.BODYWEIGHT, LoadType.BODYWEIGHT_PLUS, LoadType.ASSISTED).forEach { loadType ->
            assertNull(
                "a 0 kg working set must be loggable for $loadType",
                SetLogRules.validate(weightKg = 0.0, reps = 8, isWarmup = false, loadType = loadType),
            )
        }
    }

    @Test
    fun zeroKgWorkingSetIsStillBlockedForLoadedLifts() {
        listOf(LoadType.EXTERNAL, LoadType.STACK, null).forEach { loadType ->
            assertEquals(
                "a 0 kg working set must still be refused for $loadType",
                SetLogRules.ZERO_WORKING_WEIGHT,
                SetLogRules.validate(weightKg = 0.0, reps = 5, isWarmup = false, loadType = loadType),
            )
        }
    }

    @Test
    fun theRepsRuleAppliesWhateverTheLoadType() {
        // Ordering matters: the zero-weight test sits above the reps test in validate(), so a
        // bodyweight set skipping the first must still be caught by the second.
        assertEquals(
            SetLogRules.INVALID_REPS,
            SetLogRules.validate(weightKg = 0.0, reps = 0, isWarmup = false, loadType = LoadType.BODYWEIGHT),
        )
    }

    @Test
    fun aNegativeOrNonFiniteWeightIsRefusedForEveryLoadType() {
        (LoadType.entries + null).forEach { loadType ->
            assertEquals(
                "negative weight, $loadType",
                SetLogRules.INVALID_WEIGHT,
                SetLogRules.validate(weightKg = -1.0, reps = 5, isWarmup = false, loadType = loadType),
            )
            assertEquals(
                "NaN weight, $loadType",
                SetLogRules.INVALID_WEIGHT,
                SetLogRules.validate(weightKg = Double.NaN, reps = 5, isWarmup = false, loadType = loadType),
            )
        }
    }

}