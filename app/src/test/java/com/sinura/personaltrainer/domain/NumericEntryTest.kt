package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericEntryTest {
    @Test
    fun readsAPlainWeight() {
        assertEquals(100.0, NumericEntry.parseWeightKg("100", WeightUnit.KG)!!, 0.0001)
        assertEquals(102.5, NumericEntry.parseWeightKg("102.5", WeightUnit.KG)!!, 0.0001)
    }

    @Test
    fun commaDecimalWorksOnEveryTypedPath() {
        assertEquals(102.5, NumericEntry.parseDecimal("102,5")!!, 0.0001)
        assertEquals(102.5, NumericEntry.parseWeightKg("102,5", WeightUnit.KG)!!, 0.0001)
        assertEquals(
            102.5,
            WeightConverter.parseDisplayToKg("102,5", WeightUnit.KG, originalKg = null)!!,
            0.0001,
        )
        assertEquals(102.5, ComposerCopy.parseWeightToKg("102,5", WeightUnit.KG), 0.0001)
        assertEquals(5.5, ComposerCopy.parseDistanceKm("5,5")!!, 0.0001)
        assertEquals("102,5", NumericEntry.filterDecimal("102,5kg"))
        assertEquals("80.55", NumericEntry.filterDecimal("80.5.5"))
        assertEquals(".5", NumericEntry.filterDecimal(".5"))
        assertEquals(",5", NumericEntry.filterDecimal(",5"))
    }

    @Test
    fun nextThenDoneIsTheNumericChain() {
        assertEquals(
            listOf(
                NumericEntry.Ime.NEXT,
                NumericEntry.Ime.NEXT,
                NumericEntry.Ime.NEXT,
                NumericEntry.Ime.DONE,
            ),
            NumericEntry.ROUTINE_EDITOR_CHAIN,
        )
        assertEquals(
            listOf(NumericEntry.Ime.NEXT, NumericEntry.Ime.DONE),
            NumericEntry.COMPOSER_STRENGTH_CHAIN,
        )
        assertEquals(
            listOf(NumericEntry.Ime.NEXT, NumericEntry.Ime.DONE),
            NumericEntry.COMPOSER_CARDIO_CHAIN,
        )
        assertEquals(
            listOf(NumericEntry.Ime.NEXT, NumericEntry.Ime.DONE),
            NumericEntry.PASSWORD_CHAIN,
        )
        assertEquals(NumericEntry.Ime.DONE, NumericEntry.CUSTOM_REST)
        assertEquals(NumericEntry.Ime.DONE, NumericEntry.LIVE_CARDIO_DISTANCE)
        assertTrue(readUi("routines/SessionLiftStrip.kt").contains("ROUTINE_EDITOR_CHAIN"))
        assertTrue(readUi("activity/ActivityComposerScreen.kt").contains("COMPOSER_STRENGTH_CHAIN"))
        assertTrue(readUi("activity/ActivityComposerScreen.kt").contains("COMPOSER_CARDIO_CHAIN"))
        assertTrue(readUi("activity/LiveCardioScreen.kt").contains("LIVE_CARDIO_DISTANCE"))
        assertTrue(readUi("components/Common.kt").contains("CUSTOM_REST"))
        assertTrue(readUi("settings/SettingsScreen.kt").contains("PASSWORD_CHAIN"))
        assertTrue(readUi("components/Common.kt").contains("KeyboardType.Number"))
    }

    private fun readUi(relative: String): String {
        val roots = listOf(
            java.io.File("app/src/main/java/com/sinura/personaltrainer/ui"),
            java.io.File("../app/src/main/java/com/sinura/personaltrainer/ui"),
        )
        val file = roots.map { java.io.File(it, relative) }.first { it.isFile }
        return file.readText()
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
