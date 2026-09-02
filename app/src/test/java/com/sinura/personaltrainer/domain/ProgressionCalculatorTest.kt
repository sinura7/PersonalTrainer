package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionCalculatorTest {
    /** The kilogram step, which every case below uses unless it is testing the absence of one. */
    private val kg = IncrementTable.STEP_KG

    private fun suggest(
        lastWeightKg: Double,
        lastWorkingReps: Int,
        targetReps: Int,
        displayStep: Double? = kg,
        weightMeaning: WeightMeaning = WeightMeaning.LIFTED,
        unit: WeightUnit = WeightUnit.KG,
    ): Double = ProgressionCalculator.suggestWeightKg(
        lastWeightKg = lastWeightKg,
        lastWorkingReps = lastWorkingReps,
        targetReps = targetReps,
        displayStep = displayStep,
        weightMeaning = weightMeaning,
        unit = unit,
    )

    @Test
    fun completedTargetRepsAddsTwoPointFiveKg() {
        assertEquals(82.5, suggest(80.0, 5, 5), 0.001)
        assertEquals(ProgressionAction.INCREASE, ProgressionCalculator.action(5, 5))
    }

    @Test
    fun extraRepsStillIncrease() {
        assertEquals(102.5, suggest(100.0, 8, 5), 0.001)
    }

    @Test
    fun oneOrTwoRepsShortHoldsWeight() {
        assertEquals(80.0, suggest(80.0, 4, 5), 0.001)
        assertEquals(80.0, suggest(80.0, 3, 5), 0.001)
        assertEquals(ProgressionAction.HOLD, ProgressionCalculator.action(4, 5))
        assertEquals(ProgressionAction.HOLD, ProgressionCalculator.action(3, 5))
    }

    @Test
    fun threeOrMoreRepsShortDropsTwoPointFiveKg() {
        assertEquals(77.5, suggest(80.0, 2, 5), 0.001)
        assertEquals(ProgressionAction.DECREASE, ProgressionCalculator.action(2, 5))
    }

    @Test
    fun suggestionNeverGoesBelowZero() {
        assertEquals(0.0, suggest(2.0, 0, 5), 0.001)
    }

    @Test
    fun aHoldHoldsTheStoredWeightExactlyInEitherUnit() {
        // A pound user's stored weight is rarely on the kilogram grid. Re-deriving a hold
        // through the display grid would nudge it — a suggestion nobody asked for.
        val stored = WeightConverter.toKg(115.0, WeightUnit.LBS)
        val step = IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.LBS)
        assertEquals(stored, suggest(stored, 4, 5, step, unit = WeightUnit.LBS), 0.0)
    }

    @Test
    fun aPoundWalkStaysOnTheFivePoundGrid() {
        // The recorded drift: the step was added in kilograms (5 lbs = 2.2679618… kg) and
        // re-quantised to the tenth, which rounded up every time. The real step became
        // 2.3 kg = 5.07 lbs and the error compounded — 115.5 lbs by the third session,
        // 151 by the tenth. Twelve sessions is long enough for a single wrong tenth to show.
        val step = IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.LBS)
        var kgValue = WeightConverter.toKg(100.0, WeightUnit.LBS)
        for (session in 1..12) {
            kgValue = suggest(kgValue, 5, 5, step, unit = WeightUnit.LBS)
            assertEquals(
                "session $session",
                100.0 + 5.0 * session,
                WeightConverter.toDisplayValue(kgValue, WeightUnit.LBS),
                0.0,
            )
        }
    }

    @Test
    fun aKilogramWalkStaysOnTheTwoAndAHalfKilogramGrid() {
        var kgValue = 50.0
        for (session in 1..12) {
            kgValue = suggest(kgValue, 5, 5)
            assertEquals("session $session", 50.0 + 2.5 * session, kgValue, 0.0)
        }
    }

    @Test
    fun anAcceptedSuggestionStoresWhatTypingTheSameWeightStores() {
        // If these differ, one displayed weight splits into two stored values and a
        // reps-at-weight record can never accumulate.
        val step = IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.LBS)
        var kgValue = WeightConverter.toKg(100.0, WeightUnit.LBS)
        for (session in 1..12) {
            kgValue = suggest(kgValue, 5, 5, step, unit = WeightUnit.LBS)
            val typed = WeightConverter.toKg(100.0 + 5.0 * session, WeightUnit.LBS)
            assertEquals("session $session", typed, kgValue, 0.0)
        }
    }

    @Test
    fun aPoundDropAlsoLandsOnTheGrid() {
        val step = IncrementTable.displayStep(LoadType.EXTERNAL, WeightUnit.LBS)
        val from = WeightConverter.toKg(100.0, WeightUnit.LBS)
        val dropped = suggest(from, 2, 5, step, unit = WeightUnit.LBS)
        assertEquals(95.0, WeightConverter.toDisplayValue(dropped, WeightUnit.LBS), 0.0)
    }

    @Test
    fun assistanceFallsAsThePoundUserGetsStronger() {
        // Less help is the harder set, and it must land on the pound grid like everything else.
        val step = IncrementTable.displayStep(LoadType.ASSISTED, WeightUnit.LBS)
        val from = WeightConverter.toKg(40.0, WeightUnit.LBS)
        val next = suggest(from, 8, 8, step, WeightMeaning.ASSISTANCE, WeightUnit.LBS)
        assertEquals(35.0, WeightConverter.toDisplayValue(next, WeightUnit.LBS), 0.0)
    }

    @Test
    fun assistanceStopsAtZeroRatherThanGoingNegative() {
        val step = IncrementTable.displayStep(LoadType.ASSISTED, WeightUnit.LBS)
        assertEquals(
            0.0,
            suggest(0.0, 8, 8, step, WeightMeaning.ASSISTANCE, WeightUnit.LBS),
            0.0,
        )
    }

    @Test
    fun aLiftWithNoStepHoldsItsWeightAndStillReadsAsProgress() {
        // A push-up. There is nothing to add, so the suggestion must not invent a load — but
        // the action stays INCREASE, because the lifter DID hit target and the copy layer turns
        // that into "add a rep".
        assertEquals(0.0, suggest(0.0, 12, 12, displayStep = null), 0.001)
        assertEquals(ProgressionAction.INCREASE, ProgressionCalculator.action(12, 12))

        // And it does not go backwards on a missed target either.
        assertEquals(0.0, suggest(0.0, 5, 12, displayStep = null), 0.001)
    }

    @Test
    fun hintBundlesLastAndSuggestedLoad() {
        val hint = ProgressionCalculator.hint(
            exerciseId = "ex-squat",
            exerciseName = "Barbell Back Squat",
            lastWeightKg = 60.0,
            lastWorkingReps = 5,
            targetReps = 5,
            displayStep = kg,
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
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
