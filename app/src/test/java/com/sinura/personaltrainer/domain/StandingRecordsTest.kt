package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * History's Records section is a lifetime claim. It used to be computed from the 32-day
 * insight window, so a stronger lift older than a month vanished and a weaker recent set was
 * presented as the record.
 */
class StandingRecordsTest {
    private val now = 1_700_000_000_000L

    @Test
    fun anOlderBestOutlivesAWeakerRecentLift() {
        val rows = standingRecords(
            listOf(
                loaded("a", "s-old", "ex-bench", "Bench", 120.0, 5, now - 90 * DAY),
                loaded("b", "s-new", "ex-bench", "Bench", 100.0, 5, now - 2 * DAY),
            ),
        )
        val bench = rows.single()
        assertEquals(PersonalRecordKind.ESTIMATED_ONE_REP_MAX, bench.kind)
        assertEquals(120.0, bench.valueKg, 1e-9)
        assertEquals(now - 90 * DAY, bench.achievedAt)
    }

    @Test
    fun aRepsLiftRanksByRepsHoweverOldTheBestIs() {
        val rows = standingRecords(
            listOf(
                RecordSet("ex-pull", "Pull-Up", LoadClass.BODYWEIGHT, record("a", "s1", 0.0, 15, now - 200 * DAY)),
                RecordSet("ex-pull", "Pull-Up", LoadClass.BODYWEIGHT, record("b", "s2", 0.0, 9, now - DAY)),
            ),
        )
        val pull = rows.single()
        assertEquals(PersonalRecordKind.REPS, pull.kind)
        assertEquals(15, pull.reps)
    }

    @Test
    fun theNewestSetDecidesALiftsNameAndClass() {
        // An activity block snapshots the lift as it was; a later session reads the library.
        // Where they disagree, the most recent statement wins, not whichever sorted first.
        val rows = standingRecords(
            listOf(
                RecordSet("ex-dip", "Dip", LoadClass.LOADED, record("a", "s1", 20.0, 8, now - 10 * DAY)),
                RecordSet("ex-dip", "Weighted dip", LoadClass.BODYWEIGHT_ADDED, record("b", "s2", 10.0, 12, now - DAY)),
            ),
        )
        val dip = rows.single()
        assertEquals("Weighted dip", dip.exerciseName)
        assertEquals(PersonalRecordKind.REPS, dip.kind)
        assertEquals(12, dip.reps)
    }

    @Test
    fun oneRowPerLiftNewestFirstAndCapped() {
        val rows = standingRecords(
            listOf(
                loaded("a", "s1", "ex-squat", "Squat", 140.0, 3, now - 3 * DAY),
                loaded("b", "s1", "ex-squat", "Squat", 120.0, 8, now - 3 * DAY),
                loaded("c", "s2", "ex-bench", "Bench", 100.0, 5, now - 2 * DAY),
                loaded("d", "s3", "ex-row", "Row", 80.0, 8, now - DAY),
                loaded("e", "s4", "ex-ohp", "OHP", 60.0, 5, now - 4 * DAY),
            ),
        )
        assertEquals(listOf("ex-row", "ex-bench", "ex-squat"), rows.map { it.exerciseId })
        assertEquals(rows.size, rows.map { it.exerciseId }.toSet().size)
    }

    @Test
    fun emptyLogHasNoRecords() {
        assertTrue(standingRecords(emptyList()).isEmpty())
    }

    @Test
    fun aSessionGraphProjectsOnlyFinishedWorkingSets() {
        val bench = sessionExercise("ex-bench", "Bench", "Chest", loadType = LoadType.BODYWEIGHT_PLUS)
        val finished = session(
            id = "s1",
            finishedAt = now - DAY,
            sets = listOf(
                set("w", "s1", "ex-bench", "Bench", 40.0, 10, warmup = true, at = now - DAY),
                set("a", "s1", "ex-bench", "Bench", 20.0, 8, at = now - DAY + 1),
            ),
            exercises = listOf(bench),
        )
        val live = session(
            id = "live",
            finishedAt = null,
            sets = listOf(set("l", "live", "ex-bench", "Bench", 300.0, 5, at = now)),
            exercises = listOf(bench),
            date = now,
        )
        val sets = finished.recordSets() + live.recordSets()
        val only = sets.single()
        assertEquals("a", only.set.setId)
        // The class comes from the session's own copy of the lift, not the library.
        assertEquals(LoadClass.BODYWEIGHT_ADDED, only.loadClass)
    }

    @Test
    fun prSummaryIsTheGraphShapedWayIn() {
        val bench = sessionExercise("ex-bench", "Bench", "Chest")
        val graph = session(
            id = "s1",
            finishedAt = now - DAY,
            sets = listOf(
                set("a", "s1", "ex-bench", "Bench", 100.0, 1, at = now - DAY),
                set("b", "s1", "ex-bench", "Bench", 95.0, 5, at = now - DAY + 1),
            ),
            exercises = listOf(bench),
        )
        assertEquals(standingRecords(graph.recordSets()), prSummary(listOf(graph)))
    }

    private fun loaded(
        setId: String,
        sessionId: String,
        exerciseId: String,
        name: String,
        weightKg: Double,
        reps: Int,
        at: Long,
    ): RecordSet = RecordSet(
        exerciseId = exerciseId,
        exerciseName = name,
        loadClass = LoadClass.LOADED,
        set = record(setId, sessionId, weightKg, reps, at),
    )

    private fun record(
        setId: String,
        sessionId: String,
        weightKg: Double,
        reps: Int,
        at: Long,
    ): ExerciseSetRecord = ExerciseSetRecord(
        setId = setId,
        sessionId = sessionId,
        weightKg = weightKg,
        reps = reps,
        completedAt = at,
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
