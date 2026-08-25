package com.sinura.personaltrainer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesKindTest {
    @Test
    fun sessionAndProgramKeepTheirOwnWords() {
        assertEquals("Session notes", notesToggleLabel(NotesKind.SESSION, notes = "", expanded = false))
        assertEquals(
            "Session notes · saved",
            notesToggleLabel(NotesKind.SESSION, notes = "felt strong", expanded = false),
        )
        assertEquals("Add notes", notesToggleLabel(NotesKind.PROGRAM, notes = "", expanded = false))
        assertEquals("Notes", notesToggleLabel(NotesKind.PROGRAM, notes = "tempo", expanded = false))
        assertEquals("Hide notes", notesToggleLabel(NotesKind.SESSION, notes = "x", expanded = true))
        assertEquals("Hide notes", notesToggleLabel(NotesKind.PROGRAM, notes = "x", expanded = true))
    }
}
