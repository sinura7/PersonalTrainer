package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class RecommendationEngineTest {
    private val zone = ZoneOffset.UTC
    private val now = 1_700_000_000_000L

    @Test
    fun emptyHistoryProducesNoRecommendations() {
        val snap = MuscleLoadCalculator.snapshot(emptyList(), HeatWindow.LAST_7_DAYS, now, zone)
        assertTrue(RecommendationEngine.recommend(snap, emptyList()).isEmpty())
    }

    @Test
    fun neglectedMuscleUsesDaysSinceLastTrained() {
        val snap = snapshotWith(
            set("c", "s1", "ex-bench", "Bench", 100.0, 5, at = now - days(1), muscle = "Chest"),
            set("b", "s2", "ex-row", "Row", 80.0, 5, at = now - days(9), muscle = "Back"),
            set("k", "s1", "ex-ohp", "OHP", 40.0, 5, at = now - days(2), muscle = "Shoulders"),
            set("i", "s1", "ex-curl", "Curl", 20.0, 8, at = now - days(2), muscle = "Biceps"),
            set("t", "s1", "ex-tri", "Pushdown", 25.0, 8, at = now - days(2), muscle = "Triceps"),
            set("q", "s1", "ex-squat", "Squat", 80.0, 5, at = now - days(2), muscle = "Quads"),
            set("h", "s1", "ex-rdl", "RDL", 80.0, 5, at = now - days(2), muscle = "Hamstrings"),
            set("g", "s1", "ex-ht", "Hip Thrust", 80.0, 5, at = now - days(2), muscle = "Glutes"),
            set("v", "s1", "ex-calf", "Calf Raise", 40.0, 10, at = now - days(2), muscle = "Calves"),
            set("o", "s1", "ex-plank", "Plank", 0.0, 20, at = now - days(2), muscle = "Core"),
        )
        val recs = RecommendationEngine.neglectedMuscles(snap, suppressUpper = false)
        assertTrue(recs.any { it.id == "neglect-BACK" && it.title.contains("9 days") })
        assertTrue(recs.any { it.priority == RecommendationPriority.ATTENTION })
    }

    @Test
    fun neverTrainedMuscleIsHighPriority() {
        val snap = snapshotWith(
            set("c", "s1", "ex-bench", "Bench", 100.0, 5, at = now - days(1), muscle = "Chest"),
        )
        val recs = RecommendationEngine.neglectedMuscles(snap, suppressUpper = false)
        assertTrue(recs.any { it.actionMuscle == CanonicalMuscle.BACK && it.priority == RecommendationPriority.HIGH })
    }

    @Test
    fun chestBackImbalance() {
        val snap = snapshotWith(
            set("c", "s1", "ex-bench", "Bench", 120.0, 5, at = now - days(1), muscle = "Chest"),
            set("b", "s1", "ex-row", "Row", 40.0, 5, at = now - days(1), muscle = "Back"),
        )
        val recs = RecommendationEngine.imbalances(snap)
        assertEquals(1, recs.size)
        assertEquals("imbalance-CHEST-BACK", recs.first().id)
        assertEquals(CanonicalMuscle.BACK, recs.first().actionMuscle)
        assertTrue(recs.first().title.contains("Chest"))
        assertTrue(recs.first().title.contains("Back"))
    }

    @Test
    fun imbalanceIgnoresTinySamples() {
        val snap = snapshotWith(
            set("c", "s1", "ex-bench", "Bench", 20.0, 5, at = now - days(1), muscle = "Chest"),
            set("b", "s1", "ex-row", "Row", 5.0, 5, at = now - days(1), muscle = "Back"),
        )
        assertTrue(RecommendationEngine.imbalances(snap).isEmpty())
    }

    @Test
    fun recoveryWhenUpperIsHotAndLegsAreQuiet() {
        val snap = snapshotWith(
            set("c", "s1", "ex-bench", "Bench", 100.0, 5, at = now - days(1), muscle = "Chest"),
            set("k", "s1", "ex-ohp", "OHP", 90.0, 5, at = now - days(1), muscle = "Shoulders"),
            set("t", "s1", "ex-tri", "Pushdown", 85.0, 5, at = now - days(1), muscle = "Triceps"),
            set("q", "s1", "ex-squat", "Squat", 10.0, 5, at = now - days(1), muscle = "Quads"),
        )
        val rec = RecommendationEngine.recoverySignal(snap)
        assertEquals("recovery-upper", rec?.id)
        assertEquals(RecommendationPriority.HIGH, rec?.priority)
    }

    @Test
    fun recoverySuppressesUpperNeglect() {
        val snap = snapshotWith(
            set("c", "s1", "ex-bench", "Bench", 100.0, 5, at = now - days(1), muscle = "Chest"),
            set("k", "s1", "ex-ohp", "OHP", 90.0, 5, at = now - days(1), muscle = "Shoulders"),
            set("t", "s1", "ex-tri", "Pushdown", 85.0, 5, at = now - days(1), muscle = "Triceps"),
            set("q", "s1", "ex-squat", "Squat", 10.0, 5, at = now - days(1), muscle = "Quads"),
        )
        val recs = RecommendationEngine.recommend(snap, emptyList())
        assertTrue(recs.any { it.id == "recovery-upper" })
        assertTrue(recs.none { it.id.startsWith("neglect-") && it.actionMuscle?.region == MuscleRegion.UPPER })
    }

    @Test
    fun coreGapWhenWindowHasTrainingButNoCore() {
        val snap = snapshotWith(
            set("c", "s1", "ex-bench", "Bench", 100.0, 5, at = now - days(1), muscle = "Chest"),
        )
        val rec = RecommendationEngine.coreCoverageGap(snap)
        assertEquals("coverage-core", rec?.id)
        assertEquals(CanonicalMuscle.CORE, rec?.actionMuscle)
    }

    @Test
    fun coreGapSkippedWhenCoreWasTrained() {
        val snap = snapshotWith(
            set("c", "s1", "ex-plank", "Plank", 0.0, 30, at = now - days(1), muscle = "Core"),
        )
        assertNull(RecommendationEngine.coreCoverageGap(snap))
    }

    @Test
    fun progressionListsReadyLifts() {
        val hints = listOf(
            ProgressionHint("ex-squat", "Squat", 100.0, 5, 5, 102.5, ProgressionAction.INCREASE),
            ProgressionHint("ex-bench", "Bench", 80.0, 5, 5, 82.5, ProgressionAction.INCREASE),
            ProgressionHint("ex-row", "Row", 70.0, 3, 5, 70.0, ProgressionAction.HOLD),
        )
        val rec = RecommendationEngine.progressionOpportunity(hints)
        assertEquals("progression-ready", rec?.id)
        assertTrue(rec!!.title.contains("Squat"))
        assertTrue(rec.title.contains("Bench"))
        assertTrue(rec.title.contains("+2.5 kg"))
        assertEquals(RecommendationAction.START_WORKOUT, rec.action)
    }

    @Test
    fun recommendCapsAndRanksHighFirst() {
        val snap = snapshotWith(
            set("c", "s1", "ex-bench", "Bench", 140.0, 5, at = now - days(1), muscle = "Chest"),
            set("b", "s1", "ex-row", "Row", 40.0, 5, at = now - days(1), muscle = "Back"),
        )
        val recs = RecommendationEngine.recommend(
            snap,
            listOf(ProgressionHint("ex-bench", "Bench", 140.0, 5, 5, 142.5, ProgressionAction.INCREASE)),
        )
        assertTrue(recs.size <= RecommendationEngine.MAX_RESULTS)
        val priorities = recs.map { it.priority }
        assertEquals(priorities.sortedByDescending { it.ordinal.let { p -> 2 - p } }, priorities)
        assertTrue(recs.first().priority == RecommendationPriority.HIGH)
    }

    private fun snapshotWith(vararg sets: Pair<SetLog, String>): BodyHeatSnapshot {
        val grouped = sets.groupBy { it.first.sessionId }
        val sessions = grouped.map { (sessionId, rows) ->
            session(
                id = sessionId,
                finishedAt = rows.maxOf { it.first.completedAt },
                sets = rows.map { it.first },
                exercises = rows.map { (set, muscle) ->
                    sessionExercise(set.exerciseId, set.exerciseName, muscle)
                }.distinctBy { it.exercise.id },
            )
        }
        return MuscleLoadCalculator.snapshot(sessions, HeatWindow.LAST_7_DAYS, now, zone)
    }

    private fun set(
        id: String,
        sessionId: String,
        exerciseId: String,
        name: String,
        weightKg: Double,
        reps: Int,
        at: Long,
        muscle: String,
    ): Pair<SetLog, String> = set(id, sessionId, exerciseId, name, weightKg, reps, false, at) to muscle

    private fun days(count: Long): Long = count * 24L * 60L * 60L * 1000L
}
