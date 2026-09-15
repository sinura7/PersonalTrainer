package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorStepperTest {
    @Test
    fun barbellStackAndDumbbellStepsMatchTheIncrementTable() {
        assertEquals(
            102.5,
            FloorStepper.nextWeightKg(100.0, WeightUnit.KG, 1, LoadType.EXTERNAL),
            0.0001,
        )
        assertEquals(
            97.5,
            FloorStepper.nextWeightKg(100.0, WeightUnit.KG, -1, LoadType.EXTERNAL),
            0.0001,
        )
        val at135 = WeightConverter.toKg(135.0, WeightUnit.LBS)
        val heavierLb = FloorStepper.nextWeightKg(at135, WeightUnit.LBS, 1, LoadType.EXTERNAL)
        assertEquals(140.0, WeightConverter.toDisplayValue(heavierLb, WeightUnit.LBS), 0.001)

        assertEquals(
            105.0,
            FloorStepper.nextWeightKg(100.0, WeightUnit.KG, 1, LoadType.STACK),
            0.0001,
        )
        val stackLb = WeightConverter.toKg(100.0, WeightUnit.LBS)
        val heavierStack = FloorStepper.nextWeightKg(stackLb, WeightUnit.LBS, 1, LoadType.STACK)
        assertEquals(110.0, WeightConverter.toDisplayValue(heavierStack, WeightUnit.LBS), 0.001)

        assertEquals(
            22.0,
            FloorStepper.nextWeightKg(
                20.0,
                WeightUnit.KG,
                1,
                LoadType.EXTERNAL,
                EquipmentType.DUMBBELL,
            ),
            0.0001,
        )
        val dbLb = WeightConverter.toKg(20.0, WeightUnit.LBS)
        val heavierDb = FloorStepper.nextWeightKg(
            dbLb,
            WeightUnit.LBS,
            1,
            LoadType.EXTERNAL,
            EquipmentType.DUMBBELL,
        )
        assertEquals(25.0, WeightConverter.toDisplayValue(heavierDb, WeightUnit.LBS), 0.001)
    }

    @Test
    fun bodyweightHasNoWeightStep() {
        assertEquals(
            0.0,
            FloorStepper.nextWeightKg(0.0, WeightUnit.KG, 1, LoadType.BODYWEIGHT),
            0.0001,
        )
        assertEquals(WeightMeaning.NONE, LoadClass.BODYWEIGHT.weightMeaning)
        assertEquals("Assistance", WeightMeaning.ASSISTANCE.fieldLabel)
        assertEquals("Added", WeightMeaning.ADDED.fieldLabel)
        assertEquals("Weight", WeightMeaning.LIFTED.fieldLabel)
    }

    @Test
    fun repsStepByOneAndStopAtTheKeypadRange() {
        assertEquals(6, FloorStepper.nextReps(5, 1))
        assertEquals(4, FloorStepper.nextReps(5, -1))
        assertEquals(1, FloorStepper.nextReps(1, -1))
        assertEquals(NumericEntry.MAX_REPS, FloorStepper.nextReps(NumericEntry.MAX_REPS, 1))
        assertEquals(1, FloorStepper.REPS_STEP)
    }

    @Test
    fun holdDraftStepsByFiveSeconds() {
        assertEquals(HoldWork.STEP_SECONDS, 5)
        assertEquals(35, FloorStepper.nextHoldSeconds(30, 1))
        assertEquals(25, FloorStepper.nextHoldSeconds(30, -1))
        assertEquals(HoldWork.MIN_SECONDS, FloorStepper.nextHoldSeconds(HoldWork.MIN_SECONDS, -1))
        assertEquals(HoldWork.MAX_SECONDS, FloorStepper.nextHoldSeconds(HoldWork.MAX_SECONDS, 1))
    }

    @Test
    fun holdRepeatIsCappedAtFivePerSecondAfterFourHundredFiftyMs() {
        assertEquals(450L, StepperRepeat.HOLD_BEFORE_REPEAT_MS)
        assertEquals(200L, StepperRepeat.REPEAT_MS)
        assertEquals(5, StepperRepeat.MAX_CHANGES_PER_SECOND)
        assertTrue(StepperRepeat.REPEAT_MS * StepperRepeat.MAX_CHANGES_PER_SECOND >= 1_000L)
    }

    @Test
    fun planLastAndSuggestedAreLabelsNotAnArbitraryChipRow() {
        assertEquals(
            WeightDraftSource.SUGGESTED,
            FloorWeightPresets.source(
                currentKg = 102.5,
                plannedKg = 100.0,
                lastKg = 97.5,
                suggestedKg = 102.5,
            ),
        )
        assertEquals(
            WeightDraftSource.PLAN,
            FloorWeightPresets.source(
                currentKg = 100.0,
                plannedKg = 100.0,
                lastKg = 97.5,
                suggestedKg = 102.5,
            ),
        )
        assertEquals(
            WeightDraftSource.LAST_TIME,
            FloorWeightPresets.source(
                currentKg = 97.5,
                plannedKg = 100.0,
                lastKg = 97.5,
                suggestedKg = 102.5,
            ),
        )
        assertNull(
            FloorWeightPresets.source(
                currentKg = 87.5,
                plannedKg = 100.0,
                lastKg = 97.5,
                suggestedKg = 102.5,
            ),
        )
        val chips = FloorWeightPresets.contextActions(
            plannedKg = 100.0,
            lastKg = 97.5,
        )
        assertEquals(
            listOf("Plan 100", "Last 97.5"),
            chips.map { it.chipLabel(WeightUnit.KG) },
        )
        assertTrue(chips.none { it.source == WeightDraftSource.SUGGESTED })
        val unique = FloorWeightPresets.contextActions(
            plannedKg = 100.0,
            lastKg = 100.0,
        )
        assertEquals(1, unique.size)
        assertEquals(WeightDraftSource.PLAN, unique.single().source)
    }
}
