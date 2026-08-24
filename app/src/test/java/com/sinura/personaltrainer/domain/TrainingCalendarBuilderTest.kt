package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.util.toCivilYearMonth
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class TrainingCalendarBuilderTest {
    private val zone = ZoneOffset.UTC
    private val august = YearMonth.of(2026, 8)
    private var nextId = 0

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun workout(
        id: String,
        iso: String,
        weightKg: Double,
        reps: Int,
        sets: Int = 1,
        finished: Boolean = true,
    ): WorkoutSession {
        val ms = at(iso)
        return session(
            id = id,
            finishedAt = if (finished) ms else null,
            sets = (0 until sets).map { set("s-${nextId++}", id, "ex-squat", "Squat", weightKg, reps, at = ms) },
            exercises = listOf(sessionExercise("ex-squat", "Squat", "Quads")),
            date = ms,
        )
    }

    private fun TrainingMonth.day(date: LocalDate): CalendarDay =
        weeks.flatten().first { it.date.epochDay == date.toEpochDay() }

    @Test
    fun gridStartsOnTheConfiguredWeekStart() {
        // 1 August 2026 is a Saturday.
        val monday = TrainingCalendarBuilder.build(august, emptyList(), zone, Weekday.MONDAY)
        assertEquals(LocalDate.of(2026, 7, 27).toEpochDay(), monday.weeks.first().first().date.epochDay)

        val sunday = TrainingCalendarBuilder.build(august, emptyList(), zone, Weekday.SUNDAY)
        assertEquals(LocalDate.of(2026, 7, 26).toEpochDay(), sunday.weeks.first().first().date.epochDay)
    }

    @Test
    fun everyWeekIsWholeAndTheMonthIsCovered() {
        val grid = TrainingCalendarBuilder.build(august, emptyList(), zone, Weekday.MONDAY)
        assertTrue(grid.weeks.all { it.size == 7 })
        val dates = grid.weeks.flatten().map { it.date.epochDay }
        assertTrue(LocalDate.of(2026, 8, 1).toEpochDay() in dates)
        assertTrue(LocalDate.of(2026, 8, 31).toEpochDay() in dates)
    }

    @Test
    fun paddingDaysAreMarkedOutOfMonth() {
        val grid = TrainingCalendarBuilder.build(august, emptyList(), zone, Weekday.MONDAY)
        assertFalse(grid.day(LocalDate.of(2026, 7, 27)).inMonth)
        assertTrue(grid.day(LocalDate.of(2026, 8, 1)).inMonth)
    }

    @Test
    fun trainedDaysCarryTheirSessions() {
        val grid = TrainingCalendarBuilder.build(
            august,
            listOf(workout("a", "2026-08-10T10:00:00Z", 100.0, 5)),
            zone,
            Weekday.MONDAY,
        )
        val day = grid.day(LocalDate.of(2026, 8, 10))
        assertTrue(day.trained)
        assertEquals(listOf("a"), day.sessionIds)
        assertEquals(500.0, day.work.volumeKg, 0.0001)
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
            Weekday.MONDAY,
        )
        assertEquals(1, grid.trainedDays)
        assertEquals(listOf("a", "b"), grid.day(LocalDate.of(2026, 8, 10)).sessionIds)
        assertEquals(1100.0, grid.day(LocalDate.of(2026, 8, 10)).work.volumeKg, 0.0001)
    }

    @Test
    fun intensityIsRelativeToTheMonthsOwnHardestDay() {
        // Measured in working sets, not kilograms. Tonnage is not a unit every lift has, and a
        // calisthenics month would otherwise shade every square at zero.
        val grid = TrainingCalendarBuilder.build(
            august,
            listOf(
                workout("light", "2026-08-05T10:00:00Z", 50.0, 5, sets = 2),
                workout("heavy", "2026-08-12T10:00:00Z", 100.0, 5, sets = 4),
            ),
            zone,
            Weekday.MONDAY,
        )
        assertEquals(1f, grid.day(LocalDate.of(2026, 8, 12)).intensity, 0.0001f)
        assertEquals(0.5f, grid.day(LocalDate.of(2026, 8, 5)).intensity, 0.0001f)
    }

    @Test
    fun aCalisthenicsMonthIsStillShaded() {
        // The regression this guards: with intensity keyed on tonnage, a month of pull-ups
        // has no tonnage at all and every day of the grid renders as untrained.
        val bodyweightDay = session(
            id = "bw",
            finishedAt = at("2026-08-12T10:00:00Z"),
            sets = (0 until 3).map {
                set("bw-$it", "bw", "ex-pu", "Pull-Up", 0.0, 10, at = at("2026-08-12T10:00:00Z"))
            },
            exercises = listOf(sessionExercise("ex-pu", "Pull-Up", "Back", LoadType.BODYWEIGHT)),
            date = at("2026-08-12T10:00:00Z"),
        )
        val grid = TrainingCalendarBuilder.build(august, listOf(bodyweightDay), zone, Weekday.MONDAY)
        val day = grid.day(LocalDate.of(2026, 8, 12))
        assertTrue(day.trained)
        assertEquals(1f, day.intensity, 0.0001f)
        assertEquals(0.0, day.work.volumeKg, 0.0001)
        assertEquals(30, day.work.bodyweightReps)
        assertEquals(30, grid.work.bodyweightReps)
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
            Weekday.MONDAY,
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
            Weekday.MONDAY,
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
                workout("july", "2026-07-27T10:00:00Z", 200.0, 10, sets = 6),
                workout("august", "2026-08-05T10:00:00Z", 50.0, 5, sets = 1),
            ),
            zone,
            Weekday.MONDAY,
        )

        val padding = grid.day(LocalDate.of(2026, 7, 27))
        assertTrue(padding.sessionIds.isNotEmpty())
        assertEquals(1f, padding.intensity, 0.0001f)
        assertTrue(grid.weeks.flatten().all { it.intensity in 0f..1f })
    }

    @Test
    fun summariesBuildAMonthWithoutMaterializingSets() {
        val summary = SessionSummary(
            id = "a",
            routineId = null,
            routineName = "Push",
            date = at("2026-08-10T10:00:00Z"),
            finishedAt = at("2026-08-10T10:00:00Z"),
            durationMinutes = 40,
            workingSets = 4,
            volumeKg = 500.0,
            localEpochDay = LocalDate.of(2026, 8, 10).toEpochDay(),
        )
        val grid = TrainingCalendarBuilder.buildSummaries(
            month = august.toCivilYearMonth(),
            summaries = listOf(summary),
            weekStart = Weekday.MONDAY,
        )
        val day = grid.day(LocalDate.of(2026, 8, 10))
        assertTrue(day.trained)
        assertEquals(listOf("a"), day.sessionIds)
        assertEquals(4, grid.workingSets)
        assertEquals(500.0, day.work.volumeKg, 0.0001)
    }

    @Test
    fun summaryActivitiesDoNotCountAsWorkoutSessionIds() {
        val activity = SessionSummary(
            id = "run-1",
            routineId = null,
            routineName = "Easy run",
            date = at("2026-08-10T10:00:00Z"),
            finishedAt = at("2026-08-10T10:00:00Z"),
            durationMinutes = 40,
            workingSets = 0,
            volumeKg = 0.0,
            localEpochDay = LocalDate.of(2026, 8, 10).toEpochDay(),
            cardioSeconds = 2_400L,
            kind = HistoryKind.ACTIVITY,
        )
        val empty = TrainingCalendarBuilder.buildSummaries(
            month = august.toCivilYearMonth(),
            summaries = emptyList(),
            weekStart = Weekday.MONDAY,
        )
        assertEquals(0, empty.trainedDays)
        assertTrue(empty.weeks.flatten().all { it.intensity == 0f })
        val grid = TrainingCalendarBuilder.buildSummaries(
            month = august.toCivilYearMonth(),
            summaries = listOf(activity),
            weekStart = Weekday.MONDAY,
        )
        val day = grid.day(LocalDate.of(2026, 8, 10))
        assertTrue(day.sessionIds.isEmpty())
        assertEquals(listOf("run-1"), day.activityIds)
        assertEquals(1, grid.trainedDays)
    }

    @Test
    fun weekdayHeadingsFollowTheWeekStart() {
        assertEquals(
            listOf(
                Weekday.SUNDAY, Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY,
                Weekday.THURSDAY, Weekday.FRIDAY, Weekday.SATURDAY,
            ),
            TrainingCalendarBuilder.weekdayOrder(Weekday.SUNDAY),
        )
    }
}
