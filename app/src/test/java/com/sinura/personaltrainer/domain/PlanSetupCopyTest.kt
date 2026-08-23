package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSetupCopyTest {
    @Test
    fun rerunCopySaysItAddsABlockAndDoesNotDeleteHistory() {
        assertTrue(PlanSetupCopy.CAPTION.startsWith("This adds a new block."))
        assertTrue(PlanSetupCopy.CAPTION.contains("It does not delete history."))
        assertEquals("Add a new block", PlanSetupCopy.ROW_TITLE)
    }
}
