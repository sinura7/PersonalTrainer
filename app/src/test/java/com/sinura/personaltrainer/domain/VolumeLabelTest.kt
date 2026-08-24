package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Volume-scale numbers are grouped and whole; weight-entry numbers must NOT be, because
 * [WeightConverter.parseDisplayToKg] round-trips against [WeightConverter.formatDisplayNumber].
 *
 * FND-005: every aggregate label is this formatter. Ties-to-even, never `roundToInt`.
 */
class VolumeLabelTest {
    private val us = Locale.US

    @Test
    fun volumeIsGroupedAndWhole() {
        assertEquals("12,450 kg", WeightConverter.formatVolumeLabel(12_450.0, WeightUnit.KG, us))
        assertEquals("500 kg", WeightConverter.formatVolumeLabel(500.0, WeightUnit.KG, us))
        assertEquals("1,000 kg", WeightConverter.formatVolumeLabel(999.6, WeightUnit.KG, us))
        assertEquals("0 kg", WeightConverter.formatVolumeLabel(0.0, WeightUnit.KG, us))
    }

    @Test
    fun volumeConvertsToTheDisplayUnitBeforeGrouping() {
        // 5000 kg -> 11023.1... lbs -> rounded and grouped
        assertEquals("11,023 lbs", WeightConverter.formatVolumeLabel(5_000.0, WeightUnit.LBS, us))
    }

    @Test
    fun volumeSurvivesNonFiniteInput() {
        assertEquals("0 kg", WeightConverter.formatVolumeLabel(Double.NaN, WeightUnit.KG, us))
        assertEquals("0 kg", WeightConverter.formatVolumeLabel(Double.POSITIVE_INFINITY, WeightUnit.KG, us))
        assertEquals(0L, WeightConverter.volumeDisplayWhole(Double.NaN, WeightUnit.KG))
        assertEquals(0, WeightConverter.volumeAnimationTarget(Double.NEGATIVE_INFINITY, WeightUnit.LBS))
    }

    @Test
    fun weightEntryFormattingStaysUngrouped() {
        // A separator here would break parseDisplayToKg's unchanged-display check.
        assertEquals("1250", WeightConverter.formatDisplayNumber(1_250.0))
        assertEquals("102.5", WeightConverter.formatDisplayNumber(102.5))
        assertEquals(
            1_250.0,
            WeightConverter.parseDisplayToKg("1250", WeightUnit.KG, 1_250.0)!!,
            0.001,
        )
    }

    @Test
    fun oneHundredPoundsTimesFiveIsFiveHundredNotFiveHundredOne() {
        val storedKg = WeightConverter.toKg(100.0, WeightUnit.LBS)
        val work = SetWork.of(storedKg, 5, LoadClass.LOADED)
        val display = WeightConverter.toDisplayValue(work.volumeKg, WeightUnit.LBS)

        assertEquals(500.5, display, 0.001)
        assertEquals(501, display.roundToInt())
        assertEquals(500L, WeightConverter.volumeDisplayWhole(work.volumeKg, WeightUnit.LBS))
        assertEquals(500, WeightConverter.volumeAnimationTarget(work.volumeKg, WeightUnit.LBS))
        assertEquals("500 lbs", WeightConverter.formatVolumeLabel(work.volumeKg, WeightUnit.LBS, us))
        assertEquals("500", WeightConverter.formatVolumeNumber(work.volumeKg, WeightUnit.LBS, us))
        assertEquals("500", SetCopy.workColumn(work, WeightUnit.LBS).value)
        assertEquals("lbs", SetCopy.workColumn(work, WeightUnit.LBS).label)
        assertNotEquals("501 lbs", work.volumeKg.toVolumeLabel(WeightUnit.LBS))
    }

    @Test
    fun halfBoundariesUseTiesToEven() {
        assertEquals(0L, WeightConverter.volumeDisplayWhole(0.5, WeightUnit.KG))
        assertEquals(2L, WeightConverter.volumeDisplayWhole(1.5, WeightUnit.KG))
        assertEquals(2L, WeightConverter.volumeDisplayWhole(2.5, WeightUnit.KG))
        assertEquals(4L, WeightConverter.volumeDisplayWhole(3.5, WeightUnit.KG))
        assertEquals(10L, WeightConverter.volumeDisplayWhole(9.5, WeightUnit.KG))
        assertEquals(10L, WeightConverter.volumeDisplayWhole(10.5, WeightUnit.KG))
        assertEquals("0 kg", WeightConverter.formatVolumeLabel(0.5, WeightUnit.KG, us))
        assertEquals("2 kg", WeightConverter.formatVolumeLabel(2.5, WeightUnit.KG, us))
    }

    @Test
    fun largeValuesKeepGroupingInBothUnits() {
        assertEquals("1,000,000 kg", WeightConverter.formatVolumeLabel(1_000_000.0, WeightUnit.KG, us))
        assertEquals(
            "2,204,620 lbs",
            WeightConverter.formatVolumeLabel(1_000_000.0, WeightUnit.LBS, us),
        )
    }

    @Test
    fun everySurfacePrintsTheSameBytesForBothUnits() {
        val samples = listOf(
            0.0, 0.5, 1.5, 2.5, 9.5, 10.5, 45.4, 227.0, 500.0, 999.6,
            1_000.0, 12_450.0, 50_000.0,
            WeightConverter.toKg(100.0, WeightUnit.LBS) * 5,
        )
        val grouping = NumberFormat.getIntegerInstance(us)
        for (kg in samples) {
            for (unit in WeightUnit.entries) {
                val work = SetWork(volumeKg = kg, bodyweightReps = 0)
                val whole = WeightConverter.volumeDisplayWhole(kg, unit)
                val number = WeightConverter.formatVolumeNumber(kg, unit, us)
                val label = WeightConverter.formatVolumeLabel(kg, unit, us)
                val column = SetCopy.workColumn(work, unit)
                val target = WeightConverter.volumeAnimationTarget(kg, unit)

                if (kg > 0.0) {
                    assertEquals(number, column.value)
                    assertEquals(unit.suffix, column.label)
                    assertEquals(label, "${column.value} ${column.label}")
                    assertEquals(grouping.format(whole), number)
                    assertEquals(grouping.format(target.toLong()), number)
                    assertEquals("$number ${unit.suffix}", label)
                }
            }
        }
    }
}
