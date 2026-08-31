package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDayCopyTest {
    @Test
    fun weekdayTitleMatchesTheCustomWeekName() {
        assertEquals("Friday", PlanDayCopy.weekdayTitle(Weekday.FRIDAY))
        assertEquals("Monday", PlanDayCopy.weekdayTitle(Weekday.MONDAY))
    }

    @Test
    fun runChipSaysSprints() {
        assertEquals("Run / sprints", PlanDayCopy.cardioPickLabel(CardioType.RUN))
        assertEquals("Walk", PlanDayCopy.cardioPickLabel(CardioType.WALK))
        assertEquals("Add session", PlanDayCopy.ADD_SESSION)
        assertEquals("Add extra", PlanDayCopy.ADD_EXTRA)
        assertEquals("Warm-up", PlanDayCopy.WARM_UP)
        assertTrue(PlanDayCopy.AUX_SUBTITLE.contains("warm-up"))
    }
}
