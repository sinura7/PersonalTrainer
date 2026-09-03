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
        assertEquals(80.0, ComposerCopy.parseWeightToKg("80", WeightUnit.KG), 0.0)
        assertEquals(102.5, ComposerCopy.parseWeightToKg("102,5", WeightUnit.KG), 0.0001)
        assertEquals(5.5, ComposerCopy.parseDistanceKm("5,5")!!, 0.0001)
        assertEquals(
            WeightConverter.toKg(185.0, WeightUnit.LBS),
            ComposerCopy.parseWeightToKg("185", WeightUnit.LBS),
            0.0,
        )
    }

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
