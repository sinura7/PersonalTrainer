package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorEntryWheelsTest {
    @Test
    fun swipeSnapsWeightByThePlateStep() {
        assertEquals(2.5, FloorEntryWheels.weightStep(WeightUnit.KG), 0.0)
        assertEquals(5.0, FloorEntryWheels.weightStep(WeightUnit.LBS), 0.0)
        assertEquals(
            102.5,
            FloorEntryWheels.swipeWeightKg(100.0, WeightUnit.KG, 1),
            0.0001,
        )
        assertEquals(
            97.5,
            FloorEntryWheels.swipeWeightKg(100.0, WeightUnit.KG, -1),
            0.0001,
        )
        val at135Lb = WeightConverter.toKg(135.0, WeightUnit.LBS)
        val heavier = FloorEntryWheels.swipeWeightKg(at135Lb, WeightUnit.LBS, 1)
        assertEquals(140.0, WeightConverter.toDisplayValue(heavier, WeightUnit.LBS), 0.001)
        val lighter = FloorEntryWheels.swipeWeightKg(at135Lb, WeightUnit.LBS, -1)
        assertEquals(130.0, WeightConverter.toDisplayValue(lighter, WeightUnit.LBS), 0.001)
    }

    @Test
    fun swipeSnapsRepsByOne() {
        assertEquals(9, FloorEntryWheels.swipeReps(8, 1))
        assertEquals(7, FloorEntryWheels.swipeReps(8, -1))
        assertEquals(1, FloorEntryWheels.swipeReps(1, -1))
        assertEquals(NumericEntry.MAX_REPS, FloorEntryWheels.swipeReps(NumericEntry.MAX_REPS, 1))
        assertEquals(8, FloorEntryWheels.repsAt(FloorEntryWheels.repsPage(8)))
    }

    @Test
    fun holdSwipeIsADurationWheelNotAFakeRep() {
        assertEquals(HoldWork.STEP_SECONDS, 5)
        assertEquals(35, FloorEntryWheels.swipeHoldSeconds(30, 1))
        assertEquals(25, FloorEntryWheels.swipeHoldSeconds(30, -1))
        assertEquals(HoldWork.MIN_SECONDS, FloorEntryWheels.swipeHoldSeconds(HoldWork.MIN_SECONDS, -1))
        val values = FloorEntryWheels.holdSecondsValues(30)
        assertTrue(values.contains(30))
        assertFalse(values.contains(1))
        assertTrue(values.all { it == 30 || it % HoldWork.STEP_SECONDS == 0 })
        assertEquals(30, FloorEntryWheels.holdSecondsAt(FloorEntryWheels.holdPage(30), 30))
    }

    @Test
    fun restSwipeStepsByFifteenLikeTheFloorAdjust() {
        assertEquals(FloorEntryWheels.REST_STEP_SECONDS, 15)
        assertEquals(105, FloorEntryWheels.swipeRestSeconds(90, 1))
        assertEquals(75, FloorEntryWheels.swipeRestSeconds(90, -1))
        assertEquals(
            RestTimerPreferences.MIN_SECONDS,
            FloorEntryWheels.swipeRestSeconds(RestTimerPreferences.MIN_SECONDS, -1),
        )
        val values = FloorEntryWheels.restSecondsValues(90)
        assertTrue(values.contains(90))
        assertTrue(values.contains(RestTimerPreferences.MIN_SECONDS))
        assertEquals(90, FloorEntryWheels.restSecondsAt(FloorEntryWheels.restPage(90), 90))
    }

    @Test
    fun offStepWeightLandsOnTheNextPlateAfterAFlick() {
        assertEquals(
            102.5,
            FloorEntryWheels.swipeWeightKg(101.0, WeightUnit.KG, 1),
            0.0001,
        )
        assertEquals(
            100.0,
            FloorEntryWheels.swipeWeightKg(101.0, WeightUnit.KG, -1),
            0.0001,
        )
    }

    @Test
    fun firstSettleOnTheParkedPageIsNotAChoice() {
        val fresh = FloorEntryWheels.WheelSettleMemory()
        assertFalse(
            FloorEntryWheels.shouldCommitSettledPage(
                settledPage = 40,
                initialPage = 40,
                memory = fresh,
                selectedIndex = 40,
            ),
        )
        assertFalse(
            FloorEntryWheels.shouldCommitSettledPage(
                settledPage = 41,
                initialPage = 40,
                memory = fresh,
                selectedIndex = 40,
            ),
        )
    }

    @Test
    fun returnToTheParkedPageAfterLeavingIsAChoice() {
        var memory = FloorEntryWheels.WheelSettleMemory()
        assertFalse(
            FloorEntryWheels.shouldCommitSettledPage(40, 40, memory, selectedIndex = 40),
        )
        memory = FloorEntryWheels.afterWheelSettle(40, 40, memory)
        assertTrue(
            FloorEntryWheels.shouldCommitSettledPage(41, 40, memory, selectedIndex = 40),
        )
        memory = FloorEntryWheels.afterWheelSettle(41, 40, memory)
        assertTrue(memory.hasLeftInitialPage)
        assertTrue(
            FloorEntryWheels.shouldCommitSettledPage(40, 40, memory, selectedIndex = 41),
        )
    }

    @Test
    fun displayedWheelNumbersAreWhatALogWouldWrite() {
        val kg = FloorEntryWheels.swipeWeightKg(100.0, WeightUnit.KG, 1)
        val reps = FloorEntryWheels.swipeReps(5, 1)
        assertEquals(102.5, kg, 0.0001)
        assertEquals(6, reps)
        assertEquals(
            kg,
            FloorEntryWheels.weightKgAt(FloorEntryWheels.weightPage(kg, WeightUnit.KG), WeightUnit.KG),
            0.0001,
        )
        assertEquals(reps, FloorEntryWheels.repsAt(FloorEntryWheels.repsPage(reps)))
    }

    @Test
    fun emptyAndCeilingStayOnTheWheel() {
        assertEquals(0.0, FloorEntryWheels.swipeWeightKg(0.0, WeightUnit.KG, -1), 0.0)
        val top = FloorEntryWheels.weightKgAt(
            FloorEntryWheels.weightDisplays(WeightUnit.KG).lastIndex,
            WeightUnit.KG,
        )
        assertEquals(top, FloorEntryWheels.swipeWeightKg(top, WeightUnit.KG, 1), 0.0001)
        assertEquals(400.0, WeightConverter.toDisplayValue(top, WeightUnit.KG), 0.001)
    }
}
