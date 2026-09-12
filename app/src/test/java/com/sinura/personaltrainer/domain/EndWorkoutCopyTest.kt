package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndWorkoutCopyTest {
    @Test
    fun saveNeedsALoggedSet() {
        assertEquals("End workout?", EndWorkoutCopy.TITLE)
        assertEquals("Save as is", EndWorkoutCopy.SAVE)
        assertEquals("Leave without saving", EndWorkoutCopy.DISCARD)
        assertFalse(EndWorkoutCopy.canSave(0))
        assertTrue(EndWorkoutCopy.canSave(1))
        assertTrue(EndWorkoutCopy.body(0).contains("Nothing is logged yet"))
        assertTrue(EndWorkoutCopy.body(1).contains("set you logged"))
        assertTrue(EndWorkoutCopy.body(3).contains("3 sets"))
    }
}
