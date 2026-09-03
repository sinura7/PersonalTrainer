package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightConverterTest {
    @Test
    fun convertsKilogramsToPoundsWithSpecifiedFactor() {
        assertEquals(176.3696, WeightConverter.kgToLbs(80.0), 0.00001)
        assertEquals(80.0, WeightConverter.lbsToKg(176.3696), 0.00001)
    }

    @Test
    fun formatsKilogramsToOneDecimalWithUnit() {
        assertEquals("80 kg", 80.0.toWeightLabel(WeightUnit.KG))
        assertEquals("82.5 kg", 82.5.toWeightLabel(WeightUnit.KG))
        assertEquals("80 kg", 80.0.toKgLabel())
        assertEquals("82.5", 82.5.toKgNumber())
    }

    @Test
    fun formatsPoundsToHalfIncrementsWithUnit() {
        assertEquals("176.5 lbs", 80.0.toWeightLabel(WeightUnit.LBS))
        assertEquals("182 lbs", 82.5.toWeightLabel(WeightUnit.LBS))
        assertEquals("0 lbs", 0.0.toWeightLabel(WeightUnit.LBS))
    }

    @Test
    fun convertsDisplayPoundsBackToKilograms() {
        assertEquals(81.6, WeightConverter.toKg(180.0, WeightUnit.LBS), 0.001)
        assertEquals(82.5, WeightConverter.toKg(82.5, WeightUnit.KG), 0.001)
    }

    @Test
    fun incrementsInThePreferredDisplayUnitThenStoresKg() {
        val fromKg = WeightConverter.incrementKg(80.0, WeightUnit.KG, 1)
        assertEquals(82.5, fromKg, 0.001)

        val fromLbs = WeightConverter.incrementKg(80.0, WeightUnit.LBS, 1)
        assertEquals(WeightConverter.toKg(181.5, WeightUnit.LBS), fromLbs, 0.001)
        assertEquals("181.5 lbs", fromLbs.toWeightLabel(WeightUnit.LBS))
    }

    @Test
    fun parseKeepsOriginalKgWhenDisplayValueIsUnchanged() {
        val originalKg = 80.0
        val displayed = WeightConverter.formatDisplayNumber(
            WeightConverter.toDisplayValue(originalKg, WeightUnit.LBS),
        )
        val parsed = WeightConverter.parseDisplayToKg(displayed, WeightUnit.LBS, originalKg)
        assertEquals(originalKg, parsed)
    }

    @Test
    fun parseConvertsEditedDisplayValueToKg() {
        val parsed = WeightConverter.parseDisplayToKg("180", WeightUnit.LBS, 80.0)
        assertEquals(81.6, parsed!!, 0.001)
        assertNull(WeightConverter.parseDisplayToKg("", WeightUnit.LBS, 80.0))
        assertEquals(
            102.5,
            WeightConverter.parseDisplayToKg("102,5", WeightUnit.KG, originalKg = null)!!,
            0.0001,
        )
    }

    @Test
    fun defaultUnitIsPounds() {
        assertEquals(WeightUnit.LBS, WeightUnit.fromStorage(null))
        assertEquals(WeightUnit.LBS, WeightUnit.fromStorage("nope"))
        assertEquals(WeightUnit.KG, WeightUnit.fromStorage("kg"))
        assertEquals(WeightUnit.LBS, WeightUnit.fromStorage("lbs"))
    }

    @Test
    fun rejectsNegativeAndNonFiniteWeights() {
        assertEquals(0.0, WeightConverter.sanitizeKg(Double.NaN), 0.0)
        assertEquals(0.0, WeightConverter.sanitizeKg(Double.NEGATIVE_INFINITY), 0.0)
        assertEquals(0.0, WeightConverter.sanitizeKg(-12.5), 0.0)
        assertEquals("0 kg", Double.NaN.toWeightLabel(WeightUnit.KG))
        assertEquals(0.0, WeightConverter.toKg(-10.0, WeightUnit.LBS), 0.001)
    }
}
