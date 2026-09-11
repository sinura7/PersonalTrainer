package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestBatteryCopyTest {
    @Test
    fun firstRestNamesUnrestrictedBattery() {
        assertEquals(
            "Allow unrestricted battery or the clock dies.",
            RestBatteryCopy.SENTENCE,
        )
        assertTrue(RestBatteryCopy.SENTENCE.contains("unrestricted battery"))
        assertTrue(RestBatteryCopy.SENTENCE.contains("clock dies"))
        assertFalse(RestBatteryCopy.SENTENCE.contains("overlay", ignoreCase = true))
        assertEquals("Got it", RestBatteryCopy.GOT_IT)
        assertTrue(RestBatteryCopy.GOT_IT.length <= 12)
    }
}
