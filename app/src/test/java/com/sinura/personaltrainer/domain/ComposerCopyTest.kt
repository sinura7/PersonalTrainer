package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerCopyTest {
    @Test
    fun typeChipsReuseCardioCopyAndNeverDumpSchemaEnums() {
        CardioType.entries.forEach { type ->
            assertEquals(CardioCopy.name(type), ComposerCopy.typeChipLabel(type))
            assertNotEquals(type.name, ComposerCopy.typeChipLabel(type))
            assertFalse(type.name, ComposerCopy.typeChipLabel(type) == type.name.uppercase())
        }
        assertEquals("Run", ComposerCopy.typeChipLabel(CardioType.RUN))
        assertEquals("Ride", ComposerCopy.typeChipLabel(CardioType.RIDE))
        assertEquals("Walk", ComposerCopy.typeChipLabel(CardioType.WALK))
    }

    @Test
    fun untitledCardioTitleIsTheGymName() {
        assertEquals("Run", ComposerCopy.untitledCardioTitle(CardioType.RUN))
        assertNotEquals("RUN", ComposerCopy.untitledCardioTitle(CardioType.RUN))
    }

    @Test
    fun weightFieldFollowsTheChosenUnit() {
        assertEquals("Weight kg", ComposerCopy.weightFieldLabel(WeightUnit.KG))
        assertEquals("Weight lbs", ComposerCopy.weightFieldLabel(WeightUnit.LBS))
        assertEquals("5 reps · 80 kg", ComposerCopy.strengthLineSubtitle(5, 80.0, WeightUnit.KG))
        assertEquals("5 reps · 176.5 lbs", ComposerCopy.strengthLineSubtitle(5, 80.0, WeightUnit.LBS))
        assertEquals(80.0, readyStrength("80", "5", WeightUnit.KG).weightKg, 0.0)
        assertEquals(102.5, readyStrength("102,5", "5", WeightUnit.KG).weightKg, 0.0001)
        assertEquals(5.5, readyCardio("30", "5,5", DistanceUnit.KM).distanceKm!!, 0.0001)
        assertEquals(
            DistanceUnit.KM,
            DistanceUnit.fromWeight(WeightUnit.KG),
        )
        assertEquals(
            DistanceUnit.MI,
            DistanceUnit.fromWeight(WeightUnit.LBS),
        )
        assertEquals(8.04672, readyCardio("30", "5", DistanceUnit.MI).distanceKm!!, 0.00001)
        assertEquals(5.0, readyCardio("30", "5", DistanceUnit.KM).distanceKm!!, 0.0)
        assertEquals(
            WeightConverter.toKg(185.0, WeightUnit.LBS),
            readyStrength("185", "5", WeightUnit.LBS).weightKg,
            0.0,
        )
    }

    /**
     * UX06: Add set reads both boxes as typed and refuses, box by box, what cannot be stored
     * as written. Nothing is rewritten on the way: "8.5" reps is a refusal, not 85.
     */
    @Test
    fun addSetRefusesWhatItCannotStoreAsWritten() {
        val refused = ComposerCopy.strengthEntry("-50", "8.5", WeightUnit.KG) as StrengthEntry.RefusedSet
        assertEquals(NumericEntry.WEIGHT_NEGATIVE, refused.weightError)
        assertEquals(NumericEntry.REPS_WHOLE_RULE, refused.repsError)

        val repsOnly = ComposerCopy.strengthEntry("60", "", WeightUnit.KG) as StrengthEntry.RefusedSet
        assertEquals(null, repsOnly.weightError)
        assertEquals(NumericEntry.REPS_WHOLE_RULE, repsOnly.repsError)

        // No cap on a backdated set: 150 push-ups is a thing that happened.
        assertEquals(150, readyStrength("", "150", WeightUnit.KG).reps)
        assertEquals(NumericEntry.REPS_WHOLE_RULE, (ComposerCopy.strengthEntry("60", "0", WeightUnit.KG) as StrengthEntry.RefusedSet).repsError)

        val weightOnly = ComposerCopy.strengthEntry("1.2.3", "8", WeightUnit.KG) as StrengthEntry.RefusedSet
        assertEquals(NumericEntry.WEIGHT_RULE, weightOnly.weightError)
        assertEquals(null, weightOnly.repsError)

        // A blank weight is bodyweight, as the field's 0 default and every weight hint say.
        val bodyweight = readyStrength("", "12", WeightUnit.LBS)
        assertEquals(0.0, bodyweight.weightKg, 0.0)
        assertEquals(12, bodyweight.reps)
    }

    @Test
    fun addCardioRefusesFractionalMinutesAndUnreadableDistance() {
        val refused = ComposerCopy.cardioEntry("2.5", "5k", DistanceUnit.KM) as CardioEntry.RefusedCardio
        assertEquals(NumericEntry.MINUTES_RULE, refused.minutesError)
        assertEquals(NumericEntry.DISTANCE_RULE, refused.distanceError)

        val noMinutes = ComposerCopy.cardioEntry("", "", DistanceUnit.KM) as CardioEntry.RefusedCardio
        assertEquals(NumericEntry.MINUTES_RULE, noMinutes.minutesError)
        assertEquals(null, noMinutes.distanceError)

        val zeroMinutes = ComposerCopy.cardioEntry("0", "", DistanceUnit.KM) as CardioEntry.RefusedCardio
        assertEquals(NumericEntry.MINUTES_RULE, zeroMinutes.minutesError)

        val noDistance = readyCardio("30", "", DistanceUnit.MI)
        assertEquals(30, noDistance.minutes)
        assertEquals(null, noDistance.distanceKm)
    }

    private fun readyStrength(weight: String, reps: String, unit: WeightUnit): StrengthEntry.ReadySet =
        ComposerCopy.strengthEntry(weight, reps, unit) as StrengthEntry.ReadySet

    private fun readyCardio(minutes: String, distance: String, unit: DistanceUnit): CardioEntry.ReadyCardio =
        ComposerCopy.cardioEntry(minutes, distance, unit) as CardioEntry.ReadyCardio

    @Test
    fun saveIsTheOnlyFilledVolt() {
        assertEquals("Save", ComposerCopy.VOLT)
        assertEquals(ComposerCopy.SAVE, ComposerCopy.VOLT)
        assertEquals(listOf("Add set", "Add cardio", "Choose a lift"), ComposerCopy.SECONDARY_ACTS)
        assertFalse(ComposerCopy.SECONDARY_ACTS.contains(ComposerCopy.SAVE))
        assertEquals(1, listOf(ComposerCopy.VOLT).size)
        assertEquals("Remove", ComposerCopy.REMOVE)
        assertEquals("Cancel", ComposerCopy.CANCEL)
    }

    @Test
    fun cardioLineKeepsMinutesAndOptionalDistance() {
        assertEquals("30 min", ComposerCopy.cardioLineSubtitle(30, null))
        assertEquals("30 min · 5.0 km", ComposerCopy.cardioLineSubtitle(30, 5.0))
        assertEquals("30 min · Indoor", ComposerCopy.cardioLineSubtitle(30, null, indoor = true))
        assertEquals(
            "30 min · 5.0 mi",
            ComposerCopy.cardioLineSubtitle(30, 8.04672, distance = DistanceUnit.MI),
        )
    }

    @Test
    fun laterIsDisabledOnTodayAndDirtyTracksDraft() {
        assertTrue(ComposerCopy.canShiftLater(10L, 11L))
        assertFalse(ComposerCopy.canShiftLater(11L, 11L))
        assertFalse(
            ComposerCopy.isDirty(
                title = "",
                strengthCount = 0,
                cardioCount = 0,
                epochDay = 11L,
                todayEpochDay = 11L,
            ),
        )
        assertTrue(
            ComposerCopy.isDirty(
                title = "Run",
                strengthCount = 0,
                cardioCount = 0,
                epochDay = 11L,
                todayEpochDay = 11L,
            ),
        )
        assertTrue(
            ComposerCopy.isDirty(
                title = "",
                strengthCount = 0,
                cardioCount = 0,
                epochDay = 10L,
                todayEpochDay = 11L,
            ),
        )
    }
}
