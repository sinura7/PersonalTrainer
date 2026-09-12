package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCopyTest {
    @Test
    fun quietHoursLineUsesStoredHours() {
        assertEquals(
            "Quiet hours 22:00–07:00",
            ReminderCopy.quietHoursLine(22, 7, ClockFormat.TWENTY_FOUR),
        )
        assertEquals("10 PM", ReminderCopy.hourLabel(22, ClockFormat.TWELVE))
        assertTrue(ReminderCopy.startChoices(22).contains(22))
        assertTrue(ReminderCopy.endChoices(7).contains(7))
        assertTrue(ReminderCopy.startChoices(19).contains(19))
        assertEquals(7, ReminderCopy.toHour24(7, pm = false))
        assertEquals(19, ReminderCopy.toHour24(7, pm = true))
    }

    @Test
    fun switchIsNotAnInvertedOffLabel() {
        assertEquals("Reminders", ReminderCopy.SWITCH_TITLE)
        assertFalse(ReminderCopy.SWITCH_TITLE.contains("off", ignoreCase = true))
        assertFalse(ReminderCopy.PERMISSION_BODY.contains("reliable", ignoreCase = true))
        assertTrue(ReminderCopy.PERMISSION_ACTION.length <= 12)
    }
}
