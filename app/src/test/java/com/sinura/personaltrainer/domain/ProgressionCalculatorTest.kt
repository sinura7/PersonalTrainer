package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionCalculatorTest {
    /** The kilogram step, which every case below uses unless it is testing the absence of one. */
    private val kg = IncrementTable.STEP_KG

    @Test
    fun completedTargetRepsAddsTwoPointFiveKg() {
        val suggested = ProgressionCalculator.suggestWeightKg(
            lastWeightKg = 80.0,
            lastWorkingReps = 5,
            targetReps = 5,
            stepKg = kg,
            weightMeaning = WeightMeaning.LIFTED,
        )
        assertEquals(82.5, suggested, 0.001)
        assertEquals(ProgressionAction.INCREASE, ProgressionCalculator.action(5, 5))
    }

    @Test
    fun extraRepsStillIncrease() {
        val suggested = ProgressionCalculator.suggestWeightKg(100.0, 8, 5, kg, WeightMeaning.LIFTED)
        assertEquals(102.5, suggested, 0.001)
    }

    @Test
    fun oneOrTwoRepsShortHoldsWeight() {
        assertEquals(80.0, ProgressionCalculator.suggestWeightKg(80.0, 4, 5, kg, WeightMeaning.LIFTED), 0.001)
        assertEquals(80.0, ProgressionCalculator.suggestWeightKg(80.0, 3, 5, kg, WeightMeaning.LIFTED), 0.001)
        assertEquals(ProgressionAction.HOLD, ProgressionCalculator.action(4, 5))
        assertEquals(ProgressionAction.HOLD, ProgressionCalculator.action(3, 5))
    }

    @Test
    fun threeOrMoreRepsShortDropsTwoPointFiveKg() {
        assertEquals(77.5, ProgressionCalculator.suggestWeightKg(80.0, 2, 5, kg, WeightMeaning.LIFTED), 0.001)
        assertEquals(ProgressionAction.DECREASE, ProgressionCalculator.action(2, 5))
    }

    @Test
    fun suggestionNeverGoesBelowZero() {
        assertEquals(0.0, ProgressionCalculator.suggestWeightKg(2.0, 0, 5, kg, WeightMeaning.LIFTED), 0.001)
    }

    @Test
    fun aPoundUserStepsInPoundsNotConvertedKilograms() {
        // The recorded "+5.5 lbs" defect: 2.5 kg rendered in pounds. The step is now decided in
        // the unit the lifter reads, so this must come back as exactly five pounds.
        val step = IncrementTable.stepKg(LoadType.EXTERNAL, WeightUnit.LBS)
        val suggested = ProgressionCalculator.suggestWeightKg(100.0, 5, 5, step, WeightMeaning.LIFTED)
        val delta = WeightConverter.toDisplayValue(suggested, WeightUnit.LBS) -
            WeightConverter.toDisplayValue(100.0, WeightUnit.LBS)
        assertEquals(5.0, delta, 0.05)
    }

    @Test
    fun aLiftWithNoStepHoldsItsWeightAndStillReadsAsProgress() {
        // A push-up. There is nothing to add, so the suggestion must not invent a load — but
        // the action stays INCREASE, because the lifter DID hit target and the copy layer turns
        // that into "add a rep".
        val suggested = ProgressionCalculator.suggestWeightKg(0.0, 12, 12, stepKg = null, WeightMeaning.LIFTED)
        assertEquals(0.0, suggested, 0.001)
        assertEquals(ProgressionAction.INCREASE, ProgressionCalculator.action(12, 12))

        // And it does not go backwards on a missed target either.
        assertEquals(0.0, ProgressionCalculator.suggestWeightKg(0.0, 5, 12, stepKg = null, WeightMeaning.LIFTED), 0.001)
    }

    @Test
    fun hintBundlesLastAndSuggestedLoad() {
        val hint = ProgressionCalculator.hint(
            exerciseId = "ex-squat",
            exerciseName = "Barbell Back Squat",
            lastWeightKg = 60.0,
            lastWorkingReps = 5,
            targetReps = 5,
            stepKg = kg,
            loadType = LoadType.EXTERNAL,
        )
        assertEquals(62.5, hint.suggestedWeightKg, 0.001)
        assertEquals(ProgressionAction.INCREASE, hint.action)
        assertEquals(LoadType.EXTERNAL, hint.loadType)
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
