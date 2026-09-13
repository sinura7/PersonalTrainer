package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsHomeCopyTest {
    @Test
    fun indexSummariesNameTheCurrentChoice() {
        assertEquals(
            "lbs · Regular",
            SettingsHomeCopy.displaySummary(WeightUnit.LBS, ClockFormat.TWELVE),
        )
        assertEquals(SettingsHomeCopy.OFF, SettingsHomeCopy.remindersSummary(
            ReminderPreferences(optOut = true),
            ClockFormat.TWELVE,
        ))
        assertEquals(
            SettingsHomeCopy.NO_DAYS,
            SettingsHomeCopy.remindersSummary(ReminderPreferences(), ClockFormat.TWELVE),
        )
        val saturday = ReminderPreferences(
            dayAlarms = mapOf(Weekday.SATURDAY to DayReminder(19, 30)),
        )
        assertEquals(
            "Sat · 7:30 PM",
            SettingsHomeCopy.remindersSummary(saturday, ClockFormat.TWELVE),
        )
        assertEquals(
            "3 days · Upper / Lower · Strength",
            SettingsHomeCopy.generatorSummary(
                daysPerWeek = 4,
                preferredDays = setOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY),
                split = SplitStyle.UPPER_LOWER,
                goal = TrainingGoal.STRENGTH,
            ),
        )
        assertEquals("1:30", SettingsHomeCopy.restSummary(RestTimerPreferences(defaultRestSeconds = 90)))
        assertEquals("Not set", SettingsHomeCopy.bodyweightSummary(null, WeightUnit.LBS, null))
        assertEquals(
            SettingsHomeCopy.BACKUP_SUMMARY,
            "Export, restore, Drive",
        )
    }
}
