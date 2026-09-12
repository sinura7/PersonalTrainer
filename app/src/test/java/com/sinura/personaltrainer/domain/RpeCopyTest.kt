package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RpeCopyTest {
    @Test
    fun historyNamesLastEffortAndKeepsRpeOptional() {
        assertEquals("Optional.", RpeCopy.blurb(null))
        assertEquals("Last time RPE 8. Optional.", RpeCopy.blurb(8))
        assertEquals(8, RpeCopy.recommended(8))
        assertNull(RpeCopy.recommended(null))
        assertNull(RpeCopy.recommended(3))
    }
}
