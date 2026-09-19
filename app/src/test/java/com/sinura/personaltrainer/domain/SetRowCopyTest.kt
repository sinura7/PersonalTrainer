package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SetRowCopyTest {
    @Test
    fun sheetRowsSpeakTheirNumber() {
        assertEquals("Actions for set 2", SetRowCopy.actionsForSet(2))
        assertEquals("Revise set 2", SetRowCopy.reviseSet(2))
        assertEquals("Delete set 2", SetRowCopy.deleteSet(2))
    }

    @Test
    fun floorChipsSpeakTheirDerivedOrdinal() {
        assertEquals("Actions for Set 1 of 4", SetRowCopy.actionsFor("Set 1 of 4"))
        assertEquals("Revise WU 1", SetRowCopy.revise("WU 1"))
        assertEquals("Delete Extra 1", SetRowCopy.delete("Extra 1"))
    }
}
