package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCopyTest {
    @Test
    fun quietHoursLineUsesStoredHours() {
        assertEquals("Quiet hours 22:00–07:00", ReminderCopy.quietHoursLine(22, 7))
        assertEquals("21:00", ReminderCopy.hourLabel(21))
        assertTrue(ReminderCopy.startChoices(22).contains(22))
        assertTrue(ReminderCopy.endChoices(7).contains(7))
        assertTrue(ReminderCopy.startChoices(19).contains(19))
    }

    @Test
    fun switchIsNotAnInvertedOffLabel() {
        assertEquals("Reminders", ReminderCopy.SWITCH_TITLE)
        assertFalse(ReminderCopy.SWITCH_TITLE.contains("off", ignoreCase = true))
        assertFalse(ReminderCopy.PERMISSION_BODY.contains("reliable", ignoreCase = true))
        assertTrue(ReminderCopy.PERMISSION_ACTION.length <= 12)
    }
}
