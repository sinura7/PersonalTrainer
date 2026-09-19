package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFloorStatsTest {
    private val unit = WeightUnit.LBS
    private val kg70 = WeightConverter.lbsToKg(70.0)
    private val kg90 = WeightConverter.lbsToKg(90.0)

    @Test
    fun lastSetIsTheLatestSavedRowToday() {
        val session = session(
            sets = listOf(
                set(number = 1, weightKg = kg70, reps = 10, rpe = 8, at = 10L),
                set(number = 2, weightKg = kg70, reps = 10, rpe = 9, at = 20L),
            ),
        )
        val stats = ExerciseFloorStatsCalculator.of(session = session, exerciseId = "leg-ext", lastPerformance = null, priorHistory = emptyList(), unit = unit)
        assertEquals("Last set", stats.lastSet.label)
        assertEquals("70 × 10 @ 9", stats.lastSet.value)
        assertNull(stats.lastSet.detail)
        assertEquals("Last set, 70 lbs × 10, RPE 9", stats.lastSet.spoken)
    }

    @Test
    fun lastSetFallsBackToLastTimeThenFirstSet() {
        val previous = ExerciseSessionSummary(
            sessionId = "old",
            sessionName = "Lower B",
            performedAtMs = 1L,
            topSet = null,
            workingSets = 2,
            volumeKg = 0.0,
            estimatedOneRepMaxKg = null,
            sets = listOf(
                ExerciseSetRecord(setId = "a", sessionId = "old", weightKg = kg70, reps = 8, completedAt = 1L, rpe = 7),
                ExerciseSetRecord(setId = "b", sessionId = "old", weightKg = kg70, reps = 9, completedAt = 2L, rpe = 8),
            ),
        )
        val withHistory = ExerciseFloorStatsCalculator.of(session = session(sets = emptyList()), exerciseId = "leg-ext", lastPerformance = previous, priorHistory = emptyList(), unit = unit)
        assertEquals("70 × 9 @ 8", withHistory.lastSet.value)
        assertEquals("Last time", withHistory.lastSet.detail)
        assertEquals(previous.sets.last(), withHistory.lastSet.applies)
        val heldBefore = previous.copy(sets = listOf(ExerciseSetRecord(setId = "h", sessionId = "old", weightKg = 0.0, reps = 0, completedAt = 3L)))
        val hold = ExerciseFloorStatsCalculator.of(session = session(sets = emptyList()), exerciseId = "leg-ext", lastPerformance = heldBefore, priorHistory = emptyList(), unit = unit)
        assertEquals("Hold", hold.lastSet.value)
        assertNull(hold.lastSet.applies)
        val fresh = ExerciseFloorStatsCalculator.of(session = session(sets = emptyList()), exerciseId = "leg-ext", lastPerformance = null, priorHistory = emptyList(), unit = unit)
        assertEquals("First set", fresh.lastSet.value)
        assertNull(fresh.lastSet.applies)
        assertEquals("—", fresh.bestSet.value)
        assertTrue(fresh.work.isEmpty)
    }

    @Test
    fun warmupLastSetIsMarkedAndExcludedFromVolumeAndBest() {
        val session = session(
            sets = listOf(
                set(number = 1, weightKg = kg70, reps = 10, at = 10L),
                set(number = 2, weightKg = kg90, reps = 5, at = 20L, warmup = true),
            ),
        )
        val stats = ExerciseFloorStatsCalculator.of(session = session, exerciseId = "leg-ext", lastPerformance = null, priorHistory = emptyList(), unit = unit)
        assertEquals("Warm-up", stats.lastSet.detail)
        assertEquals("90 × 5", stats.lastSet.value)
        assertEquals("70 × 10", stats.bestSet.value)
        assertEquals("700", stats.volumeColumn(unit).value)
        assertEquals("lbs", stats.volumeColumn(unit).label)
    }

    @Test
    fun bestSetUsesTheStandingRecordAcrossHistoryAndToday() {
        val history = listOf(
            ExerciseSetRecord(setId = "h1", sessionId = "old", weightKg = kg90, reps = 8, completedAt = 1L),
            ExerciseSetRecord(setId = "h2", sessionId = "old", weightKg = kg70, reps = 12, completedAt = 2L),
        )
        val session = session(sets = listOf(set(number = 1, weightKg = kg70, reps = 10, at = 10L)))
        val stats = ExerciseFloorStatsCalculator.of(session = session, exerciseId = "leg-ext", lastPerformance = null, priorHistory = history, unit = unit)
        // Loaded lifts rank by estimated one-rep max: 90 × 8 beats 70 × 12 and today's 70 × 10.
        assertEquals("90 × 8", stats.bestSet.value)
        assertEquals("Est. 1RM", stats.bestSet.detail)
        val brokenToday = session(sets = listOf(set(number = 1, weightKg = WeightConverter.lbsToKg(100.0), reps = 8, at = 10L)))
        val today = ExerciseFloorStatsCalculator.of(session = brokenToday, exerciseId = "leg-ext", lastPerformance = null, priorHistory = history, unit = unit)
        assertEquals("100 × 8", today.bestSet.value)
        assertEquals("Today", today.bestSet.detail)
        assertTrue(today.bestSet.spoken.endsWith("set today"))
    }

    @Test
    fun bodyweightLiftsReadRepsAndVolumeInReps() {
        val session = session(
            loadType = LoadType.BODYWEIGHT,
            sets = listOf(set(number = 1, weightKg = 0.0, reps = 12, rpe = 8, at = 10L), set(number = 2, weightKg = 0.0, reps = 14, at = 20L)),
        )
        val stats = ExerciseFloorStatsCalculator.of(session = session, exerciseId = "leg-ext", lastPerformance = null, priorHistory = emptyList(), unit = unit)
        assertEquals("14 reps", stats.lastSet.value)
        assertEquals("14 reps", stats.bestSet.value)
        assertEquals("Most reps", stats.bestSet.detail)
        assertEquals("26", stats.volumeColumn(unit).value)
        assertEquals("reps", stats.volumeColumn(unit).label)
    }

    @Test
    fun compactLinesCoverEveryLoadClass() {
        assertEquals("70 × 10 @ 9", FloorStatCopy.compactSet(weightKg = kg70, reps = 10, loadClass = LoadClass.LOADED, unit = unit, rpe = 9))
        assertEquals("10 reps", FloorStatCopy.compactSet(weightKg = 0.0, reps = 10, loadClass = LoadClass.BODYWEIGHT, unit = unit))
        assertEquals("10 reps +20 @ 8", FloorStatCopy.compactSet(weightKg = WeightConverter.lbsToKg(20.0), reps = 10, loadClass = LoadClass.BODYWEIGHT_ADDED, unit = unit, rpe = 8))
        assertEquals("10 reps −20", FloorStatCopy.compactSet(weightKg = WeightConverter.lbsToKg(20.0), reps = 10, loadClass = LoadClass.BODYWEIGHT_ASSISTED, unit = unit))
        assertEquals("0:30", FloorStatCopy.compactSet(weightKg = 0.0, reps = 0, loadClass = LoadClass.BODYWEIGHT, unit = unit, durationSeconds = 30))
        assertEquals("20 × 0:30", FloorStatCopy.compactSet(weightKg = WeightConverter.lbsToKg(20.0), reps = 0, loadClass = LoadClass.LOADED, unit = unit, durationSeconds = 30))
        assertEquals("10 reps", FloorStatCopy.compactSet(weightKg = 0.0, reps = 10, loadClass = LoadClass.LOADED, unit = unit))
    }

    private fun session(sets: List<SetLog>, loadType: LoadType = LoadType.EXTERNAL) = WorkoutSession(
        id = "s1",
        routineId = null,
        routineName = "Lower B",
        date = 1L,
        notes = "",
        durationMinutes = 0,
        startedAt = 1L,
        finishedAt = null,
        exercises = listOf(
            SessionExercise(
                id = "se-1",
                sessionId = "s1",
                exercise = Exercise(
                    id = "leg-ext", name = "Leg Extension", muscleGroup = "Legs", notes = "", isCustom = false,
                    equipment = EquipmentType.MACHINE, loadType = loadType,
                ),
                sortOrder = 0,
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = kg70,
                restSeconds = 120,
            ),
        ),
        sets = sets,
    )

    private fun set(number: Int, weightKg: Double, reps: Int, rpe: Int? = null, at: Long, warmup: Boolean = false) = SetLog(
        id = "set-$number",
        sessionId = "s1",
        exerciseId = "leg-ext",
        exerciseName = "Leg Extension",
        setNumber = number,
        weightKg = weightKg,
        reps = reps,
        rpe = rpe,
        isWarmup = warmup,
        completedAt = at,
    )
}
