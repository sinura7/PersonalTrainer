package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.domain.Weekday
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklySchedulePlanTest {
    private val fourDay = WeeklySchedulePlanner.trainingDayIndices(4)

    @Test
    fun fullyPinnedWeekHasNoOpenTrainingSlot() {
        val week = plan(
            day(0, rest = false, slotId = "a"),
            day(1, rest = true),
            day(2, rest = false, slotId = "b"),
            day(3, rest = true),
            day(4, rest = false, slotId = "c"),
            day(5, rest = false, slotId = "d"),
            day(6, rest = true),
        )
        assertFalse(week.hasOpenTrainingSlot(todayEpochDay = 0, trainingDayIndices = fourDay))
        assertFalse(week.hasOpenTrainingSlot(todayEpochDay = 3, trainingDayIndices = fourDay))
    }

    @Test
    fun emptyWeekKeepsSuggestAlive() {
        val week = plan(*(0..6).map { day(it.toLong(), rest = true) }.toTypedArray())
        assertTrue(week.hasOpenTrainingSlot(todayEpochDay = 0, trainingDayIndices = fourDay))
    }

    @Test
    fun leftoverRestDaysDoNotCountAsOpenOnceTrainingSlotsArePinned() {
        val week = plan(
            day(0, rest = false, slotId = "a"),
            day(1, rest = true),
            day(2, rest = false, slotId = "b"),
            day(3, rest = true),
            day(4, rest = false, slotId = "c"),
            day(5, rest = false, slotId = "d"),
            day(6, rest = true),
        )
        // Sunday rest on a 4-day week is a rest day, not a hole.
        assertFalse(week.hasOpenTrainingSlot(todayEpochDay = 6, trainingDayIndices = fourDay))
    }

    @Test
    fun unpinnedTrainingIndexTodayIsOpen() {
        val week = plan(
            day(0, rest = false, slotId = "a"),
            day(1, rest = true),
            day(2, rest = true),
            day(3, rest = true),
            day(4, rest = true),
            day(5, rest = true),
            day(6, rest = true),
        )
        assertTrue(week.hasOpenTrainingSlot(todayEpochDay = 2, trainingDayIndices = fourDay))
    }

    private fun plan(vararg days: SuggestedTrainingDay) = WeeklySchedulePlan(
        weekStartEpochDay = days.first().epochDay,
        generatedAtMs = 0L,
        preferences = SchedulePreferences.DEFAULT,
        resolvedSplit = SplitStyle.FULL_BODY,
        days = days.toList(),
        thinHistory = true,
        summary = "",
    )

    private fun day(epoch: Long, rest: Boolean, slotId: String? = null) = SuggestedTrainingDay(
        epochDay = epoch,
        dayOfWeek = Weekday.MONDAY,
        isRest = rest,
        focusKind = if (rest) SessionFocusKind.RECOVERY else SessionFocusKind.FULL_BODY,
        focusTitle = if (rest) "Rest" else "Train",
        routineId = null,
        routineName = null,
        reason = "",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
        slotId = slotId,
    )
}
