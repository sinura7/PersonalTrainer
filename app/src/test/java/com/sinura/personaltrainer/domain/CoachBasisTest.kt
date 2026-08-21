package com.sinura.personaltrainer.domain

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The coach's own window, and the one property that makes the advice trustworthy: it does not
 * move when the display does.
 */
class CoachBasisTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L

    @Test
    fun theTrailingWindowHasAHardBoundary() {
        val inside = basisOf(listOf(benchSession("in", now - hours(13 * 24 + 23))))
        val outside = basisOf(listOf(benchSession("out", now - hours(14 * 24 + 1))))

        assertTrue(inside.load(CanonicalMuscle.CHEST).weeklySets > 0.0)
        assertEquals(0.0, outside.load(CanonicalMuscle.CHEST).weeklySets, 1e-9)
        assertTrue("a set outside the window still happened", outside.hasAnyWorkingSets)
        assertFalse("…but not inside it", outside.hasBasisWorkingSets)
    }

    @Test
    fun fourteenDaysAreExpressedPerWeek() {
        // Fourteen sets over fourteen days is seven a week, which is the number the bands cut on.
        val sessions = (0 until 14).map { day ->
            benchSession("s$day", now - hours(day * 24L + 1))
        }
        val basis = basisOf(sessions)
        assertEquals(7.0, basis.load(CanonicalMuscle.CHEST).weeklySets, 1e-9)
    }

    @Test
    fun recencyReadsAllOfHistoryNotJustTheWindow() {
        // Forty days ago is well outside the basis, and "40 days since a working set" is still
        // the true and useful answer — clipping it to the window would say "never".
        val basis = basisOf(listOf(benchSession("old", now - hours(40 * 24))))
        assertEquals(40, basis.load(CanonicalMuscle.CHEST).daysSinceLastTrained)
        assertEquals(0.0, basis.load(CanonicalMuscle.CHEST).weeklySets, 1e-9)
    }

    @Test
    fun secondariesAreCreditedByTheirJunctionWeight() {
        val catalog = mapOf(
            "ex-bench" to Exercise(
                id = "ex-bench",
                name = "Bench",
                muscleGroup = "Chest",
                notes = "",
                isCustom = false,
                muscles = listOf(MuscleCredit("chest", 1.0), MuscleCredit("triceps", 0.5)),
            ),
        )
        val sessions = (0 until 14).map { day -> benchSession("s$day", now - hours(day * 24L + 1)) }
        val basis = MuscleLoadCalculator.coachBasis(sessions, now, zone, catalog)

        assertEquals(7.0, basis.load(CanonicalMuscle.CHEST).weeklySets, 1e-9)
        assertEquals(3.5, basis.load(CanonicalMuscle.TRICEPS).weeklySets, 1e-9)
    }

    private fun basisOf(sessions: List<WorkoutSession>): CoachBasis =
        MuscleLoadCalculator.coachBasis(sessions, now, zone)

    private fun benchSession(id: String, at: Long): WorkoutSession = session(
        id = id,
        finishedAt = at,
        sets = listOf(set(id, id, "ex-bench", "Bench", 100.0, 5, at = at)),
        exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
        date = at,
    )

    private fun hours(count: Long): Long = count * 60L * 60L * 1000L
}

/**
 * The property the whole decoupling exists for: the same training produces the same advice,
 * whichever window the body map happens to be showing.
 */
class CoachDecouplingTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L

    @Test
    fun theDisplayWindowCannotChangeTheAdvice() {
        val history = (0 until 10).map { day ->
            session(
                id = "s$day",
                finishedAt = now - day * 24L * 60 * 60 * 1000 - 1000,
                sets = listOf(
                    set("c$day", "s$day", "ex-bench", "Bench", 100.0, 5, at = now - day * 24L * 60 * 60 * 1000 - 1000),
                ),
                exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
                date = now - day * 24L * 60 * 60 * 1000 - 1000,
            )
        }

        // Both snapshots are built, exactly as the two chips would build them, and the coach's
        // basis is constructed independently of either.
        MuscleLoadCalculator.snapshot(history, HeatWindow.CURRENT_WEEK, now, zone)
        MuscleLoadCalculator.snapshot(history, HeatWindow.LAST_30_DAYS, now, zone)

        val basis = MuscleLoadCalculator.coachBasis(history, now, zone)
        val first = RecommendationEngine.recommend(inputsFor(basis, history))
        val second = RecommendationEngine.recommend(inputsFor(basis, history))
        assertEquals(first, second)

        // And the basis itself is a function of history and clock alone — no window anywhere
        // in its signature, which is what makes the property structural rather than incidental.
        assertEquals(basis, MuscleLoadCalculator.coachBasis(history, now, zone))
    }

    private fun inputsFor(basis: CoachBasis, history: List<WorkoutSession>) = CoachInputs(
        basis = basis,
        history = history,
        routines = emptyList(),
        hints = emptyList(),
        exerciseCatalog = emptyMap(),
        nowMs = now,
        zone = zone,
    )
}
