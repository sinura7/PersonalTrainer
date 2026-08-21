package com.sinura.personaltrainer.domain

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v2 heat model, and the fallbacks that keep the Body tab honest while it is arriving.
 *
 * The whole reason the junction exists is that a single muscle-group string could not say what a
 * deadlift does. These tests pin the new answer, and pin that a lift WITHOUT junction data still
 * gets exactly the old one — which is what makes the window between the migration and the first
 * seed pass a non-event rather than a body map that has gone blank.
 */
class JunctionHeatTest {

    @Test
    fun junctionCreditsDriveHeatWhenPresent() {
        val catalog = mapOf(
            DEADLIFT to exerciseWithCredits(
                id = DEADLIFT,
                name = "Conventional Deadlift",
                muscleGroup = "Posterior chain",
                credits = listOf(
                    MuscleCredit("glutes", 1.0),
                    MuscleCredit("hamstrings", 0.5),
                    MuscleCredit("back", 0.5),
                ),
            ),
        )
        val snapshot = snapshotOf(
            sessions = listOf(
                session(
                    id = "s1",
                    finishedAt = NOW - HOUR,
                    sets = listOf(
                        set(
                            id = "set-1", sessionId = "s1", exerciseId = DEADLIFT,
                            name = "Conventional Deadlift", weightKg = 100.0, reps = 5,
                            at = NOW - HOUR,
                        ),
                    ),
                    exercises = listOf(sessionExercise(DEADLIFT, "Conventional Deadlift", "Posterior chain")),
                ),
            ),
            catalog = catalog,
        )
        // 100 kg x 5 = 500 kg of set volume, split by the junction weights.
        assertEquals(500.0, volumeOf(snapshot, CanonicalMuscle.GLUTES), EPSILON)
        assertEquals(250.0, volumeOf(snapshot, CanonicalMuscle.HAMSTRINGS), EPSILON)
        assertEquals(250.0, volumeOf(snapshot, CanonicalMuscle.BACK), EPSILON)
    }

    @Test
    fun catalogJunctionWinsOverEmbeddedMuscleGroup() {
        val catalog = mapOf(
            "ex-odd" to exerciseWithCredits(
                id = "ex-odd",
                name = "Reclassified Lift",
                muscleGroup = "Back",
                credits = listOf(MuscleCredit("back", 1.0)),
            ),
        )
        val snapshot = snapshotOf(
            sessions = listOf(
                session(
                    id = "s1",
                    finishedAt = NOW - HOUR,
                    sets = listOf(
                        set(
                            id = "set-1", sessionId = "s1", exerciseId = "ex-odd",
                            name = "Reclassified Lift", weightKg = 50.0, reps = 10, at = NOW - HOUR,
                        ),
                    ),
                    // The session still remembers this lift as a chest exercise.
                    exercises = listOf(sessionExercise("ex-odd", "Reclassified Lift", "Chest")),
                ),
            ),
            catalog = catalog,
        )
        assertEquals(500.0, volumeOf(snapshot, CanonicalMuscle.BACK), EPSILON)
        assertEquals(0.0, volumeOf(snapshot, CanonicalMuscle.CHEST), EPSILON)
    }

    @Test
    fun fallsBackToMuscleGroupDerivationWithoutJunction() {
        // No catalog at all: exactly the v1 path, and exactly the v1 numbers.
        val snapshot = snapshotOf(
            sessions = listOf(
                session(
                    id = "s1",
                    finishedAt = NOW - HOUR,
                    sets = listOf(
                        set(
                            id = "set-1", sessionId = "s1", exerciseId = DEADLIFT,
                            name = "Conventional Deadlift", weightKg = 100.0, reps = 5,
                            at = NOW - HOUR,
                        ),
                    ),
                    exercises = listOf(sessionExercise(DEADLIFT, "Conventional Deadlift", "Posterior chain")),
                ),
            ),
            catalog = emptyMap(),
        )
        assertEquals(500.0, volumeOf(snapshot, CanonicalMuscle.BACK), EPSILON)
        assertEquals(200.0, volumeOf(snapshot, CanonicalMuscle.HAMSTRINGS), EPSILON)
        assertEquals(200.0, volumeOf(snapshot, CanonicalMuscle.GLUTES), EPSILON)
    }

    @Test
    fun unknownJunctionKeyResolvesToParentGroupNeverOther() {
        val catalog = mapOf(
            "ex-raise" to exerciseWithCredits(
                id = "ex-raise",
                name = "Lateral Raise",
                muscleGroup = "Shoulders",
                // A sub-muscle key a later batch might introduce, and a key nothing can place.
                credits = listOf(MuscleCredit("front_delts", 1.0), MuscleCredit("qqzz", 0.5)),
            ),
        )
        val snapshot = snapshotOf(
            sessions = listOf(
                session(
                    id = "s1",
                    finishedAt = NOW - HOUR,
                    sets = listOf(
                        set(
                            id = "set-1", sessionId = "s1", exerciseId = "ex-raise",
                            name = "Lateral Raise", weightKg = 10.0, reps = 10, at = NOW - HOUR,
                        ),
                    ),
                    exercises = listOf(sessionExercise("ex-raise", "Lateral Raise", "Shoulders")),
                ),
            ),
            catalog = catalog,
        )
        // front_delts resolves through the alias index; qqzz falls back to the lift's own group.
        // Both land on shoulders, so the set is worth 1.5x there and nothing is lost to OTHER.
        assertEquals(150.0, volumeOf(snapshot, CanonicalMuscle.SHOULDERS), EPSILON)
        assertEquals(0.0, volumeOf(snapshot, CanonicalMuscle.OTHER), EPSILON)
    }

