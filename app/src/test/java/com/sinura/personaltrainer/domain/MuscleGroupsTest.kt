package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleGroupsTest {
    @Test
    fun blankDoesNotBecomeOther() {
        assertNull(MuscleGroups.resolved(""))
        assertNull(MuscleGroups.resolved("   "))
        assertEquals("Chest", MuscleGroups.resolved("  Chest "))
    }

    @Test
    fun newDraftStartsEmptyUnlessTheLibraryFilterNamedAMuscle() {
        assertEquals("", MuscleGroups.forNewDraft(null))
        assertEquals(CanonicalMuscle.CHEST.catalogLabel, MuscleGroups.forNewDraft(CanonicalMuscle.CHEST))
        assertEquals(CanonicalMuscle.QUADRICEPS.catalogLabel, MuscleGroups.forNewDraft(CanonicalMuscle.QUADRICEPS))
    }

    @Test
    fun otherIsNotSelectedOnAnEmptyDraft() {
        assertFalse(MuscleGroups.otherSelected(""))
        assertFalse(MuscleGroups.otherSelected("Chest"))
        assertTrue(MuscleGroups.otherSelected("Other"))
        assertTrue(MuscleGroups.otherSelected("Neck"))
    }
}
