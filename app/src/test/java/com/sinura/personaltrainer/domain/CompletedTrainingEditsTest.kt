package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedTrainingEditsTest {
    @Test
    fun notesAndDeleteAreOnForEveryKind() {
        CompletedTraining.Kind.entries.forEach { kind ->
            assertTrue(kind.name, CompletedTrainingEdits.canEditNotes(kind))
            assertTrue(kind.name, CompletedTrainingEdits.canDelete(kind))
        }
    }

    @Test
    fun setRepairAndRepeatStayStrengthOnly() {
        assertTrue(CompletedTrainingEdits.canRepairSets(CompletedTraining.Kind.STRENGTH_SESSION))
        assertTrue(CompletedTrainingEdits.canRepeat(CompletedTraining.Kind.STRENGTH_SESSION))
        assertFalse(CompletedTrainingEdits.canRepairSets(CompletedTraining.Kind.ACTIVITY))
        assertFalse(CompletedTrainingEdits.canRepeat(CompletedTraining.Kind.ACTIVITY))
    }

    @Test
    fun deleteCopyNamesTheSessionAndCountsWhatItRemoves() {
        assertEquals("Delete Easy run?", ActivityEditCopy.deleteTitle("Easy run"))
        assertEquals("Delete this session?", ActivityEditCopy.deleteTitle("  "))
        assertEquals(
            "This deletes 1 logged set and its cardio from history. This cannot be undone.",
            ActivityEditCopy.deleteBody(setCount = 1, hasCardio = true),
        )
        assertEquals(
            "This deletes its cardio from history. This cannot be undone.",
            ActivityEditCopy.deleteBody(setCount = 0, hasCardio = true),
        )
    }
}
