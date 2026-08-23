package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RpeCopyTest {
    @Test
    fun explainerNamesTheScale() {
        assertEquals(
            "How hard that set felt. 10 is nothing left; 6 is several reps in the tank. Optional. Two top sets at 9+ hold the load.",
            RpeCopy.BLURB,
        )
    }
}
