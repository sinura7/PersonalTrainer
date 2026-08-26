package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioCopyTest {
    @Test
    fun gymNamesAreSpokenNotSchemaEnums() {
        assertEquals("Run", CardioCopy.name(CardioType.RUN))
        assertEquals("Ride", CardioCopy.name(CardioType.RIDE))
        assertEquals("Walk", CardioCopy.name(CardioType.WALK))
        assertEquals("Row", CardioCopy.name(CardioType.ROW))
        assertEquals("Swim", CardioCopy.name(CardioType.SWIM))
        assertEquals("Hike", CardioCopy.name(CardioType.HIKE))
        assertEquals("Ski", CardioCopy.name(CardioType.SKI))
        assertEquals("Other", CardioCopy.name(CardioType.OTHER))
        CardioType.entries.forEach { type ->
            val spoken = CardioCopy.name(type)
            assertNotEquals(type.name, spoken)
            assertFalse(type.name, spoken == spoken.uppercase())
        }
    }

    @Test
    fun liveScreenHasOneVoltAndStackedSecondaries() {
        assertEquals("Finish", CardioCopy.LIVE_VOLT)
        assertEquals(
            listOf("Finish", "Leave running", "Discard"),
            CardioCopy.LIVE_ACTIONS,
        )
        assertEquals(1, CardioCopy.LIVE_ACTIONS.count { it == CardioCopy.LIVE_VOLT })
        assertFalse(CardioCopy.LIVE_ACTIONS.contains("Keep and exit"))
        assertEquals("Done", CardioCopy.DONE)
    }

    @Test
    fun leaveDialogHasOneVoltAndStackedButtons() {
        assertEquals("Leave running", CardioCopy.LEAVE_DIALOG_VOLT)
        assertEquals(
            listOf("Leave running", "Stay", "Discard this session instead"),
            CardioCopy.LEAVE_DIALOG_ACTIONS,
        )
        assertEquals(1, CardioCopy.LEAVE_DIALOG_ACTIONS.count { it == CardioCopy.LEAVE_DIALOG_VOLT })
        assertNotEquals(CardioCopy.LIVE_VOLT, CardioCopy.LEAVE_DIALOG_VOLT)
        assertEquals("Stay", CardioCopy.STAY)
    }

    @Test
    fun discardIsNeverOneTap() {
        assertEquals("Discard", CardioCopy.DISCARD)
        assertEquals("Discard this session?", CardioCopy.DISCARD_TITLE)
        assertEquals("This deletes the session. This cannot be undone.", CardioCopy.DISCARD_BODY)
        assertEquals("Discard", CardioCopy.DISCARD_CONFIRM)
        assertNotEquals(CardioCopy.DISCARD_TITLE, CardioCopy.DISCARD)
        assertTrue(CardioCopy.DISCARD_BODY.contains("cannot be undone"))
    }

    @Test
    fun clockCaptionIsGymLanguage() {
        assertFalse(CardioCopy.CLOCK_CAPTION.contains("Process death"))
        assertFalse(CardioCopy.CLOCK_CAPTION.contains("Reboot"))
        assertTrue(CardioCopy.CLOCK_CAPTION.contains("bar"))
    }

}
