package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BodyweightCheckInTest {
    private val mondayStart = Weekday.MONDAY
    private val monday = LocalDate.of(2026, 8, 17).toEpochDay()

    @Test
    fun autoUsesTheFirstPreferredDayInWeekOrder() {
        assertEquals(
            Weekday.MONDAY,
            BodyweightCheckIn.dueWeekday(
                preferredDays = setOf(Weekday.WEDNESDAY, Weekday.MONDAY),
                weekStart = mondayStart,
                daysPerWeek = 2,
                override = null,
            ),
        )
    }

    @Test
    fun autoFallsBackToTheSchedulePlannerWhenPreferredDaysAreEmpty() {
        assertEquals(
            Weekday.MONDAY,
            BodyweightCheckIn.dueWeekday(
                preferredDays = emptySet(),
                weekStart = mondayStart,
                daysPerWeek = 3,
                override = null,
            ),
        )
    }

    @Test
    fun overrideWins() {
        assertEquals(
            Weekday.FRIDAY,
            BodyweightCheckIn.dueWeekday(
                preferredDays = setOf(Weekday.MONDAY),
                weekStart = mondayStart,
                daysPerWeek = 1,
                override = Weekday.FRIDAY,
            ),
        )
    }

    @Test
    fun dueOnTheCheckInDayUntilALogLandsThisWeek() {
        val preferred = setOf(Weekday.MONDAY)
        assertTrue(
            BodyweightCheckIn.isDueToday(
                todayEpochDay = monday,
                preferredDays = preferred,
                weekStart = mondayStart,
                daysPerWeek = 1,
                override = null,
                log = emptyList(),
            ),
        )
        assertFalse(
            BodyweightCheckIn.isDueToday(
                todayEpochDay = monday,
                preferredDays = preferred,
                weekStart = mondayStart,
                daysPerWeek = 1,
                override = null,
                log = listOf(BodyweightEntry(epochDay = monday, kg = 80.0)),
            ),
        )
        assertFalse(
            BodyweightCheckIn.isDueToday(
                todayEpochDay = monday + 1,
                preferredDays = preferred,
                weekStart = mondayStart,
                daysPerWeek = 1,
                override = null,
                log = emptyList(),
            ),
        )
    }
}
