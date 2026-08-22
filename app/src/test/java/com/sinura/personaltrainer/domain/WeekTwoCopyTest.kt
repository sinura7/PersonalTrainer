package com.sinura.personaltrainer.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class WeekTwoCopyTest {
    @Test
    fun replayCopySaysItDoesNotAddRoutinesOrDeleteHistory() {
        assertTrue(WeekTwoCopy.CAPTION.contains("Does not add routines"))
        assertTrue(WeekTwoCopy.CAPTION.contains("delete history"))
    }

    @Test
    fun matchFailureSendsThemToSettingsNotToASilentEmptyWeek() {
        assertTrue(WeekTwoCopy.MATCH_FAILED.contains("Rebuild from Settings"))
    }
}
