package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionCalculatorTest {
    @Test
    fun completedTargetRepsAddsTwoPointFiveKg() {
        val suggested = ProgressionCalculator.suggestWeightKg(
            lastWeightKg = 80.0,
            lastWorkingReps = 5,
            targetReps = 5,
        )
        assertEquals(82.5, suggested, 0.001)
        assertEquals(ProgressionAction.INCREASE, ProgressionCalculator.action(5, 5))
    }

    @Test
    fun extraRepsStillIncrease() {
        val suggested = ProgressionCalculator.suggestWeightKg(100.0, 8, 5)
        assertEquals(102.5, suggested, 0.001)
    }

    @Test
    fun oneOrTwoRepsShortHoldsWeight() {
        assertEquals(80.0, ProgressionCalculator.suggestWeightKg(80.0, 4, 5), 0.001)
        assertEquals(80.0, ProgressionCalculator.suggestWeightKg(80.0, 3, 5), 0.001)
        assertEquals(ProgressionAction.HOLD, ProgressionCalculator.action(4, 5))
        assertEquals(ProgressionAction.HOLD, ProgressionCalculator.action(3, 5))
    }

    @Test
    fun threeOrMoreRepsShortDropsTwoPointFiveKg() {
        assertEquals(77.5, ProgressionCalculator.suggestWeightKg(80.0, 2, 5), 0.001)
        assertEquals(ProgressionAction.DECREASE, ProgressionCalculator.action(2, 5))
    }

    @Test
    fun suggestionNeverGoesBelowZero() {
        assertEquals(0.0, ProgressionCalculator.suggestWeightKg(2.0, 0, 5), 0.001)
    }

    @Test
    fun hintBundlesLastAndSuggestedLoad() {
        val hint = ProgressionCalculator.hint(
            exerciseId = "ex-squat",
            exerciseName = "Barbell Back Squat",
            lastWeightKg = 60.0,
            lastWorkingReps = 5,
            targetReps = 5,
        )
        assertEquals(62.5, hint.suggestedWeightKg, 0.001)
        assertEquals(ProgressionAction.INCREASE, hint.action)
    }
}

class WeightFormatTest {
    @Test
    fun formatsWholeAndHalfKilograms() {
        assertEquals("80 kg", 80.0.toKgLabel())
        assertEquals("82.5 kg", 82.5.toKgLabel())
        assertEquals("80", 80.0.toKgNumber())
        assertEquals("82.5", 82.5.toKgNumber())
    }
}
