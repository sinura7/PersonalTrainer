package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutWeightCopyTest {
    @Test fun importedPrecisionIsVisibleInEntrySpokenAndCommitWhileConversionRulesRemainUnchanged() {
        assertEquals("99999.99", WorkoutWeightCopy.number(99999.99, WeightUnit.KG))
        assertEquals("Weight 99999.99 kg", SetCopy.weightWellSpoken(WeightMeaning.LIFTED, 99999.99, WeightUnit.KG, entryPrecision = true))
        assertEquals("99999.99 kg × 8", SetCopy.setLine(99999.99, 8, LoadClass.LOADED, WeightUnit.KG, entryPrecision = true))
        assertEquals("85.6 kg", WorkoutWeightCopy.label(WeightConverter.toKg(85.55, WeightUnit.KG), WeightUnit.KG))
        assertEquals(WeightConverter.formatLabel(45.4, WeightUnit.LBS), WorkoutWeightCopy.label(45.4, WeightUnit.LBS))
        assertEquals("60", WorkoutWeightCopy.number(60.0, WeightUnit.KG))
    }
}
