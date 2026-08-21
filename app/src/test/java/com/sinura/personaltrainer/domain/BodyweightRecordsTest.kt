package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A calisthenics lifter could previously break no records at all.
 *
 * All three record kinds were statements about a bar — heaviest set, most reps *at that
 * weight*, estimated one-rep max — and a push-up has no bar. Someone going from eight reps to
 * twenty over three months set nothing, was told nothing, and appeared in no PR list.
 */
class BodyweightRecordsTest {
    private fun rec(weightKg: Double, reps: Int, at: Long) =
        ExerciseSetRecord("set-$at", "s", weightKg, reps, at)

    @Test
    fun moreRepsIsTheRecordABodyweightLiftChases() {
        val prior = listOf(rec(0.0, 8, 1), rec(0.0, 10, 2))
        assertEquals(
            setOf(PersonalRecordKind.REPS),
            PersonalRecords.detect(rec(0.0, 11, 3), prior, LoadClass.BODYWEIGHT),
        )
    }

    @Test
    fun equallingItIsStillNotBreakingIt() {
        val prior = listOf(rec(0.0, 10, 1))
        assertTrue(PersonalRecords.detect(rec(0.0, 10, 2), prior, LoadClass.BODYWEIGHT).isEmpty())
    }

    @Test
    fun aBodyweightLiftNeverClaimsAHeaviestOrAnEstimatedMax() {
        val history = listOf(rec(0.0, 8, 1), rec(0.0, 15, 2))
        val bests = PersonalRecords.bests(history, LoadClass.BODYWEIGHT)
        assertEquals(setOf(PersonalRecordKind.REPS), bests.keys)
        assertEquals(15.0, bests.getValue(PersonalRecordKind.REPS).value, 0.0001)
    }

    @Test
    fun aVestGetsAHeaviestOfItsOwn() {
        // The same eight pull-ups with ten more kilograms on is a better set, and nothing else
        // here would have noticed.
        val prior = listOf(rec(10.0, 8, 1))
        assertEquals(
            setOf(PersonalRecordKind.WEIGHT),
            PersonalRecords.detect(rec(20.0, 8, 2), prior, LoadClass.BODYWEIGHT_ADDED),
        )
        val bests = PersonalRecords.bests(listOf(rec(10.0, 8, 1), rec(20.0, 6, 2)), LoadClass.BODYWEIGHT_ADDED)
        assertEquals(setOf(PersonalRecordKind.REPS, PersonalRecordKind.WEIGHT), bests.keys)
        assertEquals(20.0, bests.getValue(PersonalRecordKind.WEIGHT).value, 0.0001)
        assertEquals(8.0, bests.getValue(PersonalRecordKind.REPS).value, 0.0001)
    }

    @Test
    fun anAssistedLiftHasNoHeaviest() {
        // More assistance is an easier set. A "heaviest assist" record would reward going
        // backwards, which is the whole reason assistance is classed apart from added load.
        val bests = PersonalRecords.bests(
            listOf(rec(30.0, 8, 1), rec(20.0, 8, 2)),
            LoadClass.BODYWEIGHT_ASSISTED,
        )
        assertEquals(setOf(PersonalRecordKind.REPS), bests.keys)
        assertFalse(
            PersonalRecordKind.WEIGHT in
                PersonalRecords.detect(rec(40.0, 8, 3), listOf(rec(20.0, 8, 1)), LoadClass.BODYWEIGHT_ASSISTED),
        )
    }

    @Test
    fun aLoadedLiftIsUntouched() {
        val prior = listOf(rec(100.0, 5, 1))
        val broken = PersonalRecords.detect(rec(110.0, 5, 2), prior, LoadClass.LOADED)
        assertTrue(PersonalRecordKind.WEIGHT in broken)
        assertFalse(PersonalRecordKind.REPS in broken)
    }

    @Test
    fun theFirstSetOfABodyweightLiftSetsNothing() {
        assertTrue(PersonalRecords.detect(rec(0.0, 20, 1), emptyList(), LoadClass.BODYWEIGHT).isEmpty())
    }
}
