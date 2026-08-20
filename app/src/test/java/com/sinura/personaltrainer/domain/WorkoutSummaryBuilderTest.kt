package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSummaryBuilderTest {
    private val at = 1_700_000_000_000L
    private var nextId = 0

    private fun logged(
        exerciseId: String,
        name: String,
        weightKg: Double,
        reps: Int,
        offsetMs: Long,
        warmup: Boolean = false,
    ) = set(
        id = "set-${nextId++}",
        sessionId = "s1",
        exerciseId = exerciseId,
        name = name,
        weightKg = weightKg,
        reps = reps,
        warmup = warmup,
        at = at + offsetMs,
    )

    private fun finished(sets: List<SetLog>) = session(
        id = "s1",
        finishedAt = at + 3_600_000,
        sets = sets,
        exercises = emptyList(),
        date = at,
    )

    private fun prior(weightKg: Double, reps: Int) = ExerciseSetRecord(
        setId = "old-${nextId++}",
        sessionId = "s0",
        weightKg = weightKg,
        reps = reps,
        completedAt = at - 7 * 24 * 3_600_000L,
    )

    @Test
    fun highlightsLeadWithTheHeaviestWorkedLift() {
        val summary = WorkoutSummaryBuilder.build(
            finished(
                listOf(
                    logged("ex-curl", "Curl", 20.0, 12, 0),
                    logged("ex-squat", "Squat", 140.0, 5, 1000),
                ),
            ),
            emptyMap(),
        )
        assertEquals(listOf("Squat", "Curl"), summary.highlights.map { it.exerciseName })
    }

    @Test
    fun warmupsAreNotWork() {
        val summary = WorkoutSummaryBuilder.build(
            finished(
                listOf(
                    logged("ex-squat", "Squat", 60.0, 5, 0, warmup = true),
                    logged("ex-squat", "Squat", 100.0, 5, 1000),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, summary.workingSets)
        assertEquals(500.0, summary.volumeKg, 0.0001)
    }

    @Test
    fun ascendingSetsAreOneWeightRecordNotThree() {
        // Judged against the pre-session history alone, 100, 105 and 110 would each "beat" the
        // 95 kg best and the session would claim three weight records in one exercise.
        val summary = WorkoutSummaryBuilder.build(
            finished(
                listOf(
                    logged("ex-squat", "Squat", 100.0, 5, 0),
                    logged("ex-squat", "Squat", 105.0, 5, 1000),
                    logged("ex-squat", "Squat", 110.0, 5, 2000),
                ),
            ),
            mapOf("ex-squat" to listOf(prior(95.0, 5))),
        )
        assertEquals(setOf(PersonalRecordKind.WEIGHT, PersonalRecordKind.ESTIMATED_ONE_REP_MAX), summary.highlights.single().records)
        assertEquals(2, summary.recordCount)
    }

    @Test
    fun aLiftWithNoPriorHistorySetsNoRecords() {
        val summary = WorkoutSummaryBuilder.build(
            finished(listOf(logged("ex-new", "New lift", 60.0, 8, 0))),
            emptyMap(),
        )
        assertTrue(summary.highlights.single().records.isEmpty())
        assertEquals(0, summary.recordCount)
    }

    @Test
    fun aBackOffSessionBreaksNothing() {
        val summary = WorkoutSummaryBuilder.build(
            finished(
                listOf(
                    logged("ex-squat", "Squat", 100.0, 5, 0),
                    logged("ex-squat", "Squat", 80.0, 8, 1000),
                ),
            ),
            mapOf("ex-squat" to listOf(prior(120.0, 5))),
        )
        assertTrue(summary.highlights.single().records.isEmpty())
    }

    @Test
    fun theTopSetIsTheOneProgressionWouldJudge() {
        val summary = WorkoutSummaryBuilder.build(
            finished(
                listOf(
                    logged("ex-squat", "Squat", 100.0, 5, 0),
                    logged("ex-squat", "Squat", 80.0, 8, 1000),
                ),
            ),
            emptyMap(),
        )
        val top = summary.highlights.single().topSet!!
        assertEquals(100.0, top.weightKg, 0.0001)
        assertEquals(5, top.reps)
    }

    @Test
    fun aWarmupOnlySessionHasNoWork() {
        val summary = WorkoutSummaryBuilder.build(
            finished(listOf(logged("ex-squat", "Squat", 60.0, 5, 0, warmup = true))),
            emptyMap(),
        )
        assertFalse(summary.hasWork)
        assertTrue(summary.highlights.isEmpty())
    }

    @Test
    fun anUnnamedSessionStillHasATitle() {
        // Free workouts carry no routine name; the summary must not be headed by a blank.
        val unnamed = WorkoutSession(
            id = "s1",
            routineId = null,
            routineName = null,
            date = at,
            notes = "",
            durationMinutes = 42,
            startedAt = at,
            finishedAt = at + 3_600_000,
            exercises = emptyList(),
            sets = listOf(logged("ex-squat", "Squat", 100.0, 5, 0)),
        )
        assertEquals("Workout", WorkoutSummaryBuilder.build(unnamed, emptyMap()).title)
        assertEquals("Workout", WorkoutSummaryBuilder.build(unnamed.copy(routineName = "  "), emptyMap()).title)
    }
}
