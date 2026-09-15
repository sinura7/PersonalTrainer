package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoHostCopyTest {
    @Test
    fun linesNameTheActionAndTheThing() {
        assertEquals("Undo", UndoHostCopy.ACTION)
        assertEquals("Set deleted · 100 × 5", UndoHostCopy.setDeleted("100 × 5"))
        assertEquals("Lift removed · Squat", UndoHostCopy.liftRemoved("Squat"))
        assertEquals("Skipped · Push", UndoHostCopy.daySkipped("Push"))
    }
}
