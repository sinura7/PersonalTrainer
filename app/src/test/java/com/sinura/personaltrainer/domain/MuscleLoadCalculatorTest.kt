package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class MuscleLoadCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L

    @Test
    fun workingVolumeIgnoresWarmups() {
        val session = session(
            id = "s1",
            finishedAt = now - days(1),
            sets = listOf(
                set("a", "s1", "ex-bench", "Bench", 100.0, 5, warmup = false, at = now - days(1)),
                set("b", "s1", "ex-bench", "Bench", 60.0, 8, warmup = true, at = now - days(1)),
            ),
            exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_7_DAYS, now, zone)
        val chest = snap.load(CanonicalMuscle.CHEST)
        assertEquals(500.0, chest.volumeKg, 0.001)
        assertEquals(1, chest.workingSets)
        assertEquals(1, chest.sessionCount)
        assertEquals(1, chest.exercises.size)
        assertEquals(500.0, chest.exercises.first().volumeKg, 0.001)
    }

    @Test
    fun bodyweightSetsUseEquivalentLoad() {
        val session = session(
            id = "s1",
            finishedAt = now - days(1),
            sets = listOf(set("a", "s1", "ex-pu", "Pull-Up", 0.0, 8, at = now - days(1))),
            exercises = listOf(sessionExercise("ex-pu", "Pull-Up", "Back")),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_7_DAYS, now, zone)
        val expected = MuscleLoadCalculator.BODYWEIGHT_EQUIVALENT_KG * 8
        assertEquals(expected, snap.load(CanonicalMuscle.BACK).volumeKg, 0.001)
    }

    @Test
    fun windowExcludesOlderSetsAndKeepsLifetimeRecency() {
        val recent = session(
            id = "recent",
            finishedAt = now - days(2),
            sets = listOf(set("r", "recent", "ex-squat", "Squat", 80.0, 5, at = now - days(2))),
            exercises = listOf(sessionExercise("ex-squat", "Squat", "Quads")),
        )
        val old = session(
            id = "old",
            finishedAt = now - days(20),
            sets = listOf(set("o", "old", "ex-squat", "Squat", 140.0, 5, at = now - days(20))),
            exercises = listOf(sessionExercise("ex-squat", "Squat", "Quads")),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(recent, old), HeatWindow.LAST_7_DAYS, now, zone)
        val quads = snap.load(CanonicalMuscle.QUADRICEPS)
        assertEquals(400.0, quads.volumeKg, 0.001)
        assertEquals(2, quads.daysSinceLastTrained)
        assertTrue(snap.hasWindowWorkingSets)
        assertTrue(snap.hasAnyWorkingSets)
    }

    @Test
    fun heatIsNormalizedToUsersOwnMax() {
        val session = session(
            id = "s1",
            finishedAt = now - days(1),
            sets = listOf(
                set("c", "s1", "ex-bench", "Bench", 100.0, 5, at = now - days(1)),
                set("b", "s1", "ex-row", "Row", 50.0, 5, at = now - days(1)),
            ),
            exercises = listOf(
                sessionExercise("ex-bench", "Bench", "Chest"),
                sessionExercise("ex-row", "Row", "Back"),
            ),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_7_DAYS, now, zone)
        assertEquals(1.0, snap.load(CanonicalMuscle.CHEST).heat, 0.001)
        assertEquals(0.5, snap.load(CanonicalMuscle.BACK).heat, 0.001)
        assertEquals(0.0, snap.load(CanonicalMuscle.CORE).heat, 0.001)
        assertEquals(HeatBand.HIGH, snap.load(CanonicalMuscle.CHEST).band)
        assertEquals(HeatBand.MODERATE, snap.load(CanonicalMuscle.BACK).band)
        assertEquals(HeatBand.NONE, snap.load(CanonicalMuscle.CORE).band)
    }

    @Test
    fun secondaryVolumeIsDiscounted() {
        val session = session(
            id = "s1",
            finishedAt = now - days(1),
            sets = listOf(set("d", "s1", "ex-dl", "Deadlift", 150.0, 4, at = now - days(1))),
            exercises = listOf(sessionExercise("ex-dl", "Deadlift", "Posterior chain")),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_7_DAYS, now, zone)
        val primary = 150.0 * 4
        assertEquals(primary, snap.load(CanonicalMuscle.BACK).volumeKg, 0.001)
        assertEquals(primary * MuscleLoadCalculator.SECONDARY_VOLUME_WEIGHT, snap.load(CanonicalMuscle.HAMSTRINGS).volumeKg, 0.001)
        assertEquals(primary * MuscleLoadCalculator.SECONDARY_VOLUME_WEIGHT, snap.load(CanonicalMuscle.GLUTES).volumeKg, 0.001)
        assertEquals(1.0, snap.load(CanonicalMuscle.BACK).heat, 0.001)
    }

    @Test
    fun inProgressSessionsDoNotCount() {
        val open = session(
            id = "open",
            finishedAt = null,
            sets = listOf(set("x", "open", "ex-bench", "Bench", 100.0, 5, at = now)),
            exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(open), HeatWindow.LAST_7_DAYS, now, zone)
        assertFalse(snap.hasAnyWorkingSets)
        assertEquals(0.0, snap.load(CanonicalMuscle.CHEST).volumeKg, 0.001)
        assertNull(snap.load(CanonicalMuscle.CHEST).daysSinceLastTrained)
    }

    @Test
    fun missingMuscleLabelFallsBackToOther() {
        val session = session(
            id = "s1",
            finishedAt = now - days(1),
            sets = listOf(set("n", "s1", "ex-neck", "Neck Curl", 20.0, 10, at = now - days(1))),
            exercises = listOf(sessionExercise("ex-neck", "Neck Curl", "")),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_7_DAYS, now, zone)
        assertEquals(200.0, snap.load(CanonicalMuscle.OTHER).volumeKg, 0.001)
        assertEquals(1.0, snap.load(CanonicalMuscle.OTHER).heat, 0.001)
    }

    @Test
    fun currentWeekStartsMonday() {
        // Friday 2024-01-05 12:00 UTC. Week starts Monday 2024-01-01.
        val friday = 1_704_456_000_000L
        val mondaySet = friday - days(4)
        val priorSunday = friday - days(5)
        val inWeek = session(
            id = "in",
            finishedAt = mondaySet,
            sets = listOf(set("i", "in", "ex-bench", "Bench", 100.0, 1, at = mondaySet)),
            exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
        )
        val before = session(
            id = "out",
            finishedAt = priorSunday,
            sets = listOf(set("o", "out", "ex-bench", "Bench", 200.0, 1, at = priorSunday)),
            exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(inWeek, before), HeatWindow.CURRENT_WEEK, friday, zone)
        assertEquals(100.0, snap.load(CanonicalMuscle.CHEST).volumeKg, 0.001)
    }

    @Test
    fun normalizeHeatHandlesZeroMax() {
        assertEquals(0.0, MuscleLoadCalculator.normalizeHeat(10.0, 0.0), 0.0)
        assertEquals(0.0, MuscleLoadCalculator.normalizeHeat(0.0, 100.0), 0.0)
        assertEquals(1.0, MuscleLoadCalculator.normalizeHeat(50.0, 50.0), 0.0)
        assertEquals(0.25, MuscleLoadCalculator.normalizeHeat(25.0, 100.0), 0.0)
    }

    private fun days(count: Long): Long = count * 24L * 60L * 60L * 1000L
}

internal fun session(
    id: String,
    finishedAt: Long?,
    sets: List<SetLog>,
    exercises: List<SessionExercise>,
    date: Long = finishedAt ?: 0L,
): WorkoutSession = WorkoutSession(
    id = id,
    routineId = null,
    routineName = "Test",
    date = date,
    notes = "",
    durationMinutes = 40,
    startedAt = date,
    finishedAt = finishedAt,
    exercises = exercises,
    sets = sets,
)

internal fun sessionExercise(exerciseId: String, name: String, muscleGroup: String): SessionExercise =
    SessionExercise(
        id = "se-$exerciseId",
        sessionId = "s",
        exercise = Exercise(exerciseId, name, muscleGroup, "", false),
        sortOrder = 0,
        targetSets = 3,
        targetReps = 5,
        targetWeightKg = null,
        restSeconds = 90,
    )

internal fun set(
    id: String,
    sessionId: String,
    exerciseId: String,
    name: String,
    weightKg: Double,
    reps: Int,
    warmup: Boolean = false,
    at: Long,
): SetLog = SetLog(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    exerciseName = name,
    setNumber = 1,
    weightKg = weightKg,
    reps = reps,
    rpe = null,
    isWarmup = warmup,
    completedAt = at,
)
