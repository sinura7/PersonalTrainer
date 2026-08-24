package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryTest {
    @Test
    fun workoutSummaryDropsTheSetGraphAndKeepsWork() {
        val session = WorkoutSession(
            id = "s1",
            routineId = "r1",
            routineName = "Push",
            date = 1_700_000_000_000L,
            notes = "",
            durationMinutes = 50,
            startedAt = 1_700_000_000_000L,
            finishedAt = 1_700_000_000_000L + 3_000_000L,
            exercises = listOf(
                SessionExercise(
                    id = "se1",
                    sessionId = "s1",
                    exercise = Exercise(
                        id = "ex-1",
                        name = "Bench",
                        muscleGroup = "Chest",
                        notes = "",
                        isCustom = false,
                    ),
                    sortOrder = 0,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = 100.0,
                    restSeconds = 90,
                ),
            ),
            sets = listOf(
                SetLog(
                    id = "set-1",
                    sessionId = "s1",
                    exerciseId = "ex-1",
                    exerciseName = "Bench",
                    setNumber = 1,
                    weightKg = 100.0,
                    reps = 5,
                    rpe = null,
                    isWarmup = false,
                    completedAt = 1_700_000_000_001L,
                ),
                SetLog(
                    id = "set-w",
                    sessionId = "s1",
                    exerciseId = "ex-1",
                    exerciseName = "Bench",
                    setNumber = 0,
                    weightKg = 60.0,
                    reps = 5,
                    rpe = null,
                    isWarmup = true,
                    completedAt = 1_700_000_000_000L,
                ),
            ),
        )
        val summary = session.toSummary()
        assertEquals(1, summary.workingSets)
        assertEquals(500.0, summary.volumeKg, 0.01)
        assertEquals(HistoryKind.WORKOUT, summary.kind)
        val stub = summary.toSessionStub()
        assertTrue(stub.sets.isEmpty())
        assertEquals("Push", stub.routineName)
        val entry = summary.toHistoryEntry()
        assertEquals(HistoryKind.WORKOUT, entry.kind)
        assertEquals("Push", entry.title)
        assertEquals(1, entry.workingSets)
    }

    @Test
    fun activitySummaryKeepsCardioAndMarksKind() {
        val activity = ActivitySession(
            id = "a1",
            status = ActivityStatus.COMPLETED,
            origin = ActivityOrigin.BACKDATED,
            source = ActivitySource.TEMPER,
            title = "Easy run",
            notes = "",
            performedStart = CapturedCivilTime(1_000L, "UTC", 0, 20_000L),
            performedEnd = CapturedCivilTime(1_000L + 2_400_000L, "UTC", 0, 20_000L),
            templateId = null,
            occurrenceId = null,
            blocks = listOf(
                CardioBlock(
                    id = "c1",
                    sortOrder = 0,
                    type = CardioType.RUN,
                    indoor = false,
                    elapsedSeconds = 2_400L,
                    movingSeconds = 2_400L,
                    distanceMeters = 6_000.0,
                    elevationMeters = null,
                    heartRateBpm = null,
                    energyKj = null,
                    rpe = 6,
                    routeRef = null,
                ),
            ),
            createdAtMs = 1L,
            updatedAtMs = 1L,
            revision = 1L,
        )
        val summary = activity.toSummary()
        assertEquals(HistoryKind.ACTIVITY, summary.kind)
        assertEquals(2_400L, summary.cardioSeconds)
        assertEquals(6_000.0, summary.cardioDistanceMeters!!, 0.01)
        assertEquals(0, summary.workingSets)
        val entry = summary.toHistoryEntry()
        assertEquals(HistoryKind.ACTIVITY, entry.kind)
        assertEquals("Easy run", entry.title)
        assertEquals(40, entry.cardioMinutes)
    }

    @Test
    fun untitledCardioFallsBackToCardioAndUntitledLiftFallsBackToWorkout() {
        val untitledRun = ActivitySession(
            id = "a2",
            status = ActivityStatus.COMPLETED,
            origin = ActivityOrigin.BACKDATED,
            source = ActivitySource.TEMPER,
            title = "  ",
            notes = "",
            performedStart = CapturedCivilTime(1_000L, "UTC", 0, 20_000L),
            performedEnd = CapturedCivilTime(1_000L + 600_000L, "UTC", 0, 20_000L),
            templateId = null,
            occurrenceId = null,
            blocks = listOf(
                CardioBlock(
                    id = "c2",
                    sortOrder = 0,
                    type = CardioType.WALK,
                    indoor = true,
                    elapsedSeconds = 600L,
                    movingSeconds = 600L,
                    distanceMeters = 0.0,
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
        val cardioEntry = untitledRun.toSummary().toHistoryEntry()
        assertEquals("Cardio", cardioEntry.title)
        assertEquals(10, cardioEntry.cardioMinutes)

        val untitledLift = ActivitySession(
            id = "a3",
            status = ActivityStatus.COMPLETED,
            origin = ActivityOrigin.BACKDATED,
            source = ActivitySource.TEMPER,
            title = "",
            notes = "",
            performedStart = CapturedCivilTime(2_000L, "UTC", 0, 20_001L),
            performedEnd = null,
            templateId = null,
            occurrenceId = null,
            blocks = listOf(
                StrengthBlock(
                    id = "s1",
                    sortOrder = 0,
                    exerciseId = "ex-1",
                    exerciseName = "Squat",
                    loadType = LoadType.EXTERNAL,
                    equipment = EquipmentType.BARBELL,
                    muscles = emptyList(),
                    sets = listOf(
                        StrengthSet(
                            id = "set-1",
                            setNumber = 1,
                            weightKg = 80.0,
                            reps = 5,
                            rpe = null,
                            isWarmup = false,
                            completedAtMs = 2_000L,
                        ),
                    ),
                ),
            ),
            createdAtMs = 1L,
            updatedAtMs = 1L,
            revision = 1L,
        )
        val liftSummary = untitledLift.toSummary()
        assertEquals(1, liftSummary.workingSets)
        assertEquals(400.0, liftSummary.volumeKg, 0.01)
        assertEquals(20_001L, liftSummary.localEpochDay)
        assertEquals("Workout", liftSummary.toHistoryEntry().title)
        assertEquals(0, liftSummary.toHistoryEntry().cardioMinutes)
    }
}
