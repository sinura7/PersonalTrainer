package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupRampTest {
    @Test
    fun aHundredKiloBarRampsFortySixtyEighty() {
        val sets = WarmupRamp.sets(
            workingWeightKg = 100.0,
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
        )
        assertEquals(listOf(40.0, 60.0, 80.0), sets.map { it.weightKg })
        assertEquals(listOf(40, 60, 80), sets.map { it.percent })
    }

    @Test
    fun aPinStackSnapsToFiveKilogramPins() {
        val sets = WarmupRamp.sets(
            workingWeightKg = 50.0,
            loadType = LoadType.STACK,
            unit = WeightUnit.KG,
        )
        assertEquals(listOf(20.0, 30.0, 40.0), sets.map { it.weightKg })
    }

    @Test
    fun aDumbbellRampsOnTwoKilogramJumps() {
        val sets = WarmupRamp.sets(
            workingWeightKg = 20.0,
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
            equipment = EquipmentType.DUMBBELL,
        )
        assertEquals(listOf(8.0, 12.0, 16.0), sets.map { it.weightKg })
    }

    @Test
    fun bodyweightAndAssistedHaveNoRamp() {
        assertTrue(
            WarmupRamp.sets(80.0, LoadType.BODYWEIGHT, WeightUnit.KG).isEmpty(),
        )
        assertTrue(
            WarmupRamp.sets(30.0, LoadType.ASSISTED, WeightUnit.KG).isEmpty(),
        )
    }

    @Test
    fun zeroWorkingWeightIsEmpty() {
        assertTrue(
            WarmupRamp.sets(0.0, LoadType.EXTERNAL, WeightUnit.KG).isEmpty(),
        )
    }

    @Test
    fun aRungAtOrAboveWorkingWeightIsDropped() {
        val sets = WarmupRamp.sets(
            workingWeightKg = 5.0,
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
        )
        assertTrue(sets.none { it.weightKg >= 5.0 })
        assertTrue(sets.all { it.weightKg > 0.0 })
    }

    @Test
    fun firstSetCarriesTheRampAndWorkingSetsDoNot() {
        val first = checkNotNull(
            SetMicroRecCalculator.suggest(
                SetMicroRecInputs(
                    editing = false,
                    loadType = LoadType.EXTERNAL,
                    unit = WeightUnit.KG,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = 100.0,
                    workingLogged = 0,
                    thisSessionWorking = emptyList(),
                    lastAnySetWasWarmup = false,
                    hint = ProgressionHint(
                        exerciseId = "ex-1",
                        exerciseName = "Squat",
                        lastWeightKg = 100.0,
                        lastReps = 5,
                        targetReps = 5,
                        suggestedWeightKg = 100.0,
                        action = ProgressionAction.INCREASE,
                        loadType = LoadType.EXTERNAL,
                    ),
                    lighterWeek = false,
                    draftWeightKg = 100.0,
                    draftReps = 5,
                    draftRpe = null,
                ),
            ),
        )
        assertEquals(SetMicroRecCalculator.FIRST_SET, first.reasonCode)
        assertEquals(listOf(40.0, 60.0, 80.0), first.warmupSets.map { it.weightKg })
        assertEquals("Warm up 40 · 60 · 80 kg", SetMicroRecCopy.warmupLine(first, WeightUnit.KG))

        val working = checkNotNull(
            SetMicroRecCalculator.suggest(
                SetMicroRecInputs(
                    editing = false,
                    loadType = LoadType.EXTERNAL,
                    unit = WeightUnit.KG,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = 100.0,
                    workingLogged = 1,
                    thisSessionWorking = listOf(
                        LoggedSetView(weightKg = 100.0, reps = 5, rpe = 8, isWarmup = false),
                    ),
                    lastAnySetWasWarmup = false,
                    hint = first.let {
                        ProgressionHint(
                            exerciseId = "ex-1",
                            exerciseName = "Squat",
                            lastWeightKg = 100.0,
                            lastReps = 5,
                            targetReps = 5,
                            suggestedWeightKg = 100.0,
                            action = ProgressionAction.INCREASE,
                            loadType = LoadType.EXTERNAL,
                        )
                    },
                    lighterWeek = false,
                    draftWeightKg = 100.0,
                    draftReps = 5,
                    draftRpe = null,
                ),
            ),
        )
        assertTrue(working.warmupSets.isEmpty())
        assertNull(SetMicroRecCopy.warmupLine(working, WeightUnit.KG))
    }
}
