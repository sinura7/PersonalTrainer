package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestCompleteCueTest {
    @Test
    fun previewAlwaysTurnsTheToneOnAndLeavesVibrationAlone() {
        val off = RestTimerPreferences(
            soundEnabled = false,
            vibrationEnabled = false,
            tickEnabled = false,
        )
        val preview = RestCompleteCue.previewPreferences(off)
        assertTrue(preview.soundEnabled)
        assertFalse(preview.vibrationEnabled)
        assertFalse(preview.tickEnabled)
        assertEquals(off.defaultRestSeconds, preview.defaultRestSeconds)
    }

    @Test
    fun playRowCopyNamesTheCompleteCue() {
        assertEquals("Play complete cue", RestCompleteCue.TITLE)
        assertTrue(RestCompleteCue.CAPTION.contains("stand-up tone"))
        assertTrue(RestCompleteCue.CAPTION.contains("Vibration"))
    }
}
