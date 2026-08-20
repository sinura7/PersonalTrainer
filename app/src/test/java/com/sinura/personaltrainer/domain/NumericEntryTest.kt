package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumericEntryTest {
    @Test
    fun readsAPlainWeight() {
        assertEquals(100.0, NumericEntry.parseWeightKg("100", WeightUnit.KG)!!, 0.0001)
        assertEquals(102.5, NumericEntry.parseWeightKg("102.5", WeightUnit.KG)!!, 0.0001)
    }

    @Test
    fun acceptsACommaDecimalSeparator() {
        // On a phone set to most of Europe the numeric keypad's decimal key emits a comma;
        // rejecting it would make typed entry unusable for half the world.
        assertEquals(102.5, NumericEntry.parseWeightKg("102,5", WeightUnit.KG)!!, 0.0001)
    }

    @Test
    fun convertsFromTheDisplayedUnit() {
        val kg = NumericEntry.parseWeightKg("225", WeightUnit.LBS)!!
        assertEquals(225.0, WeightConverter.kgToLbs(kg), 0.5)
    }

    @Test
    fun zeroIsAllowedBecauseWarmUpsAreLoggedAtZero() {
        assertEquals(0.0, NumericEntry.parseWeightKg("0", WeightUnit.KG)!!, 0.0001)
    }

    @Test
    fun refusesWhatCannotBeAWeight() {
        assertNull(NumericEntry.parseWeightKg("", WeightUnit.KG))
        assertNull(NumericEntry.parseWeightKg("   ", WeightUnit.KG))
        assertNull(NumericEntry.parseWeightKg("abc", WeightUnit.KG))
        assertNull(NumericEntry.parseWeightKg("-5", WeightUnit.KG))
        assertNull(NumericEntry.parseWeightKg("1.2.3", WeightUnit.KG))
        assertNull(NumericEntry.parseWeightKg("10kg", WeightUnit.KG))
    }

    @Test
    fun refusesAnAmbiguousGroupedNumber() {
        // "1,000" is one thousand to an English speaker and one to everyone else. Guessing
        // either way silently logs the wrong lift, so it is refused. Same for "1.000".
        assertNull(NumericEntry.parseWeightKg("1,000", WeightUnit.KG))
        assertNull(NumericEntry.parseWeightKg("1.000", WeightUnit.KG))
    }

    @Test
    fun twoDecimalPlacesStillWork() {
        assertEquals(102.25, NumericEntry.parseWeightKg("102.25", WeightUnit.KG)!!, 0.05)
        assertEquals(0.5, NumericEntry.parseWeightKg("0,5", WeightUnit.KG)!!, 0.0001)
    }

    @Test
    fun readsReps() {
        assertEquals(5, NumericEntry.parseReps("5"))
        assertEquals(12, NumericEntry.parseReps(" 12 "))
    }

    @Test
    fun refusesRepsThatAreNotAWholeSet() {
        assertNull(NumericEntry.parseReps("0"))
        assertNull(NumericEntry.parseReps("-3"))
        assertNull(NumericEntry.parseReps("5.5"))
        assertNull(NumericEntry.parseReps(""))
        assertNull(NumericEntry.parseReps("x"))
    }

    @Test
    fun refusesAFumbledRepCount() {
        // A guard against a mis-tap, not a judgement: three digits is far more often a slip
        // than a real set.
        assertEquals(NumericEntry.MAX_REPS, NumericEntry.parseReps(NumericEntry.MAX_REPS.toString()))
        assertNull(NumericEntry.parseReps((NumericEntry.MAX_REPS + 1).toString()))
    }
}
