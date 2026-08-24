package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCopyTest {
    private val captured = CapturedCivilTime(
        instantMillis = 1L,
        zoneId = "UTC",
        offsetSeconds = 0,
        localEpochDay = 20_000L,
    )

    @Test
    fun featuredPrefersAnActiveGoalOverAPausedOne() {
        val paused = snapshot(GoalKind.SESSION_COUNT, paused = true)
        val active = snapshot(GoalKind.ACTIVE_MINUTES, paused = false)
        val featured = GoalCopy.featured(listOf(paused, active))
        assertEquals(GoalKind.ACTIVE_MINUTES, featured!!.goal.kind)
        assertFalse(featured.goal.paused)
    }

    @Test
    fun formatsEachKindWithoutStreakLanguage() {
        assertEquals("80%", GoalCopy.formatCurrent(measured(GoalKind.ADHERENCE, 0.8, 1.0)))
        assertEquals("3 sessions", GoalCopy.formatCurrent(measured(GoalKind.SESSION_COUNT, 3.0, 4.0)))
        assertEquals("90 min", GoalCopy.formatCurrent(measured(GoalKind.ACTIVE_MINUTES, 90.0, 120.0)))
        assertEquals("5 km", GoalCopy.formatCurrent(measured(GoalKind.CARDIO_DISTANCE, 5_000.0, 10_000.0)))
        assertEquals("400 m", GoalCopy.formatCurrent(measured(GoalKind.CARDIO_DISTANCE, 400.0, 800.0)))
        assertEquals("25 min", GoalCopy.formatCurrent(measured(GoalKind.CARDIO_DURATION, 25.0, 40.0)))
        val lift = GoalCopy.formatCurrent(measured(GoalKind.LIFT_TARGET, 100.0, 110.0))
        assertTrue(lift.contains("100"))
        val weight = GoalCopy.formatCurrent(measured(GoalKind.BODYWEIGHT, 80.0, 78.0))
        assertTrue(weight.contains("80"))
    }

    @Test
    fun featuredReturnsNullWhenThereAreNoSnapshots() {
        assertEquals(null, GoalCopy.featured(emptyList()))
    }

    @Test
    fun featuredFallsBackToAPausedGoalWhenNothingIsActive() {
        val paused = snapshot(GoalKind.SESSION_COUNT, paused = true)
        assertEquals(paused.goal.id, GoalCopy.featured(listOf(paused))!!.goal.id)
    }

    @Test
    fun progressLineNamesPausedWithoutCallingItAStreak() {
        val line = GoalCopy.progressLine(snapshot(GoalKind.SESSION_COUNT, paused = true))
        assertTrue(line.startsWith("Paused"))
        assertFalse(line.contains("streak", ignoreCase = true))
    }

    @Test
    fun progressLineAndTargetNameTheActiveGoalInTheChosenUnit() {
        val snap = measured(GoalKind.LIFT_TARGET, 100.0, 110.0)
        val kg = GoalCopy.progressLine(snap, WeightUnit.KG)
        assertTrue(kg.contains("of"))
        assertTrue(kg.contains("kg"))
        assertFalse(kg.contains("streak", ignoreCase = true))
        val lbs = GoalCopy.formatTarget(snap.goal, WeightUnit.LBS)
        assertTrue(lbs.contains("lbs"))
        assertEquals(
            GoalCopy.formatCurrent(measured(GoalKind.ADHERENCE, 0.8, 1.0)),
            GoalCopy.formatTarget(measured(GoalKind.ADHERENCE, 0.8, 1.0).goal.copy(targetValue = 0.8)),
        )
        val bodyLbs = GoalCopy.formatCurrent(measured(GoalKind.BODYWEIGHT, 80.0, 78.0), WeightUnit.LBS)
        assertTrue(bodyLbs.contains("lbs"))
    }

    private fun measured(kind: GoalKind, current: Double, target: Double) = GoalSnapshot(
        goal = MeasurableGoal(
            id = "g-${kind.name}",
            kind = kind,
            targetValue = target,
            period = GoalPeriod.WEEK,
            captured = captured,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
        currentValue = current,
        ratio = if (target <= 0.0) 0.0 else current / target,
        met = current + 1e-6 >= target,
    )

    private fun snapshot(kind: GoalKind, paused: Boolean) = GoalSnapshot(
        goal = MeasurableGoal(
            id = "g-${kind.name}",
            kind = kind,
            targetValue = 4.0,
            period = GoalPeriod.WEEK,
            captured = captured,
            paused = paused,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
        currentValue = 1.0,
        ratio = 0.25,
        met = false,
    )
}
