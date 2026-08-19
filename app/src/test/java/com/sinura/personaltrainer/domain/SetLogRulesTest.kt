package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}