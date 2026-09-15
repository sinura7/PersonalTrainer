package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StallSignalTest {
    private val now = 1_700_000_000_000L
    private val week = 7L * 24L * 60L * 60L * 1000L

    @Test
    fun threeFlatSessionsNameTheLift() {
        val finding = StallSignal.detect(
            listOf(
                squatSession("s1", now - 3 * week, 100.0, 5),
                squatSession("s2", now - 2 * week, 100.0, 5),
                squatSession("s3", now - week, 100.0, 5),
            ),
        )
        assertNotNull(finding)
        assertEquals("ex-squat", finding!!.exerciseId)
        assertEquals("Squat", finding.exerciseName)
        assertEquals(StallSignal.STALL_SESSIONS, finding.sessionsHeld)
    }

    @Test
    fun aClimbingLiftIsNotAStall() {
        assertNull(
            StallSignal.detect(
                listOf(
                    squatSession("s1", now - 3 * week, 100.0, 5),
                    squatSession("s2", now - 2 * week, 100.0, 5),
                    squatSession("s3", now - week, 102.5, 5),
                ),
            ),
        )
    }

    @Test
    fun aRepClimbAtTheSameWeightIsNotAStall() {
        assertNull(
            StallSignal.detect(
                listOf(
                    squatSession("s1", now - 3 * week, 100.0, 4),
                    squatSession("s2", now - 2 * week, 100.0, 5),
                    squatSession("s3", now - week, 100.0, 5),
                ),
            ),
        )
    }

    @Test
    fun twoSessionsAreNotEnough() {
        assertNull(
            StallSignal.detect(
                listOf(
                    squatSession("s1", now - 2 * week, 100.0, 5),
                    squatSession("s2", now - week, 100.0, 5),
                ),
            ),
        )
    }

    @Test
    fun unfinishedSessionsDoNotCount() {
        assertNull(
            StallSignal.detect(
                listOf(
                    squatSession("s1", now - 3 * week, 100.0, 5),
                    squatSession("s2", now - 2 * week, 100.0, 5),
                    squatSession("s3", now - week, 100.0, 5, finished = false),
                ),
            ),
        )
    }

    @Test
    fun bodyweightStallsOnReps() {
        val finding = StallSignal.detect(
            listOf(
                pushSession("s1", now - 3 * week, 12),
                pushSession("s2", now - 2 * week, 12),
                pushSession("s3", now - week, 12),
            ),
        )
        assertNotNull(finding)
        assertEquals("ex-push", finding!!.exerciseId)
    }
}

class StallRecommendationTest {
    private val now = 1_700_000_000_000L
    private val week = 7L * 24L * 60L * 60L * 1000L
    private val zone = java.time.ZoneOffset.UTC

    @Test
    fun stallCardMarksThisWeek() {
        val history = listOf(
            squatSession("s1", now - 3 * week, 100.0, 5),
            squatSession("s2", now - 2 * week, 100.0, 5),
            squatSession("s3", now - week, 100.0, 5),
        )
        val card = RecommendationEngine.stallSignal(coachInputs(history))
        assertNotNull(card)
        assertEquals(RecommendationEngine.KICKER_PROGRESSION, card!!.kicker)
        assertEquals(RecommendationAction.MARK_LIGHTER_WEEK, card.action)
        assertTrue(card.hasDestination)
        assertTrue(card.title.contains("Squat"))
        assertEquals("stall-ex-squat", card.id)
        val trace = checkNotNull(card.trace)
        assertEquals("stall-ex-squat", trace.ruleId)
        assertTrue(trace.reasonCodes.contains(RuleTrace.STALL))
        assertEquals(RecommendationAction.MARK_LIGHTER_WEEK.name, trace.action)
        assertTrue(trace.alternatives.contains("swap the lift"))
        assertEquals("No progress", RuleTraceCopy.reasonLabel(RuleTrace.STALL))
    }

    @Test
    fun aDeloadCardSuppressesTheStall() {
        val perWeek = listOf(9, 6, 4)
        val history = perWeek.flatMapIndexed { weekIndex, sets ->
            (0 until sets).map { setIndex ->
                val at = now - weekIndex * week - setIndex * 3_600_000L - 3_600_000L
                squatSession("w$weekIndex-$setIndex", at, 100.0, 5)
            }
        }
        val inputs = coachInputs(history)
        assertNotNull(RecommendationEngine.deloadSignal(inputs))
        val cards = RecommendationEngine.recommend(inputs)
        assertTrue(cards.any { it.id.startsWith("deload") })
        assertTrue(cards.none { it.id.startsWith("stall-") })
    }

    private fun coachInputs(history: List<WorkoutSession>): CoachInputs =
        CoachInputs(
            basis = MuscleLoadCalculator.coachBasis(history, now, zone, emptyMap()),
            history = history,
            routines = emptyList(),
            hints = emptyList(),
            exerciseCatalog = emptyMap(),
            nowMs = now,
            zone = zone,
        )
}

private fun pushSession(
    id: String,
    at: Long,
    reps: Int,
): WorkoutSession = session(
    id = id,
    finishedAt = at,
    date = at,
    sets = listOf(
        set(
            id = "$id-0",
            sessionId = id,
            exerciseId = "ex-push",
            name = "Push-up",
            weightKg = 0.0,
            reps = reps,
            at = at,
        ),
    ),
    exercises = listOf(
        sessionExercise("ex-push", "Push-up", "Chest", loadType = LoadType.BODYWEIGHT),
    ),
)

private fun squatSession(
    id: String,
    at: Long,
    weightKg: Double,
    reps: Int,
    finished: Boolean = true,
): WorkoutSession = session(
    id = id,
    finishedAt = if (finished) at else null,
    date = at,
    sets = listOf(
        set(
            id = "$id-0",
            sessionId = id,
            exerciseId = "ex-squat",
            name = "Squat",
            weightKg = weightKg,
            reps = reps,
            at = at,
        ),
    ),
    exercises = listOf(sessionExercise("ex-squat", "Squat", "Quads")),
)
