package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bodyweight lifts are their own class and reps are their measure.
 *
 * The rule these pin is that the app never prices a body. It used to: every bodyweight rep was
 * worth a flat 40 kg, so ten push-ups and ten pull-ups were each 400 kg and each worth more
 * than a set of curls. Nothing here converts between the two numbers, because there is no rate
 * to convert at.
 */
class SetWorkTest {
    @Test
    fun aLoadedSetIsWeightTimesReps() {
        assertEquals(
            SetWork(volumeKg = 500.0, bodyweightReps = 0),
            SetWork.of(100.0, 5, LoadClass.LOADED),
        )
    }

    @Test
    fun aBodyweightSetIsRepsAndNoKilograms() {
        assertEquals(
            SetWork(volumeKg = 0.0, bodyweightReps = 12),
            SetWork.of(0.0, 12, LoadClass.BODYWEIGHT),
        )
    }

    @Test
    fun aVestIsCountedAndSoAreTheReps() {
        // Twenty kilos genuinely moved, eight times. Both halves are measurements.
        assertEquals(
            SetWork(volumeKg = 160.0, bodyweightReps = 8),
            SetWork.of(20.0, 8, LoadClass.BODYWEIGHT_ADDED),
        )
    }

    @Test
    fun aVestLeftOffIsJustBodyweight() {
        assertEquals(
            SetWork(volumeKg = 0.0, bodyweightReps = 8),
            SetWork.of(0.0, 8, LoadClass.BODYWEIGHT_ADDED),
        )
    }

    @Test
    fun assistanceEarnsNoTonnageAndLosesNoReps() {
        // Counting the assistance as volume would pay the lifter for the machine's help. The
        // reps still happened, and an assisted pull-up is the way most people get to a real one.
        assertEquals(
            SetWork(volumeKg = 0.0, bodyweightReps = 8),
            SetWork.of(20.0, 8, LoadClass.BODYWEIGHT_ASSISTED),
        )
    }

    @Test
    fun nonsenseIsFlooredRatherThanPropagated() {
        assertEquals(SetWork.NONE, SetWork.of(Double.NaN, 0, LoadClass.LOADED))
        assertEquals(SetWork.NONE, SetWork.of(-50.0, 0, LoadClass.LOADED))
        // A negative rep count cannot subtract from a total.
        assertEquals(SetWork.NONE, SetWork.of(0.0, -4, LoadClass.BODYWEIGHT))
    }

    @Test
    fun workAddsUpAndStaysInItsOwnUnits() {
        val session = SetWork.sum(
            listOf(
                SetWork.of(100.0, 5, LoadClass.LOADED),
                SetWork.of(100.0, 5, LoadClass.LOADED),
                SetWork.of(0.0, 12, LoadClass.BODYWEIGHT),
                SetWork.of(10.0, 6, LoadClass.BODYWEIGHT_ADDED),
            ),
        )
        assertEquals(SetWork(volumeKg = 1060.0, bodyweightReps = 18), session)
    }

    @Test
    fun emptyIsEmpty() {
        assertTrue(SetWork.NONE.isEmpty)
        assertTrue(SetWork.sum(emptyList()).isEmpty)
        assertFalse(SetWork.of(0.0, 1, LoadClass.BODYWEIGHT).isEmpty)
        assertFalse(SetWork.of(1.0, 1, LoadClass.LOADED).isEmpty)
    }

    @Test
    fun everyLoadTypeHasAClassAndUnknownOnesAreLoaded() {
        assertEquals(LoadClass.LOADED, LoadClass.of(LoadType.EXTERNAL))
        assertEquals(LoadClass.LOADED, LoadClass.of(LoadType.STACK))
        assertEquals(LoadClass.BODYWEIGHT, LoadClass.of(LoadType.BODYWEIGHT))
        assertEquals(LoadClass.BODYWEIGHT_ADDED, LoadClass.of(LoadType.BODYWEIGHT_PLUS))
        assertEquals(LoadClass.BODYWEIGHT_ASSISTED, LoadClass.of(LoadType.ASSISTED))
        // The safer wrong answer: an unclassifiable lift keeps its kilograms.
        assertEquals(LoadClass.LOADED, LoadClass.of(null))
    }

    @Test
    fun onlyLoadedLiftsAreMeasuredInKilograms() {
        assertFalse(LoadClass.LOADED.repsAreTheMeasure)
        assertTrue(LoadClass.BODYWEIGHT.repsAreTheMeasure)
        assertTrue(LoadClass.BODYWEIGHT_ADDED.repsAreTheMeasure)
        assertTrue(LoadClass.BODYWEIGHT_ASSISTED.repsAreTheMeasure)
    }

    @Test
    fun theWeightFieldMeansSomethingDifferentInEachClass() {
        // One column, three meanings. Every surface that shows or asks for it has to say which.
        assertEquals(WeightMeaning.LIFTED, LoadClass.LOADED.weightMeaning)
        assertEquals(WeightMeaning.NONE, LoadClass.BODYWEIGHT.weightMeaning)
        assertEquals(WeightMeaning.ADDED, LoadClass.BODYWEIGHT_ADDED.weightMeaning)
        assertEquals(WeightMeaning.ASSISTANCE, LoadClass.BODYWEIGHT_ASSISTED.weightMeaning)
    }
}
