package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerFormPolicyTest {
    @Test
    fun saveRequiresAtLeastOneLineInEachMode() {
        assertFalse(ComposerFormPolicy.canSave("strength", strengthCount = 0, cardioCount = 0))
        assertTrue(ComposerFormPolicy.canSave("strength", strengthCount = 1, cardioCount = 0))
        assertFalse(ComposerFormPolicy.canSave("cardio", strengthCount = 0, cardioCount = 0))
        assertTrue(ComposerFormPolicy.canSave("cardio", strengthCount = 0, cardioCount = 1))
        assertFalse(ComposerFormPolicy.canSave("mixed", strengthCount = 0, cardioCount = 0))
        assertTrue(ComposerFormPolicy.canSave("mixed", strengthCount = 1, cardioCount = 0))
        assertTrue(ComposerFormPolicy.canSave("mixed", strengthCount = 0, cardioCount = 1))
    }

    @Test
    fun blankWeightStillReadsAsBodyweightOnAddSet() {
        val ready = ComposerCopy.strengthEntry("", "8", WeightUnit.KG) as StrengthEntry.ReadySet
        assertEquals(0.0, ready.weightKg, 0.0)
        assertEquals(8, ready.reps)
    }
}
