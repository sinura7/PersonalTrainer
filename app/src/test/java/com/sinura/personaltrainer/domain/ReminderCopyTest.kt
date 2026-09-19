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
        assertEquals(0, ReminderCopy.toHour24(12, pm = false))
        assertEquals(12, ReminderCopy.toHour24(12, pm = true))
        assertEquals(7, ReminderCopy.twelveHour(19))
        assertTrue(ReminderCopy.isPm(19))
        assertFalse(ReminderCopy.isPm(7))
        assertEquals(60, ReminderCopy.minutes.size)
        assertEquals(listOf("AM", "PM"), ReminderCopy.periodLabels)
        assertFalse(ReminderCopy.shouldCommitSettledPage(3, 3))
        assertTrue(ReminderCopy.shouldCommitSettledPage(4, 3))
    }

    @Test
    fun switchIsNotAnInvertedOffLabel() {
        assertEquals("Reminders", ReminderCopy.SWITCH_TITLE)
        assertFalse(ReminderCopy.SWITCH_TITLE.contains("off", ignoreCase = true))
        assertFalse(ReminderCopy.PERMISSION_BODY.contains("reliable", ignoreCase = true))
        assertTrue(ReminderCopy.PERMISSION_ACTION.length <= 12)
    }
}
