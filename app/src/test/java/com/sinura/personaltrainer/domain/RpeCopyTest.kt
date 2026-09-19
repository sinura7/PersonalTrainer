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
        assertEquals("four reps left", RpeCopy.meaning(6))
        assertEquals("two reps left", RpeCopy.meaning(8))
        assertEquals("max", RpeCopy.meaning(10))
        assertEquals(
            "RPE 8, about two reps left, not selected",
            RpeCopy.spoken(8, selected = false),
        )
        assertEquals("RPE 10, max, selected", RpeCopy.spoken(10, selected = true))
        assertEquals(RpeCopy.LABEL, "RPE · OPTIONAL")
        assertEquals(RpeCopy.HELPER, "6 = four reps left · 10 = max")
        assertEquals(RpeCopy.WARMUP_REASON, "Warm-up")
    }
}
