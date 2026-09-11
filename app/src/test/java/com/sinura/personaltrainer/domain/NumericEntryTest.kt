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
        assertEquals(
            102.5,
            (ComposerCopy.strengthEntry("102,5", "5", WeightUnit.KG) as StrengthEntry.ReadySet).weightKg,
            0.0001,
        )
        assertEquals(5.5, NumericEntry.typedDistanceKm("5,5", DistanceUnit.KM).valueOrNull!!, 0.0001)
        assertEquals(
            NumericEntry.typedWeightKg("62,5", WeightUnit.KG),
            NumericEntry.typedWeightKg("62.5", WeightUnit.KG),
        )
    }

    // UX06-AC01: the number saved is the number typed, or the field is refused. Never 85 for
    // "8.5", never 50 for "-50".
    @Test
    fun aDecimalRepCountIsRefusedNotReadAsEightyFive() {
        val typed = NumericEntry.typedReps("8.5")
        assertTrue(typed is NumericEntry.Typed.Invalid)
        assertEquals(NumericEntry.REPS_RULE, typed.messageOrNull)
        assertNull(typed.valueOrNull)
        assertTrue(NumericEntry.typedReps("8,5") is NumericEntry.Typed.Invalid)
        assertTrue(NumericEntry.typedReps("-8") is NumericEntry.Typed.Invalid)
        assertTrue(NumericEntry.typedReps("1 2") is NumericEntry.Typed.Invalid)
        assertEquals(8, NumericEntry.typedReps(" 8 ").valueOrNull)
    }

    @Test
    fun aNegativeWeightIsRefusedNotReadAsPositive() {
        val typed = NumericEntry.typedWeightKg("-50", WeightUnit.KG)
        assertTrue(typed is NumericEntry.Typed.Invalid)
        assertEquals(NumericEntry.WEIGHT_NEGATIVE, typed.messageOrNull)
        assertNull(typed.valueOrNull)
        assertEquals(50.0, NumericEntry.typedWeightKg("50", WeightUnit.KG).valueOrNull!!, 0.0001)
    }

    // UX06-AC02: text with two separators or an exponent never becomes some other number.
    @Test
    fun ambiguousTextNeverBecomesADifferentAcceptedValue() {
        listOf("1.2.3", "8e2", "80.5.5", "1,000", "1.000", "10kg", "abc", "1..5").forEach { raw ->
            assertTrue(raw, NumericEntry.typedWeightKg(raw, WeightUnit.KG) is NumericEntry.Typed.Invalid)
            assertTrue(raw, NumericEntry.typedDistanceKm(raw, DistanceUnit.KM) is NumericEntry.Typed.Invalid)
            assertTrue(raw, NumericEntry.typedReps(raw) is NumericEntry.Typed.Invalid)
            assertTrue(raw, NumericEntry.typedWhole(input = raw, min = 1, rule = NumericEntry.MINUTES_RULE) is NumericEntry.Typed.Invalid)
        }
    }

    @Test
    fun blankIsItsOwnAnswerAndAZeroDistanceIsNoDistance() {
        assertEquals(NumericEntry.Typed.Blank, NumericEntry.typedWeightKg("", WeightUnit.KG))
        assertEquals(NumericEntry.Typed.Blank, NumericEntry.typedReps("   "))
        assertEquals(NumericEntry.Typed.Blank, NumericEntry.typedDistanceKm("", DistanceUnit.KM))
        assertEquals(NumericEntry.Typed.Blank, NumericEntry.typedDistanceKm("0", DistanceUnit.KM))
        assertEquals(NumericEntry.Typed.Blank, NumericEntry.typedDistanceKm("0,0", DistanceUnit.MI))
        assertTrue(NumericEntry.typedDistanceKm("-5", DistanceUnit.KM) is NumericEntry.Typed.Invalid)
        assertEquals(
            8.04672,
            NumericEntry.typedDistanceKm("5", DistanceUnit.MI).valueOrNull!!,
            0.00001,
        )
    }

    @Test
    fun wholeNumberFieldsCarryTheirOwnRule() {
        assertEquals(NumericEntry.SETS_RULE, NumericEntry.typedWhole(input = "0", min = 1, rule = NumericEntry.SETS_RULE).messageOrNull)
        assertEquals(NumericEntry.REST_RULE, NumericEntry.typedWhole(input = "1:30", min = 0, rule = NumericEntry.REST_RULE).messageOrNull)
        assertEquals(0, NumericEntry.typedWhole(input = "0", min = 0, rule = NumericEntry.REST_RULE).valueOrNull)
        assertEquals(30, NumericEntry.typedWhole(input = "30", min = 1, rule = NumericEntry.MINUTES_RULE).valueOrNull)
        assertEquals(NumericEntry.MINUTES_RULE, NumericEntry.typedWhole(input = "2.5", min = 1, rule = NumericEntry.MINUTES_RULE).messageOrNull)
    }

    // UX06-AC03: a stored kilogram value shown in the display unit and typed straight back
    // lands within that unit's display precision of where it started, for every half-kilo up
    // to 300. The precision is the one WeightConverter.toDisplayValue/formatDisplayNumber
    // actually apply: kilograms to a tenth (±0.05 kg), pounds to a half (±0.25 lb).
    @Test
    fun storedWeightsSurviveADisplayRoundTripWithinDisplayPrecision() {
        var kg = 0.0
        while (kg <= 300.0) {
            WeightUnit.entries.forEach { unit ->
                val toleranceKg = when (unit) {
                    WeightUnit.KG -> 0.05
                    WeightUnit.LBS -> WeightConverter.lbsToKg(0.25)
                }
                val shown = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(kg, unit))
                val back = NumericEntry.typedWeightKg(shown, unit).valueOrNull
                    ?: error("$shown $unit did not read back")
                assertEquals("$kg kg via $unit as $shown", kg, back, toleranceKg)
                // And a second pass changes nothing: the displayed text is a fixed point.
                val shownAgain = WeightConverter.formatDisplayNumber(WeightConverter.toDisplayValue(back, unit))
                assertEquals("$kg kg via $unit re-shown", shown, shownAgain)
            }
            kg += 0.5
        }
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
        assertTrue(readUi("settings/BackupDialogs.kt").contains("PASSWORD_CHAIN"))
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
