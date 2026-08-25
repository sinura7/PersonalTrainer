package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MuscleRecencyTest {
    @Test
    fun overlayKeepsWindowHeatAndRestoresOlderRecency() {
        val now = 40L * DAY_MS
        val fortyDaysAgo = now - 40L * DAY_MS
        val snapshot = BodyHeatSnapshot(
            window = HeatWindow.LAST_30_DAYS,
            windowStartMs = now - 30L * DAY_MS,
            generatedAtMs = now,
            loads = listOf(
                MuscleLoadSummary(
                    muscle = CanonicalMuscle.CHEST,
                    volumeKg = 0.0,
                    workingSets = 0,
                    sessionCount = 0,
                    lastTrainedAtMs = null,
                    daysSinceLastTrained = null,
                    weeklySets = 0.0,
                    heat = 0.0,
                    exercises = emptyList(),
                ),
            ),
            hasAnyWorkingSets = true,
            hasWindowWorkingSets = false,
        )
        val restored = snapshot.rememberLifetimeRecency(
            lastTrainedByMuscle = mapOf(CanonicalMuscle.CHEST to fortyDaysAgo),
            nowMs = now,
            time = JvmTime,
            zoneId = "UTC",
        )
        val chest = restored.load(CanonicalMuscle.CHEST)
        assertEquals(0, chest.workingSets)
        assertEquals(0.0, chest.heat, 0.0)
        assertEquals(fortyDaysAgo, chest.lastTrainedAtMs)
        assertEquals(40, chest.daysSinceLastTrained)
    }

    @Test
    fun exerciseLogsMapToCatalogMuscles() {
        val bench = Exercise(
            id = "ex-bench",
            name = "Bench press",
            muscleGroup = "Chest",
            notes = "",
            isCustom = false,
            muscles = listOf(
                MuscleCredit("chest", 1.0),
                MuscleCredit("triceps", 0.4),
            ),
        )
        val byMuscle = MuscleRecency.byMuscle(
            lastLoggedAtByExerciseId = mapOf("ex-bench" to 99L),
            catalog = mapOf(bench.id to bench),
        )
        assertEquals(99L, byMuscle[CanonicalMuscle.CHEST])
        assertEquals(99L, byMuscle[CanonicalMuscle.TRICEPS])
        assertNull(byMuscle[CanonicalMuscle.BACK])
    }

    @Test
    fun insightsOverlayUsesUnwindowedRecency() {
        val now = 40L * DAY_MS
        val fortyDaysAgo = now - 40L * DAY_MS
        val bench = Exercise(
            id = "ex-bench",
            name = "Bench press",
            muscleGroup = "Chest",
            notes = "",
            isCustom = false,
            muscles = listOf(MuscleCredit("chest", 1.0)),
        )
        val insights = TrainingInsightsCalculator.compute(
            TrainingInsightsInput(
                history = emptyList(),
                summaries = listOf(
                    SessionSummary(
                        id = "s1",
                        routineId = null,
                        routineName = "Upper",
                        date = fortyDaysAgo,
                        finishedAt = fortyDaysAgo,
                        durationMinutes = 40,
                        workingSets = 8,
                        volumeKg = 2_000.0,
                        localEpochDay = 1,
                    ),
                ),
                routines = emptyList(),
                exerciseCatalog = mapOf(bench.id to bench),
                hints = emptyList(),
                preferences = SchedulePreferences(),
                unit = WeightUnit.KG,
                window = HeatWindow.LAST_30_DAYS,
                nowMs = now,
                zone = ZoneId.of("UTC"),
                lastLoggedAtByExerciseId = mapOf(bench.id to fortyDaysAgo),
            ),
        )
        val chest = insights.snapshot?.load(CanonicalMuscle.CHEST)
        assertEquals(fortyDaysAgo, chest?.lastTrainedAtMs)
        assertEquals(40, chest?.daysSinceLastTrained)
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
