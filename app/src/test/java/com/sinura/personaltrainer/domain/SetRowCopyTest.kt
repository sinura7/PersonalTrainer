package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SetRowCopyTest {
    @Test
    fun floorChipsSpeakTheirDerivedOrdinal() {
        assertEquals("Actions for Set 1 of 4", SetRowCopy.actionsFor("Set 1 of 4"))
        assertEquals("Revise WU 1", SetRowCopy.revise("WU 1"))
        assertEquals("Delete Extra 1", SetRowCopy.delete("Extra 1"))
    }
}
