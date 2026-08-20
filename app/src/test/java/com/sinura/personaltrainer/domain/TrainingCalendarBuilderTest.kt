package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class TrainingCalendarBuilderTest {
    private val zone = ZoneOffset.UTC
    private val august = YearMonth.of(2026, 8)
    private var nextId = 0

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun workout(id: String, iso: String, weightKg: Double, reps: Int, finished: Boolean = true): WorkoutSession {
        val ms = at(iso)
        return session(
            id = id,
            finishedAt = if (finished) ms else null,
            sets = listOf(
                set("s-${nextId++}", id, "ex-squat", "Squat", weightKg, reps, at = ms),
            ),
            exercises = listOf(sessionExercise("ex-squat", "Squat", "Quads")),
            date = ms,
        )
    }

    private fun TrainingMonth.day(date: LocalDate): CalendarDay =
        weeks.flatten().first { it.date == date }

    @Test
    fun gridStartsOnTheConfiguredWeekStart() {
        // 1 August 2026 is a Saturday.
        val monday = TrainingCalendarBuilder.build(august, emptyList(), zone, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 7, 27), monday.weeks.first().first().date)

        val sunday = TrainingCalendarBuilder.build(august, emptyList(), zone, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 7, 26), sunday.weeks.first().first().date)
    }

    @Test
    fun everyWeekIsWholeAndTheMonthIsCovered() {
        val grid = TrainingCalendarBuilder.build(august, emptyList(), zone, DayOfWeek.MONDAY)
        assertTrue(grid.weeks.all { it.size == 7 })
        val dates = grid.weeks.flatten().map { it.date }
        assertTrue(LocalDate.of(2026, 8, 1) in dates)
        assertTrue(LocalDate.of(2026, 8, 31) in dates)
    }

    @Test
    fun paddingDaysAreMarkedOutOfMonth() {
        val grid = TrainingCalendarBuilder.build(august, emptyList(), zone, DayOfWeek.MONDAY)
        assertFalse(grid.day(LocalDate.of(2026, 7, 27)).inMonth)
        assertTrue(grid.day(LocalDate.of(2026, 8, 1)).inMonth)
    }

    @Test
    fun trainedDaysCarryTheirSessions() {
        val grid = TrainingCalendarBuilder.build(
            august,
            listOf(workout("a", "2026-08-10T10:00:00Z", 100.0, 5)),
            zone,
            DayOfWeek.MONDAY,
        )
        val day = grid.day(LocalDate.of(2026, 8, 10))
        assertTrue(day.trained)
        assertEquals(listOf("a"), day.sessionIds)
        assertEquals(500.0, day.volumeKg, 0.0001)
        assertEquals(1, grid.trainedDays)
    }

    @Test
    fun twoSessionsInOneDayCountAsOneTrainedDay() {
        val grid = TrainingCalendarBuilder.build(
            august,
            listOf(
                workout("a", "2026-08-10T08:00:00Z", 100.0, 5),
                workout("b", "2026-08-10T18:00:00Z", 60.0, 10),
            ),
            zone,
            DayOfWeek.MONDAY,
        )
        assertEquals(1, grid.trainedDays)
        assertEquals(listOf("a", "b"), grid.day(LocalDate.of(2026, 8, 10)).sessionIds)
        assertEquals(1100.0, grid.day(LocalDate.of(2026, 8, 10)).volumeKg, 0.0001)
    }

    @Test
    fun intensityIsRelativeToTheMonthsOwnHardestDay() {
        val grid = TrainingCalendarBuilder.build(
            august,
            listOf(
                workout("light", "2026-08-05T10:00:00Z", 50.0, 5),
                workout("heavy", "2026-08-12T10:00:00Z", 100.0, 5),
            ),
            zone,
            DayOfWeek.MONDAY,
        )
        assertEquals(1f, grid.day(LocalDate.of(2026, 8, 12)).intensity, 0.0001f)
        assertEquals(0.5f, grid.day(LocalDate.of(2026, 8, 5)).intensity, 0.0001f)
    }

    @Test
    fun sessionsFromOtherMonthsDoNotSetTheScale() {
        // A monster session in July must not flatten every August day to nothing.
        val grid = TrainingCalendarBuilder.build(
            august,
            listOf(
                workout("july", "2026-07-15T10:00:00Z", 1000.0, 5),
                workout("august", "2026-08-12T10:00:00Z", 100.0, 5),
            ),
            zone,
            DayOfWeek.MONDAY,
        )
        assertEquals(1f, grid.day(LocalDate.of(2026, 8, 12)).intensity, 0.0001f)
        assertEquals(1, grid.trainedDays)
    }

    @Test
    fun unfinishedSessionsAreNotOnTheCalendar() {
        val grid = TrainingCalendarBuilder.build(
            august,
            listOf(workout("live", "2026-08-12T10:00:00Z", 100.0, 5, finished = false)),
            zone,
            DayOfWeek.MONDAY,
        )
        assertEquals(0, grid.trainedDays)
        assertFalse(grid.day(LocalDate.of(2026, 8, 12)).trained)
    }

    @Test
    fun aHeavyPaddingDayCannotPushIntensityAboveOne() {
        // 27 July is a padding day of the August grid, so its volume never enters
        // `busiest` — but it keeps its own. Unclamped this produced intensity 6.0,
        // which the calendar turned into an out-of-range colour alpha and threw.
        val grid = TrainingCalendarBuilder.build(
            august,
            listOf(
                workout("july", "2026-07-27T10:00:00Z", 200.0, 10),
                workout("august", "2026-08-05T10:00:00Z", 50.0, 5),
            ),
            zone,
            DayOfWeek.MONDAY,
        )

        val padding = grid.day(LocalDate.of(2026, 7, 27))
        assertTrue(padding.volumeKg > grid.day(LocalDate.of(2026, 8, 5)).volumeKg)
        assertEquals(1f, padding.intensity, 0.0001f)
        assertTrue(grid.weeks.flatten().all { it.intensity in 0f..1f })
    }

    @Test
    fun weekdayHeadingsFollowTheWeekStart() {
        assertEquals(
            listOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
            ),
            TrainingCalendarBuilder.weekdayOrder(DayOfWeek.SUNDAY),
        )
    }
}
