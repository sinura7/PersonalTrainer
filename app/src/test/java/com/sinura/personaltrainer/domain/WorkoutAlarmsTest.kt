package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAlarmsTest {
    @Test
    fun encodeAndDecodeRoundTripPerDay() {
        val stored = WorkoutAlarms.encode(
            mapOf(Weekday.SATURDAY to DayReminder(hour = 19, minute = 30)),
        )
        assertTrue(stored.single().startsWith("SATURDAY=19:30"))
        val decoded = WorkoutAlarms.decode(stored)
        assertEquals(DayReminder(19, 30), decoded[Weekday.SATURDAY])
    }

    @Test
    fun nextTriggerStaysTodayWhenTheClockIsStillAhead() {
        val saturday = CivilDate(2026, 9, 12).epochDay
        assertEquals(
            saturday,
            WorkoutAlarms.nextTriggerEpochDay(
                weekday = Weekday.SATURDAY,
                hour = 19,
                minute = 30,
                todayEpochDay = saturday,
                nowMinutes = 18 * 60,
            ),
        )
    }

    @Test
    fun nextTriggerRollsAWeekWhenTheClockIsBehind() {
        val saturday = CivilDate(2026, 9, 12).epochDay
        assertEquals(
            saturday + 7,
            WorkoutAlarms.nextTriggerEpochDay(
                weekday = Weekday.SATURDAY,
                hour = 18,
                minute = 0,
                todayEpochDay = saturday,
                nowMinutes = 21 * 60,
            ),
        )
    }
}
