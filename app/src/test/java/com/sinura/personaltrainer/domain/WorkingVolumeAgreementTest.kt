package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/**
 * History, the body map and the exercise detail all answer "how much did I do". They have to
 * answer it the same way.
 *
 * They used to agree on a fiction. A bodyweight set was priced at a flat 40 kg so that one
 * number could cover every lift, and the test that lived here pinned that agreement — eight
 * pull-ups were 320 kg on both surfaces, which is consistent and untrue. The agreement still
 * has to hold; what it is an agreement *about* is now two numbers, and neither is invented.
 */
class WorkingVolumeAgreementTest {
    private val at = 1_700_000_000_000L

    @Test
    fun aBodyweightSetIsRepsOnBothSurfacesAndKilogramsOnNeither() {
        val pullUps = session(
            id = "s1",
            finishedAt = at,
            sets = listOf(set("a", "s1", "ex-pu", "Pull-Up", 0.0, 8, at = at)),
            exercises = listOf(sessionExercise("ex-pu", "Pull-Up", "Back", LoadType.BODYWEIGHT)),
        )
        val onTheBodyMap = MuscleLoadCalculator
            .snapshot(listOf(pullUps), HeatWindow.CURRENT_MONTH, at, ZoneOffset.UTC)
            .load(CanonicalMuscle.BACK)

        assertEquals(0.0, onTheBodyMap.volumeKg, 0.0001)
        assertEquals(8, onTheBodyMap.bodyweightReps)
        assertEquals(onTheBodyMap.work, pullUps.work())
    }

    @Test
    fun aVestCountsAsKilogramsAndTheRepsStillCount() {
        // The only honest kilogram figure for a weighted pull-up is the part that is not the
        // person: twenty kilos moved eight times.
        val weighted = session(
            id = "s1",
            finishedAt = at,
            sets = listOf(set("a", "s1", "ex-pu", "Pull-Up", 20.0, 8, at = at)),
            exercises = listOf(sessionExercise("ex-pu", "Pull-Up", "Back", LoadType.BODYWEIGHT_PLUS)),
        )
        assertEquals(SetWork(volumeKg = 160.0, bodyweightReps = 8), weighted.work())
    }

    @Test
    fun assistanceIsNotCreditedAsWorkButTheRepsAre() {
        // The machine took twenty kilos off. Counting them would pay the lifter for the help.
        val assisted = session(
            id = "s1",
            finishedAt = at,
            sets = listOf(set("a", "s1", "ex-pu", "Pull-Up", 20.0, 8, at = at)),
            exercises = listOf(sessionExercise("ex-pu", "Pull-Up", "Back", LoadType.ASSISTED)),
        )
        assertEquals(SetWork(volumeKg = 0.0, bodyweightReps = 8), assisted.work())
    }

    @Test
    fun loadedSetsAreUnchanged() {
        val bench = session(
            id = "s1",
            finishedAt = at,
            sets = listOf(
                set("a", "s1", "ex-bench", "Bench", 100.0, 5, at = at),
                set("b", "s1", "ex-bench", "Bench", 60.0, 8, warmup = true, at = at),
            ),
            exercises = listOf(sessionExercise("ex-bench", "Bench", "Chest", LoadType.EXTERNAL)),
        )
        assertEquals(SetWork(volumeKg = 500.0, bodyweightReps = 0), bench.work())
        assertEquals(500.0, bench.workingVolumeKg(), 0.0001)
    }

    @Test
    fun aMixedSessionReportsBothAndFlattensNeither() {
        val mixed = session(
            id = "s1",
            finishedAt = at,
            sets = listOf(
                set("a", "s1", "ex-bench", "Bench", 100.0, 5, at = at),
                set("b", "s1", "ex-pu", "Pull-Up", 0.0, 8, at = at),
            ),
            exercises = listOf(
                sessionExercise("ex-bench", "Bench", "Chest", LoadType.EXTERNAL),
                sessionExercise("ex-pu", "Pull-Up", "Back", LoadType.BODYWEIGHT),
            ),
        )
        assertEquals(SetWork(volumeKg = 500.0, bodyweightReps = 8), mixed.work())
    }

    @Test
    fun aLiftMissingFromTheSessionIsTreatedAsLoaded() {
        // The safer wrong answer: a barbell lift silently reclassified as bodyweight would drop
        // its kilograms out of every total the owner has.
        val orphan = session(
            id = "s1",
            finishedAt = at,
            sets = listOf(set("a", "s1", "ex-gone", "Deleted", 80.0, 5, at = at)),
            exercises = emptyList(),
        )
        assertEquals(SetWork(volumeKg = 400.0, bodyweightReps = 0), orphan.work())
    }
}
