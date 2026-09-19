package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The records row: what you have actually hit, and when.
 */
class PrSummaryTest {
    private val now = 1_700_000_000_000L

    @Test
    fun theEstimatedMaxIsPreferredOverRawWeight() {
        // A heavy single and a lighter set of five. The five is the better lift by e1RM, and
        // e1RM is the record that survives a change in rep range.
        val rows = prSummary(
            listOf(
                sessionWith(
                    "s1",
                    at = now - DAY,
                    sets = listOf(
                        loggedSet("a", "s1", "ex-bench", "Bench", 100.0, 1, now - DAY),
                        loggedSet("b", "s1", "ex-bench", "Bench", 95.0, 5, now - DAY),
                    ),
                ),
            ),
        )
        val bench = rows.single()
        assertEquals(PersonalRecordKind.ESTIMATED_ONE_REP_MAX, bench.kind)
        assertEquals(95.0, bench.valueKg, 1e-9)
        assertEquals(5, bench.reps)
    }

    @Test
    fun aBodyweightLiftFallsBackToItsWeightRecord() {
        // No external load means no estimate to make; the lift must not vanish from the list.
        val rows = prSummary(
            listOf(
                sessionWith(
                    "s1",
                    at = now - DAY,
                    sets = listOf(loggedSet("a", "s1", "ex-pull", "Pull-Up", 0.0, 10, now - DAY)),
                ),
            ),
        )
        assertEquals(PersonalRecordKind.WEIGHT, rows.single().kind)
    }

    @Test
    fun oneRowPerExerciseNewestFirstAndCapped() {
        val rows = prSummary(
            listOf(
                sessionWith(
                    "s1",
                    at = now - 3 * DAY,
                    sets = listOf(
                        loggedSet("a", "s1", "ex-squat", "Squat", 140.0, 3, now - 3 * DAY),
                        // A second squat record in the same session must not take a second row.
                        loggedSet("b", "s1", "ex-squat", "Squat", 120.0, 8, now - 3 * DAY),
                    ),
                ),
                sessionWith(
                    "s2",
                    at = now - 2 * DAY,
                    sets = listOf(loggedSet("c", "s2", "ex-bench", "Bench", 100.0, 5, now - 2 * DAY)),
                ),
                sessionWith(
                    "s3",
                    at = now - DAY,
                    sets = listOf(loggedSet("d", "s3", "ex-row", "Row", 80.0, 8, now - DAY)),
                ),
                sessionWith(
                    "s4",
                    at = now - 4 * DAY,
                    sets = listOf(loggedSet("e", "s4", "ex-ohp", "OHP", 60.0, 5, now - 4 * DAY)),
                ),
            ),
        )
        assertEquals(3, rows.size)
        assertEquals(listOf("ex-row", "ex-bench", "ex-squat"), rows.map { it.exerciseId })
        assertEquals(rows.size, rows.map { it.exerciseId }.toSet().size)
    }

    @Test
    fun warmUpsAndUnfinishedSessionsAreNotRecords() {
        val rows = prSummary(
            listOf(
                sessionWith(
                    "s1",
                    at = now - DAY,
                    sets = listOf(loggedSet("w", "s1", "ex-bench", "Bench", 200.0, 5, now - DAY, warmup = true)),
                ),
                session(
                    id = "live",
                    finishedAt = null,
                    sets = listOf(loggedSet("l", "live", "ex-squat", "Squat", 300.0, 5, now)),
                    exercises = emptyList(),
                    date = now,
                ),
            ),
        )
        assertTrue(rows.toString(), rows.isEmpty())
    }

    private fun sessionWith(id: String, at: Long, sets: List<SetLog>): WorkoutSession =
        session(id = id, finishedAt = at, sets = sets, exercises = emptyList(), date = at)

    private fun loggedSet(
        id: String,
        sessionId: String,
        exerciseId: String,
        name: String,
        weightKg: Double,
        reps: Int,
        at: Long,
        warmup: Boolean = false,
    ): SetLog = set(id, sessionId, exerciseId, name, weightKg, reps, warmup, at)

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
