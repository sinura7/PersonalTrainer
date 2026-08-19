package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Volume-scale numbers are grouped and whole; weight-entry numbers must NOT be, because
 * [WeightConverter.parseDisplayToKg] round-trips against [WeightConverter.formatDisplayNumber].
 */
class VolumeLabelTest {
    @Test
    fun volumeIsGroupedAndWhole() {
        assertEquals("12,450 kg", WeightConverter.formatVolumeLabel(12_450.0, WeightUnit.KG, Locale.US))
        assertEquals("500 kg", WeightConverter.formatVolumeLabel(500.0, WeightUnit.KG, Locale.US))
        assertEquals("1,000 kg", WeightConverter.formatVolumeLabel(999.6, WeightUnit.KG, Locale.US))
        assertEquals("0 kg", WeightConverter.formatVolumeLabel(0.0, WeightUnit.KG, Locale.US))
    }

    @Test
    fun volumeConvertsToTheDisplayUnitBeforeGrouping() {
        // 5000 kg -> 11023.1... lbs -> rounded and grouped
        assertEquals("11,023 lbs", WeightConverter.formatVolumeLabel(5_000.0, WeightUnit.LBS, Locale.US))
    }

    @Test
    fun volumeSurvivesNonFiniteInput() {
        assertEquals("0 kg", WeightConverter.formatVolumeLabel(Double.NaN, WeightUnit.KG, Locale.US))
        assertEquals("0 kg", WeightConverter.formatVolumeLabel(Double.POSITIVE_INFINITY, WeightUnit.KG, Locale.US))
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
}
