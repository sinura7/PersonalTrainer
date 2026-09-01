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
    fun moreRepsBoughtWithMoreAssistanceIsNotARecord() {
        // Eight pull-ups with ten kilograms of help, then nine with twenty. That is one more
        // rep and twice the machine — a worse set, and "most reps ever" was the badge for it.
        val prior = listOf(rec(10.0, 8, 1))
        assertTrue(
            PersonalRecords.detect(rec(20.0, 9, 2), prior, LoadClass.BODYWEIGHT_ASSISTED).isEmpty(),
        )
    }

    @Test
    fun moreRepsAtLessAssistanceIsARecord() {
        // The improvement the rule must not suppress while it is busy suppressing the other.
        val prior = listOf(rec(20.0, 8, 1))
        assertEquals(
            setOf(PersonalRecordKind.REPS),
            PersonalRecords.detect(rec(10.0, 9, 2), prior, LoadClass.BODYWEIGHT_ASSISTED),
        )
    }

    @Test
    fun moreRepsAtTheSameAssistanceIsARecord() {
        val prior = listOf(rec(10.0, 8, 1))
        assertEquals(
            setOf(PersonalRecordKind.REPS),
            PersonalRecords.detect(rec(10.0, 9, 2), prior, LoadClass.BODYWEIGHT_ASSISTED),
        )
    }

    @Test
    fun anEasierSetElsewhereInHistoryDoesNotUnlockABoughtRepRecord() {
        // Eight reps at ten kilograms of help is the standing record; three at thirty is an
        // easy day from months ago. Nine reps at twenty is still the assistance turned UP
        // against the record it claims to beat, and the existence of the easier set must not
        // launder it — which a rule that only asked "have you ever trained this easy?" did.
        val prior = listOf(rec(10.0, 8, 1), rec(30.0, 3, 2))
        assertTrue(
            PersonalRecords.detect(rec(20.0, 9, 3), prior, LoadClass.BODYWEIGHT_ASSISTED).isEmpty(),
        )
    }

    @Test
    fun anUnassistedRepRecordSurvivesAnEasierSetInHistory() {
        // The other side of the same shape: ten unassisted is the record, three at thirty
        // kilograms of help is history, and eleven unassisted beats the record on its own
        // terms. Nothing here may suppress it.
        val prior = listOf(rec(0.0, 10, 1), rec(30.0, 3, 2))
        assertEquals(
            setOf(PersonalRecordKind.REPS),
            PersonalRecords.detect(rec(0.0, 11, 3), prior, LoadClass.BODYWEIGHT_ASSISTED),
        )
    }

    @Test
    fun theRepCountStillHasToBeTheHighestEverNotJustTheHighestAtThisHelp() {
        // Ten unassisted, then three at thirty kilograms of help, then five at thirty. Five
        // beats the other assisted set, and is nowhere near the ten this lifter has done.
        val prior = listOf(rec(0.0, 10, 1), rec(30.0, 3, 2))
        assertTrue(
            PersonalRecords.detect(rec(30.0, 5, 3), prior, LoadClass.BODYWEIGHT_ASSISTED).isEmpty(),
        )
    }

    @Test
    fun anAssistedRuleDoesNotLeakIntoTheOtherRepsClasses() {
        // Bodyweight and vest lifts read the same weight column as ADDED load, where more is
        // harder. Gating their rep records on "some earlier set used at least this much" would
        // deny a bodyweight PR to anyone whose first set was heavier.
        assertEquals(
            setOf(PersonalRecordKind.REPS),
            PersonalRecords.detect(rec(0.0, 9, 2), listOf(rec(0.0, 8, 1)), LoadClass.BODYWEIGHT),
        )
        assertTrue(
            PersonalRecordKind.REPS in
                PersonalRecords.detect(rec(20.0, 9, 2), listOf(rec(10.0, 8, 1)), LoadClass.BODYWEIGHT_ADDED),
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
