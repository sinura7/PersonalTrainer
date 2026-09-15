package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestBatteryCopyTest {
    @Test
    fun firstRestNamesUnrestrictedBattery() {
        assertEquals(RestHonestyCopy.FIRST_REST, RestBatteryCopy.SENTENCE)
        assertTrue(RestBatteryCopy.SENTENCE.contains("unrestricted battery", ignoreCase = true))
        assertTrue(RestBatteryCopy.SENTENCE.contains("clock dies"))
        assertTrue(RestBatteryCopy.SENTENCE.contains("Settings"))
        assertFalse(RestBatteryCopy.SENTENCE.contains("overlay", ignoreCase = true))
        assertFalse(RestBatteryCopy.SENTENCE.contains("precise", ignoreCase = true))
        assertEquals("Got it", RestBatteryCopy.GOT_IT)
        assertTrue(RestBatteryCopy.GOT_IT.length <= 12)
    }
}
