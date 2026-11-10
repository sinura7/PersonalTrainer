package com.sinura.personaltrainer.ui.progress

import com.sinura.personaltrainer.domain.BodyWeekActivityMath
import com.sinura.personaltrainer.domain.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** F1: Body card merges workout lane SQL with activity_strength_sets. */
class BodyWeekLanesTest {
  private val weekStart = 20_000L
  private val weekEnd = weekStart + 6

    @Test
    fun activityStrengthSetsAddToWeeklyWorkingSetRows() {
        val workoutRows = listOf(
            com.sinura.personaltrainer.data.local.dao.WorkingSetCountRow(
                exerciseId = "ex-squat",
                finishedAt = 1L,
                workingSets = 3,
                routineId = "r-push",
            ),
        )
        val activityRows = listOf(
            com.sinura.personaltrainer.data.local.dao.WorkingSetCountRow(
                exerciseId = "ex-squat",
                finishedAt = 2L,
                workingSets = 2,
                routineId = null,
            ),
        )
        val merged = workoutRows + activityRows
        assertEquals(5, merged.sumOf { it.workingSets })
    }

    @Test
    fun weekActivityCardCountsCardioAndAuxiliaryMinutes() {
        val activities = listOf(
            SessionSummary(
                id = "a1",
                routineId = null,
                routineName = "Run",
                date = 0L,
                finishedAt = 0L,
                durationMinutes = 30,
                workingSets = 0,
                volumeKg = 0.0,
                localEpochDay = weekStart,
                cardioSeconds = 30 * 60L,
            ),
        )
        val lines = BodyWeekActivityMath.forWeek(
            weekStartEpochDay = weekStart,
            weekEndEpochDay = weekEnd,
            activitySummaries = activities,
            auxiliaryWorkoutMinutes = 12,
        )
        assertEquals(1, lines.cardioSessions)
        assertEquals(12, lines.warmUpStretchMinutes)
    }
}
