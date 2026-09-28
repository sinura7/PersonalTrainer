package com.sinura.personaltrainer.workout

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Saved state's notes key tells nothing saved from a note deleted (N2). A rebuilt workout screen
 * restores an empty note as a deletion, and lets the session row fill the field only when no notes
 * were saved at all; [SavedStateWorkoutDraft.sessionNotes] reads both as "".
 */
class SavedStateWorkoutDraftNotesKeyTest {
    /** S1. */
    @Test
    fun theNotesKeyTellsNothingSavedFromADeletedNote() {
        val nothingSaved = SavedStateWorkoutDraft(SavedStateHandle())
        assertNull("no notes saved reads as none", nothingSaved.sessionNotesIfSaved())
        assertEquals("the older read still gives an empty note", "", nothingSaved.sessionNotes())

        val deleted = SavedStateWorkoutDraft(SavedStateHandle())
        deleted.writeSelection(sessionId = "s1", exerciseId = null, notes = "", editingSetId = null)
        assertEquals("a deleted note reads as an empty note", "", deleted.sessionNotesIfSaved())

        val typed = SavedStateWorkoutDraft(SavedStateHandle())
        typed.writeSelection(sessionId = "s1", exerciseId = null, notes = "left knee sore", editingSetId = null)
        assertEquals("words read as the words", "left knee sore", typed.sessionNotesIfSaved())
    }
}
