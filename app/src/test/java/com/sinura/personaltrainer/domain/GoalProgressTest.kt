package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalProgressTest {
    private val today = CivilDate(2026, 8, 26)
    private val captured = CapturedCivilTime(
        instantMillis = 1_700_000_000_000L,
        zoneId = "UTC",
        offsetSeconds = 0,
        localEpochDay = today.epochDay,
    )

    @Test
    fun sessionCountMeetsTargetWithoutAStreak() {
        val goal = goal(GoalKind.SESSION_COUNT, target = 3.0)
        val projections = listOf(
            projection(today.minusDays(1).epochDay, 1),
            projection(today.epochDay, 2),
        )
        val snap = GoalProgress.measure(
            goal = goal,
            projections = projections,
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        assertEquals(3.0, snap.currentValue, 0.01)
        assertTrue(snap.met)
        assertFalse(snap.goal.paused)
    }

    @Test
    fun pausedGoalDoesNotCountAsMet() {
        val goal = goal(GoalKind.SESSION_COUNT, target = 1.0).copy(paused = true)
        val snap = GoalProgress.measure(
            goal = goal,
            projections = listOf(projection(today.epochDay, 2)),
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        assertFalse(snap.met)
        assertEquals(0.0, snap.currentValue, 0.0)
    }

    @Test
    fun adherenceUsesExpectedDaysNotConsecutiveStreaks() {
        val expected = GoalProgress.expectedTrainingDays(
            start = today.previousOrSame(Weekday.MONDAY).epochDay,
            end = today.epochDay,
            trainingDaysPerWeek = 4,
            period = GoalPeriod.WEEK,
        )
        assertEquals(4, expected)
    }

    @Test
    fun cardioKindsReadHorizonTotals() {
        val projections = listOf(
            DailyProjection(
                localEpochDay = today.epochDay,
                sessionCount = 1,
                workingSets = 0,
                volumeKg = 0.0,
                activeMinutes = 40,
                cardioSeconds = 2_400L,
                cardioDistanceMeters = 5_000.0,
                sessionIds = listOf("c1"),
            ),
        )
        val duration = GoalProgress.measure(
            goal = goal(GoalKind.CARDIO_DURATION, target = 30.0),
            projections = projections,
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        assertEquals(40.0, duration.currentValue, 0.01)
        assertTrue(duration.met)
        val distance = GoalProgress.measure(
            goal = goal(GoalKind.CARDIO_DISTANCE, target = 4_000.0),
            projections = projections,
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        assertEquals(5_000.0, distance.currentValue, 0.01)
        assertTrue(distance.met)
    }

    @Test
    fun adherenceMeasuresTrainedDaysOverExpectedDays() {
        val projections = listOf(
            projection(today.minusDays(2).epochDay, 1),
            projection(today.epochDay, 1),
        )
        val snap = GoalProgress.measure(
            goal = goal(GoalKind.ADHERENCE, target = 0.5),
            projections = projections,
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        assertEquals(0.5, snap.currentValue, 0.01)
        assertTrue(snap.met)
    }

    @Test
    fun monthYearAndAllTimePeriodsShareTheSameMetrics() {
        val older = projection(CivilDate(2025, 12, 1).epochDay, 1)
        val thisYear = projection(CivilDate(2026, 1, 15).epochDay, 2)
        val thisMonth = projection(today.epochDay, 1)
        val projections = listOf(older, thisYear, thisMonth)
        val month = GoalProgress.measure(
            goal = goal(GoalKind.SESSION_COUNT, target = 1.0).copy(period = GoalPeriod.MONTH),
            projections = projections,
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        val year = GoalProgress.measure(
            goal = goal(GoalKind.ACTIVE_MINUTES, target = 30.0).copy(period = GoalPeriod.YEAR),
            projections = projections,
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        val allTime = GoalProgress.measure(
            goal = goal(GoalKind.SESSION_COUNT, target = 4.0).copy(period = GoalPeriod.ALL_TIME),
            projections = projections,
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        assertEquals(1.0, month.currentValue, 0.01)
        assertEquals(90.0, year.currentValue, 0.01)
        assertEquals(4.0, allTime.currentValue, 0.01)
        assertTrue(allTime.met)
    }

    @Test
    fun zeroTargetNeverMeetsAndMissingFactsStayAtZero() {
        val zero = GoalProgress.measure(
            goal = goal(GoalKind.SESSION_COUNT, target = 0.0),
            projections = listOf(projection(today.epochDay, 2)),
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        assertFalse(zero.met)
        assertEquals(0.0, zero.ratio, 0.0)
        val lift = GoalProgress.measure(
            goal = goal(GoalKind.LIFT_TARGET, target = 100.0),
            projections = emptyList(),
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = null,
            liftBestKg = null,
        )
        assertEquals(0.0, lift.currentValue, 0.0)
        assertFalse(lift.met)
        val weight = GoalProgress.measure(
            goal = goal(GoalKind.BODYWEIGHT, target = 80.0),
            projections = emptyList(),
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = 82.0,
            liftBestKg = null,
        )
        assertFalse(weight.met)
    }

    @Test
    fun expectedTrainingDaysIsZeroWhenTheRangeIsInverted() {
        assertEquals(
            0,
            GoalProgress.expectedTrainingDays(
                start = today.epochDay,
                end = today.minusDays(1).epochDay,
                trainingDaysPerWeek = 4,
                period = GoalPeriod.WEEK,
            ),
        )
        val monthExpected = GoalProgress.expectedTrainingDays(
            start = CivilDate(2026, 8, 1).epochDay,
            end = today.epochDay,
            trainingDaysPerWeek = 4,
            period = GoalPeriod.MONTH,
        )
        assertTrue(monthExpected >= 4)
    }

    @Test
    fun liftAndBodyweightUseSuppliedFacts() {
        val lift = GoalProgress.measure(
            goal = goal(GoalKind.LIFT_TARGET, target = 100.0),
            projections = emptyList(),
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = 80.0,
            liftBestKg = 102.5,
        )
        assertTrue(lift.met)
        val weight = GoalProgress.measure(
            goal = goal(GoalKind.BODYWEIGHT, target = 80.0),
            projections = emptyList(),
            today = today,
            weekStart = Weekday.MONDAY,
            trainingDaysPerWeek = 4,
            latestBodyweightKg = 80.2,
            liftBestKg = null,
        )
        assertTrue(weight.met)
    }

    private fun goal(kind: GoalKind, target: Double) = MeasurableGoal(
        id = "goal-${kind.name}",
        kind = kind,
        targetValue = target,
        period = GoalPeriod.WEEK,
        captured = captured,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )

    private fun projection(day: Long, sessions: Int) = DailyProjection(
        localEpochDay = day,
        sessionCount = sessions,
        workingSets = sessions,
        volumeKg = 0.0,
        activeMinutes = sessions * 30,
        cardioSeconds = 0L,
        cardioDistanceMeters = 0.0,
        sessionIds = List(sessions) { "s-$day-$it" },
    )
}
