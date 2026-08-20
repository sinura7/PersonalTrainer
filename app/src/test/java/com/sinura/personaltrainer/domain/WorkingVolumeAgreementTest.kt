package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/**
 * History, the body map and the exercise detail all answer "how much did I lift". They have to
 * answer it the same way.
 */
class WorkingVolumeAgreementTest {
    @Test
    fun bodyweightSetsAreWorthTheSameOnHistoryAsOnTheBodyMap() {
        val at = 1_700_000_000_000L
        val pullUps = session(
            id = "s1",
            finishedAt = at,
            sets = listOf(set("a", "s1", "ex-pu", "Pull-Up", 0.0, 8, at = at)),
            exercises = listOf(sessionExercise("ex-pu", "Pull-Up", "Back")),
        )
        val onTheBodyMap = MuscleLoadCalculator
            .snapshot(listOf(pullUps), HeatWindow.LAST_7_DAYS, at, ZoneOffset.UTC)
            .load(CanonicalMuscle.BACK)
            .volumeKg
        // Previously this read 0.0 while the body map read 320.0.
        assertEquals(onTheBodyMap, pullUps.workingVolumeKg(), 0.0001)
        assertEquals(MuscleLoadCalculator.BODYWEIGHT_EQUIVALENT_KG * 8, pullUps.workingVolumeKg(), 0.0001)
    }

    @Test
    fun loadedSetsAreUnchanged() {
        val at = 1_700_000_000_000L
        val bench = session(
            id = "s1",
            finishedAt = at,
            sets = listOf(
                set("a", "s1", "ex-bench", "Bench", 100.0, 5, at = at),
                set("b", "s1", "ex-bench", "Bench", 60.0, 8, warmup = true, at = at),
            ),
            exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest")),
        )
        assertEquals(500.0, bench.workingVolumeKg(), 0.0001)
    }
}
