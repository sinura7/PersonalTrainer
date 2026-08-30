package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordsTest {
    private var nextId = 0

    private fun rec(weightKg: Double, reps: Int, at: Long, session: String = "s") =
        ExerciseSetRecord(
            setId = "set-${nextId++}",
            sessionId = session,
            weightKg = weightKg,
            reps = reps,
            completedAt = at,
        )

    @Test
    fun aSingleIsItsOwnOneRepMax() {
        // Epley would return 1.03x the weight here, an estimate worse than the measurement.
        assertEquals(140.0, PersonalRecords.estimatedOneRepMaxKg(140.0, 1)!!, 0.0001)
    }

    @Test
    fun epleyEstimatesMultiRepSets() {
        assertEquals(100.0 * (1 + 5 / 30.0), PersonalRecords.estimatedOneRepMaxKg(100.0, 5)!!, 0.0001)
    }

    @Test
    fun highRepSetsHaveNoMeaningfulEstimate() {
        assertNull(PersonalRecords.estimatedOneRepMaxKg(60.0, PersonalRecords.MAX_REPS_FOR_ESTIMATE + 1))
    }

    @Test
    fun bodyweightSetsHaveNoEstimate() {
        // 0 kg is "no external load", not "a load of zero"; treating it as a number would put
        // every bodyweight lift at an e1RM of nothing.
        assertNull(PersonalRecords.estimatedOneRepMaxKg(0.0, 8))
    }

    @Test
    fun bestsPickTheHeaviestSetForWeight() {
        val history = listOf(rec(100.0, 5, 1), rec(120.0, 1, 2), rec(110.0, 3, 3))
        val best = PersonalRecords.bests(history, LoadClass.LOADED).getValue(PersonalRecordKind.WEIGHT)
        assertEquals(120.0, best.weightKg, 0.0001)
        assertEquals(120.0, best.value, 0.0001)
    }

    @Test
    fun bestEstimatedMaxIsNotAlwaysTheHeaviestSet() {
        // 120x1 = 120; 110x3 = 121. The heavier set is not the stronger performance.
        val history = listOf(rec(120.0, 1, 1), rec(110.0, 3, 2))
        val bests = PersonalRecords.bests(history, LoadClass.LOADED)
        assertEquals(120.0, bests.getValue(PersonalRecordKind.WEIGHT).weightKg, 0.0001)
        assertEquals(121.0, bests.getValue(PersonalRecordKind.ESTIMATED_ONE_REP_MAX).value, 0.0001)
        assertEquals(110.0, bests.getValue(PersonalRecordKind.ESTIMATED_ONE_REP_MAX).weightKg, 0.0001)
    }

    @Test
    fun repsRecordIsMeasuredAtTheTopWeight() {
        // 12 reps at 60 kg is not the record being chased; 6 at 100 kg is.
        val history = listOf(rec(60.0, 12, 1), rec(100.0, 4, 2), rec(100.0, 6, 3))
        val best = PersonalRecords.bests(history, LoadClass.LOADED).getValue(PersonalRecordKind.REPS_AT_WEIGHT)
        assertEquals(100.0, best.weightKg, 0.0001)
        assertEquals(6, best.reps)
        assertEquals(6.0, best.value, 0.0001)
    }

    @Test
    fun aTiedRecordStaysWithWhoeverGotThereFirst() {
        val history = listOf(rec(100.0, 5, at = 100), rec(100.0, 5, at = 200))
        val best = PersonalRecords.bests(history, LoadClass.LOADED).getValue(PersonalRecordKind.WEIGHT)
        assertEquals(100L, best.achievedAt)
    }

    @Test
    fun emptyHistoryHasNoRecords() {
        assertTrue(PersonalRecords.bests(emptyList(), LoadClass.LOADED).isEmpty())
    }

    @Test
    fun theFirstSetOfALiftSetsNoRecords() {
        assertEquals(emptySet<PersonalRecordKind>(), PersonalRecords.detect(rec(100.0, 5, 1), emptyList(), LoadClass.LOADED))
    }

    @Test
    fun equallingARecordIsNotBreakingIt() {
        val prior = listOf(rec(100.0, 5, 1))
        assertEquals(emptySet<PersonalRecordKind>(), PersonalRecords.detect(rec(100.0, 5, 2), prior, LoadClass.LOADED))
    }

    @Test
    fun aHeavierSetBreaksTheWeightRecord() {
        val prior = listOf(rec(100.0, 5, 1))
        val broken = PersonalRecords.detect(rec(102.5, 5, 2), prior, LoadClass.LOADED)
        assertTrue(PersonalRecordKind.WEIGHT in broken)
        assertTrue(PersonalRecordKind.ESTIMATED_ONE_REP_MAX in broken)
    }

    @Test
    fun moreRepsAtTheSameWeightBreaksOnlyTheRepRecord() {
        val prior = listOf(rec(100.0, 5, 1))
        val broken = PersonalRecords.detect(rec(100.0, 6, 2), prior, LoadClass.LOADED)
        assertTrue(PersonalRecordKind.REPS_AT_WEIGHT in broken)
        assertTrue(PersonalRecordKind.WEIGHT !in broken)
    }

    @Test
    fun moreRepsAtANewWeightIsNotARepRecord() {
        // There is no standing rep record at 105 kg to beat, so claiming one would be a lie.
        val prior = listOf(rec(100.0, 5, 1))
        val broken = PersonalRecords.detect(rec(105.0, 8, 2), prior, LoadClass.LOADED)
        assertTrue(PersonalRecordKind.REPS_AT_WEIGHT !in broken)
        assertTrue(PersonalRecordKind.WEIGHT in broken)
    }

    @Test
    fun aBackOffSetBreaksNothing() {
        val prior = listOf(rec(100.0, 5, 1), rec(80.0, 8, 2))
        assertEquals(emptySet<PersonalRecordKind>(), PersonalRecords.detect(rec(70.0, 8, 3), prior, LoadClass.LOADED))
    }

    @Test
    fun aHighRepSetCannotBreakTheEstimateRecord() {
        val prior = listOf(rec(100.0, 5, 1))
        val broken = PersonalRecords.detect(rec(60.0, 30, 2), prior, LoadClass.LOADED)
        assertTrue(PersonalRecordKind.ESTIMATED_ONE_REP_MAX !in broken)
    }

    @Test
    fun aggregatePriorsMatchTheListPath() {
        val prior = listOf(rec(100.0, 5, 1), rec(110.0, 3, 2), rec(110.0, 4, 3))
        val candidate = rec(112.5, 3, 4)
        val fromList = PersonalRecords.detect(candidate, prior, LoadClass.LOADED)
        val fromPriors = PersonalRecords.detect(
            candidate,
            PersonalRecords.RecordPriors.from(prior, candidate.weightKg),
            LoadClass.LOADED,
        )
        assertEquals(fromList, fromPriors)
        assertTrue(PersonalRecordKind.WEIGHT in fromPriors)
    }
}
