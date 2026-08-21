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
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_30_DAYS, now, zone)
        val chest = snap.load(CanonicalMuscle.CHEST)
        assertEquals(500.0, chest.volumeKg, 0.001)
        assertEquals(1, chest.workingSets)
        assertEquals(1, chest.sessionCount)
        assertEquals(1, chest.exercises.size)
        assertEquals(500.0, chest.exercises.first().volumeKg, 0.001)
    }

    @Test
    fun bodyweightSetsAreCountedInRepsAndStillHeatTheMap() {
        val session = session(
            id = "s1",
            finishedAt = now - days(1),
            sets = listOf(set("a", "s1", "ex-pu", "Pull-Up", 0.0, 8, at = now - days(1))),
            exercises = listOf(sessionExercise("ex-pu", "Pull-Up", "Back", LoadType.BODYWEIGHT)),
        )
        val back = MuscleLoadCalculator
            .snapshot(listOf(session), HeatWindow.LAST_30_DAYS, now, zone)
            .load(CanonicalMuscle.BACK)

        // No kilograms, because there were none. This used to assert 320 — eight reps at a
        // flat 40 kg stand-in.
        assertEquals(0.0, back.volumeKg, 0.001)
        assertEquals(8, back.bodyweightReps)
        // The band never came from tonnage, so the map lights up exactly as it did before.
        assertEquals(1, back.workingSets)
        assertTrue(back.trainedInWindow)
    }

    @Test
    fun repsAreCreditedWholeWhileTonnageIsStillSplit() {
        // The two measures divide differently, on purpose. A secondary muscle takes a fraction
        // of a set's kilograms, because kilograms are a quantity. A rep is not: "2.4 reps of
        // glutes" is not a sentence anyone says, and it is not what happened either — the set
        // happened, and it trained all three.
        //
        // A weighted lift, so both numbers are non-zero at once and the contrast is visible.
        val session = session(
            id = "s1",
            finishedAt = now - days(1),
            sets = listOf(set("a", "s1", "ex-pistol", "Pistol Squat", 10.0, 6, at = now - days(1))),
            exercises = listOf(
                sessionExercise("ex-pistol", "Pistol Squat", "Legs", LoadType.BODYWEIGHT_PLUS),
            ),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_30_DAYS, now, zone)
        val quads = snap.load(CanonicalMuscle.QUADRICEPS)
        val glutes = snap.load(CanonicalMuscle.GLUTES)

        // Both muscles were trained by the same six reps, so both are credited with six.
        assertEquals(6, quads.bodyweightReps)
        assertEquals(6, glutes.bodyweightReps)
        // The vest's 60 kg lands whole on the primary and at the secondary weight elsewhere.
        assertEquals(60.0, quads.volumeKg, 0.001)
        assertEquals(60.0 * MuscleLoadCalculator.SECONDARY_VOLUME_WEIGHT, glutes.volumeKg, 0.001)
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
            finishedAt = now - days(40),
            sets = listOf(set("o", "old", "ex-squat", "Squat", 140.0, 5, at = now - days(40))),
            exercises = listOf(sessionExercise("ex-squat", "Squat", "Quads")),
        )
        // A set outside the window is excluded from volume while still counting for
        // "how long since" — recency reads all of history, not just the window.
        val snap = MuscleLoadCalculator.snapshot(listOf(recent, old), HeatWindow.LAST_30_DAYS, now, zone)
        val quads = snap.load(CanonicalMuscle.QUADRICEPS)
        assertEquals(400.0, quads.volumeKg, 0.001)
        assertEquals(2, quads.daysSinceLastTrained)
        assertTrue(snap.hasWindowWorkingSets)
        assertTrue(snap.hasAnyWorkingSets)
    }

    @Test
    fun heatIsAbsoluteNotRelativeToTheHardestMuscle() {
        // One set of each. Under the old relative rule the chest was fully hot for being the
        // heavier of two, and the back sat at half — a picture of "which did more", not of
        // "is either enough". Absolutely, one set a week is untrained on both.
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
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.CURRENT_WEEK, now, zone)
        assertEquals(0.0, snap.load(CanonicalMuscle.CHEST).heat, 0.001)
        assertEquals(0.0, snap.load(CanonicalMuscle.BACK).heat, 0.001)
        assertEquals(HeatBand.UNTRAINED, snap.load(CanonicalMuscle.CHEST).band)
        assertEquals(HeatBand.UNTRAINED, snap.load(CanonicalMuscle.BACK).band)
        assertEquals(HeatBand.UNTRAINED, snap.load(CanonicalMuscle.CORE).band)

        // …and the hardest-worked muscle no longer drags everything else down: adding a huge
        // leg day leaves the chest exactly where it was.
        val legs = session(
            id = "s2",
            finishedAt = now - days(1),
            sets = List(24) { index ->
                set("l$index", "s2", "ex-squat", "Squat", 200.0, 5, at = now - days(1))
            },
            exercises = listOf(sessionExercise("ex-squat", "Squat", "Quads")),
        )
        val withLegs = MuscleLoadCalculator.snapshot(
            listOf(session, legs),
            HeatWindow.CURRENT_WEEK,
            now,
            zone,
        )
        assertEquals(
            snap.load(CanonicalMuscle.CHEST).heat,
            withLegs.load(CanonicalMuscle.CHEST).heat,
            0.001,
        )
        assertEquals(HeatBand.HIGH, withLegs.load(CanonicalMuscle.QUADRICEPS).band)
    }

    @Test
    fun secondaryVolumeIsDiscounted() {
        val session = session(
            id = "s1",
            finishedAt = now - days(1),
            sets = listOf(set("d", "s1", "ex-dl", "Deadlift", 150.0, 4, at = now - days(1))),
            exercises = listOf(sessionExercise("ex-dl", "Deadlift", "Posterior chain")),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_30_DAYS, now, zone)
        val primary = 150.0 * 4
        assertEquals(primary, snap.load(CanonicalMuscle.BACK).volumeKg, 0.001)
        assertEquals(primary * MuscleLoadCalculator.SECONDARY_VOLUME_WEIGHT, snap.load(CanonicalMuscle.HAMSTRINGS).volumeKg, 0.001)
        assertEquals(primary * MuscleLoadCalculator.SECONDARY_VOLUME_WEIGHT, snap.load(CanonicalMuscle.GLUTES).volumeKg, 0.001)
        // One set is one set however heavy it was: heat is set count now, not tonnage share.
        assertEquals(0.0, snap.load(CanonicalMuscle.BACK).heat, 0.001)
    }

    @Test
    fun inProgressSessionsDoNotCount() {
        val open = session(
            id = "open",
            finishedAt = null,
            sets = listOf(set("x", "open", "ex-bench", "Bench", 100.0, 5, at = now)),
            exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
        )
        val snap = MuscleLoadCalculator.snapshot(listOf(open), HeatWindow.LAST_30_DAYS, now, zone)
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
        val snap = MuscleLoadCalculator.snapshot(listOf(session), HeatWindow.LAST_30_DAYS, now, zone)
        assertEquals(200.0, snap.load(CanonicalMuscle.OTHER).volumeKg, 0.001)
        // Off-map work is still counted, and one set of it is still one set: the old relative
        // rule painted this fully hot for being the only thing in the window.
        assertEquals(0.0, snap.load(CanonicalMuscle.OTHER).heat, 0.001)
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
    fun heatFractionIsPiecewiseAndMonotonic() {
        // The anchors the silhouette's ramp is built around.
        assertEquals(0.0, MuscleLoadCalculator.heatFraction(0.0), 0.0)
        assertEquals(0.0, MuscleLoadCalculator.heatFraction(3.99), 0.0)
        assertEquals(MuscleLoadCalculator.FRACTION_LOW, MuscleLoadCalculator.heatFraction(4.0), 1e-9)
        assertEquals(
            MuscleLoadCalculator.FRACTION_PRODUCTIVE,
            MuscleLoadCalculator.heatFraction(10.0),
            1e-9,
        )
        // Flat across the whole productive band, so "enough" is one recognisable colour.
        assertEquals(
            MuscleLoadCalculator.heatFraction(10.0),
            MuscleLoadCalculator.heatFraction(20.0),
            1e-9,
        )
        assertEquals(1.0, MuscleLoadCalculator.heatFraction(30.0), 1e-9)
        assertEquals(1.0, MuscleLoadCalculator.heatFraction(100.0), 1e-9)

        var previous = -1.0
        var sets = 0.0
        while (sets <= 40.0) {
            val value = MuscleLoadCalculator.heatFraction(sets)
            assertTrue("heat must never fall as sets rise (at $sets)", value >= previous)
            previous = value
            sets += 0.25
        }
    }

    @Test
    fun thirtyDayWindowIsAveragedToAWeek() {
        // The two chips have to be the same unit or the bands mean different things on each.
        assertEquals(
            10.0,
            MuscleLoadCalculator.weeklySetsFor(10.0, HeatWindow.CURRENT_WEEK),
            1e-9,
        )
        assertEquals(
            30.0 * 7.0 / 30.0,
            MuscleLoadCalculator.weeklySetsFor(30.0, HeatWindow.LAST_30_DAYS),
            1e-9,
        )
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

internal fun sessionExercise(
    exerciseId: String,
    name: String,
    muscleGroup: String,
    loadType: LoadType = LoadType.EXTERNAL,
): SessionExercise =
    SessionExercise(
        id = "se-$exerciseId",
        sessionId = "s",
        exercise = Exercise(exerciseId, name, muscleGroup, "", false, loadType = loadType),
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
