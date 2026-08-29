package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockCopyTest {
    @Test
    fun regularDropsMinutesWhenZero() {
        assertEquals("6 AM", ClockCopy.format(6, 0, ClockFormat.TWELVE))
        assertEquals("6:30 PM", ClockCopy.format(18, 30, ClockFormat.TWELVE))
        assertEquals("12 PM", ClockCopy.format(12, 0, ClockFormat.TWELVE))
        assertEquals("12 AM", ClockCopy.format(0, 0, ClockFormat.TWELVE))
    }

    @Test
    fun militaryIsAlwaysFourDigits() {
        assertEquals("06:00", ClockCopy.format(6, 0, ClockFormat.TWENTY_FOUR))
        assertEquals("18:30", ClockCopy.format(18, 30, ClockFormat.TWENTY_FOUR))
    }

    @Test
    fun hourChoicesKeepTheCurrentHourEvenWhenItIsOffTheMenu() {
        val hours = ClockCopy.hourChoices(14)
        assertTrue(hours.contains(14))
        ClockCopy.SESSION_HOURS.forEach { hour -> assertTrue(hours.contains(hour)) }
    }

    @Test
    fun missingStorageIsRegularHours() {
        assertEquals(ClockFormat.TWELVE, ClockFormat.fromStorage(null))
        assertEquals(ClockFormat.TWELVE, ClockFormat.fromStorage("nope"))
        assertEquals(ClockFormat.TWENTY_FOUR, ClockFormat.fromStorage("24h"))
    }
}
