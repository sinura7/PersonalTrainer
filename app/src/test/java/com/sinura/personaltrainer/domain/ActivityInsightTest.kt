package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityInsightTest {
    @Test
    fun windowedHistoryDropsActivitiesOlderThanTheCutoff() {
        val recent = strengthActivity(id = "recent", at = 10_000L)
        val old = strengthActivity(id = "old", at = 1_000L)
        val kept = windowedInsightHistory(
            sessions = emptyList(),
            activities = listOf(recent, old),
            minPerformedAtMs = 5_000L,
        )
        assertEquals(listOf("recent"), kept.map { it.id })
    }

    @Test
    fun windowedHistoryKeepsWorkoutsAndIgnoresCardioOnlyActivities() {
        val workout = WorkoutSession(
            id = "w1",
            routineId = null,
            routineName = "Push",
            date = 8_000L,
            notes = "",
            durationMinutes = 40,
            startedAt = 8_000L,
            finishedAt = 8_100L,
            exercises = emptyList(),
            sets = emptyList(),
        )
        val run = ActivitySession(
            id = "run",
            status = ActivityStatus.COMPLETED,
            origin = ActivityOrigin.BACKDATED,
            source = ActivitySource.TEMPER,
            title = "Easy run",
            notes = "",
            performedStart = CapturedCivilTime(9_000L, "UTC", 0, 20_000L),
            performedEnd = CapturedCivilTime(9_600L, "UTC", 0, 20_000L),
            templateId = null,
            occurrenceId = null,
            blocks = listOf(
                CardioBlock(
                    id = "c1",
                    sortOrder = 0,
                    type = CardioType.RUN,
                    indoor = false,
                    elapsedSeconds = 600L,
                    movingSeconds = 600L,
                    distanceMeters = 1_000.0,
                    elevationMeters = null,
                    heartRateBpm = null,
                    energyKj = null,
                    rpe = null,
                    routeRef = null,
                ),
            ),
            createdAtMs = 1L,
            updatedAtMs = 1L,
            revision = 1L,
        )
        val kept = windowedInsightHistory(
            sessions = listOf(workout),
            activities = listOf(run),
            minPerformedAtMs = 1L,
        )
        assertEquals(listOf("w1"), kept.map { it.id })
        assertTrue(run.toInsightSession() == null)
    }

    private fun strengthActivity(id: String, at: Long): ActivitySession = ActivitySession(
        id = id,
        status = ActivityStatus.COMPLETED,
        origin = ActivityOrigin.BACKDATED,
        source = ActivitySource.TEMPER,
        title = "Mixed",
        notes = "",
        performedStart = CapturedCivilTime(at, "UTC", 0, 20_000L),
        performedEnd = CapturedCivilTime(at + 1_000L, "UTC", 0, 20_000L),
        templateId = null,
        occurrenceId = null,
        blocks = listOf(
            StrengthBlock(
                id = "s-$id",
                sortOrder = 0,
                exerciseId = "ex-1",
                exerciseName = "Squat",
                loadType = LoadType.EXTERNAL,
                equipment = EquipmentType.BARBELL,
                muscles = emptyList(),
                sets = listOf(
                    StrengthSet(
                        id = "set-$id",
                        setNumber = 1,
                        weightKg = 80.0,
                        reps = 5,
                        rpe = null,
                        isWarmup = false,
                        completedAtMs = at,
                    ),
                ),
            ),
        ),
        createdAtMs = 1L,
        updatedAtMs = 1L,
        revision = 1L,
    )
}
