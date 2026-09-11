package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals(listOf("ex-1"), summary.stills.map { it.id })
        assertEquals(listOf("ex-1"), entry.stills.map { it.id })
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
        assertTrue(cardioEntry.stills.isEmpty())

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
        assertEquals(listOf("ex-1"), liftSummary.stills.map { it.id })
    }

    @Test
    fun latestPrefersFinishedAtAndSurvivesAnEmptyWindow() {
        val older = summary(id = "old", date = 1_000L, finishedAt = 1_100L, volumeKg = 100.0)
        val newer = summary(id = "new", date = 500L, finishedAt = 2_000L, volumeKg = 200.0)
        assertEquals("new", listOf(older, newer).latest()?.id)
        assertEquals(null, emptyList<SessionSummary>().latest())
    }

    @Test
    fun homeWorkFallsBackWithoutInventingKilograms() {
        val loaded = summary(id = "v", volumeKg = 8000.0).homeWork(WeightUnit.KG)
        assertEquals("8000", loaded.value)
        assertEquals("kg", loaded.label)

        val bodyweight = summary(id = "bw", volumeKg = 0.0, workingSets = 24).homeWork(WeightUnit.KG)
        assertEquals("24", bodyweight.value)
        assertEquals("sets", bodyweight.label)

        val cardio = summary(id = "run", volumeKg = 0.0, cardioSeconds = 2_400L).homeWork(WeightUnit.KG)
        assertEquals("40", cardio.value)
        assertEquals("min", cardio.label)

        val empty = summary(id = "none", volumeKg = 0.0).homeWork(WeightUnit.KG)
        assertEquals(SetCopy.NOTHING_YET, empty.value)
        assertEquals(12L, summary(id = "gap", localEpochDay = 100).daysSince(112))
        assertEquals(0L, summary(id = "today", localEpochDay = 112).daysSince(112))
        assertTrue(summary(id = "work", volumeKg = 100.0).hasLoggedWork())
        assertFalse(summary(id = "none", volumeKg = 0.0).hasLoggedWork())
    }

    @Test
    fun lastSessionDeltaIsVersusThePreviousSessionOfTheSameRoutine() {
        val pushOld = summary(id = "p1", routineId = "push", finishedAt = 1_000L, volumeKg = 8_000.0)
        val pull = summary(id = "u1", routineId = "pull", finishedAt = 2_000L, volumeKg = 1_000.0)
        val pushNew = summary(id = "p2", routineId = "push", finishedAt = 3_000L, volumeKg = 9_000.0)
        val list = listOf(pushOld, pull, pushNew)
        val last = list.latest()!!
        assertEquals("p2", last.id)
        val previous = list.previousSameRoutine(last)
        assertEquals("p1", previous!!.id)
        assertEquals("+1000 kg", last.signedWorkDelta(previous, WeightUnit.KG))
        assertEquals("−1000 kg", pushOld.signedWorkDelta(pushNew, WeightUnit.KG))
        assertNull(listOf(pushNew).previousSameRoutine(pushNew))
        val cardio = summary(id = "run", routineId = null, volumeKg = 0.0)
        assertNull(listOf(cardio).previousSameRoutine(cardio))
        assertNull(pushNew.signedWorkDelta(pushNew.copy(id = "p3", volumeKg = 9_000.0), WeightUnit.KG))
        val setsLast = summary(
            id = "bw2",
            routineId = "bw",
            finishedAt = 2L,
            volumeKg = 0.0,
            workingSets = 30,
        )
        val setsPrev = summary(
            id = "bw1",
            routineId = "bw",
            finishedAt = 1L,
            volumeKg = 0.0,
            workingSets = 24,
        )
        assertEquals("+6 sets", setsLast.signedWorkDelta(setsPrev, WeightUnit.KG))
        assertNull(listOf(pushOld, pull).previousSameRoutine(pull))
    }

    private fun summary(
        id: String,
        date: Long = 1_000L,
        finishedAt: Long? = 1_100L,
        volumeKg: Double = 0.0,
        workingSets: Int = 0,
        cardioSeconds: Long = 0L,
        localEpochDay: Long = 10L,
        routineId: String? = null,
    ) = SessionSummary(
        id = id,
        routineId = routineId,
        routineName = id,
        date = date,
        finishedAt = finishedAt,
        durationMinutes = 40,
        workingSets = workingSets,
        volumeKg = volumeKg,
        localEpochDay = localEpochDay,
        cardioSeconds = cardioSeconds,
    )
}
