package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun lineDropsVolumeAt360FontTwo() {
        val withVolume = SessionTelemetryCopy.line(
            elapsedSeconds = 18 * 60,
            workingSets = 7,
            volume = "2,340 kg",
            includeVolume = true,
        )
        assertEquals("18 min · 7 sets · 2,340 kg", withVolume)
        val dropped = SessionTelemetryCopy.line(
            elapsedSeconds = 18 * 60,
            workingSets = 7,
            volume = "2,340 kg",
            includeVolume = false,
        )
        assertEquals("18 min · 7 sets", dropped)
        assertFalse(SessionTelemetryCopy.includeVolume(fontScale = 2f, widthDp = 360))
        assertTrue(SessionTelemetryCopy.includeVolume(fontScale = 1.6f, widthDp = 360))
        assertTrue(SessionTelemetryCopy.spoken(60, 1, null, false).contains(SessionTelemetryCopy.OPEN_DETAILS))
        assertEquals("1 set", SessionTelemetryCopy.setsLabel(1))
    }
}
