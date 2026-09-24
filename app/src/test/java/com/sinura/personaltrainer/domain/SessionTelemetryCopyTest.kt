package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionTelemetryCopyTest {
    @Test
    fun elapsedIsMinuteGrainNotASecondsClock() {
        assertEquals("0 min", SessionTelemetryCopy.elapsedMinutesLabel(0))
        assertEquals("0 min", SessionTelemetryCopy.elapsedMinutesLabel(59))
        assertEquals("1 min", SessionTelemetryCopy.elapsedMinutesLabel(60))
        assertEquals("18 min", SessionTelemetryCopy.elapsedMinutesLabel(18 * 60 + 4))
        assertFalse(SessionTelemetryCopy.elapsedMinutesLabel(1084).contains(":"))
    }
}