    /**
     * The executable half of the owner's heat-diff review.
     *
     * Two sessions of real lifts, scored both ways. The v1 column is what the Body tab said
     * before this phase; the v2 column is what it says after. The difference is the point of the
     * change: a deadlift stops being a back exercise that happens to involve the legs.
     *
     * ```
     * muscle       v1        v2       why
     * back         500.0     250.0    deadlift's back share drops from 1.0 to 0.5
     * hamstrings   200.0     250.0    0.4 derived  ->  0.5 declared
     * glutes       200.0     500.0    0.4 derived  ->  1.0 primary
     * chest        400.0     400.0    unchanged, primary either way
     * triceps      160.0     200.0    0.4 derived  ->  0.5 declared
     * shoulders      0.0     100.0    bench's shoulder share was invisible to v1
     * ```
     */
    @Test
    fun knownHistoryHeatBeforeAfterJunctionSwitch() {
        val sessions = listOf(
            session(
                id = "s1",
                finishedAt = NOW - HOUR,
                sets = listOf(
                    set(
                        id = "set-1", sessionId = "s1", exerciseId = DEADLIFT,
                        name = "Conventional Deadlift", weightKg = 100.0, reps = 5, at = NOW - HOUR,
                    ),
                ),
                exercises = listOf(sessionExercise(DEADLIFT, "Conventional Deadlift", "Posterior chain")),
            ),
            session(
                id = "s2",
                finishedAt = NOW - 2 * HOUR,
                sets = listOf(
                    set(
                        id = "set-2", sessionId = "s2", exerciseId = BENCH,
                        name = "Barbell Bench Press", weightKg = 80.0, reps = 5, at = NOW - 2 * HOUR,
                    ),
                ),
                exercises = listOf(sessionExercise(BENCH, "Barbell Bench Press", "Chest")),
            ),
        )

        val v1 = snapshotOf(sessions = sessions, catalog = emptyMap())
        assertEquals(500.0, volumeOf(v1, CanonicalMuscle.BACK), EPSILON)
        assertEquals(200.0, volumeOf(v1, CanonicalMuscle.HAMSTRINGS), EPSILON)
        assertEquals(200.0, volumeOf(v1, CanonicalMuscle.GLUTES), EPSILON)
        assertEquals(400.0, volumeOf(v1, CanonicalMuscle.CHEST), EPSILON)
        assertEquals(0.0, volumeOf(v1, CanonicalMuscle.TRICEPS), EPSILON)
        assertEquals(0.0, volumeOf(v1, CanonicalMuscle.SHOULDERS), EPSILON)

        val catalog = DefaultExercises.catalog()
            .filter { it.id == DEADLIFT || it.id == BENCH }
            .associate { seed ->
                seed.id to exerciseWithCredits(seed.id, seed.name, seed.muscleGroup, seed.credits)
            }
        val v2 = snapshotOf(sessions = sessions, catalog = catalog)
        assertEquals(250.0, volumeOf(v2, CanonicalMuscle.BACK), EPSILON)
        assertEquals(250.0, volumeOf(v2, CanonicalMuscle.HAMSTRINGS), EPSILON)
        assertEquals(500.0, volumeOf(v2, CanonicalMuscle.GLUTES), EPSILON)
        assertEquals(400.0, volumeOf(v2, CanonicalMuscle.CHEST), EPSILON)
        assertEquals(200.0, volumeOf(v2, CanonicalMuscle.TRICEPS), EPSILON)
        assertEquals(100.0, volumeOf(v2, CanonicalMuscle.SHOULDERS), EPSILON)

        // Chest is unchanged, so the diff is genuinely about the secondary model rather than a
        // wholesale rescale of everything.
        assertTrue(volumeOf(v1, CanonicalMuscle.CHEST) == volumeOf(v2, CanonicalMuscle.CHEST))
    }

    private fun snapshotOf(
        sessions: List<WorkoutSession>,
        catalog: Map<String, Exercise>,
    ): BodyHeatSnapshot = MuscleLoadCalculator.snapshot(
        sessions = sessions,
        window = HeatWindow.LAST_14_DAYS,
        nowMs = NOW,
        zone = ZoneOffset.UTC,
        exerciseCatalog = catalog,
    )

    private fun volumeOf(snapshot: BodyHeatSnapshot, muscle: CanonicalMuscle): Double =
        snapshot.loads.first { it.muscle == muscle }.volumeKg

    private fun exerciseWithCredits(
        id: String,
        name: String,
        muscleGroup: String,
        credits: List<MuscleCredit>,
    ): Exercise = Exercise(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        notes = "",
        isCustom = false,
        muscles = credits,
    )

    private companion object {
        const val DEADLIFT = "ex-conventional-deadlift"
        const val BENCH = "ex-barbell-bench-press"
        const val NOW = 1_700_000_000_000L
        const val HOUR = 60L * 60 * 1000
        const val EPSILON = 1e-6
    }
}
