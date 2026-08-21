package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What twelve weeks came to.
 *
 * The rule under all of these: a block review is about what MOVED, not what was biggest. "Most
 * volume" names whatever lift happens to be a squat; "moved most" names the lift you actually
 * got better at, which is the question twelve weeks was asked to answer.
 */
class BlockReviewTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 8, 17)
    private val block = TrainingBlock.startingIn(start, DayOfWeek.MONDAY)

    private fun dayMs(weeksIn: Long): Long =
        start.plusWeeks(weeksIn).atStartOfDay(zone).toInstant().toEpochMilli() + 36_000_000L

    private fun workout(
        id: String,
        atMs: Long,
        exerciseId: String = "ex-squat",
        name: String = "Squat",
        loadType: LoadType = LoadType.EXTERNAL,
        sets: List<Pair<Double, Int>>,
    ): WorkoutSession = session(
        id = id,
        finishedAt = atMs,
        date = atMs,
        sets = sets.mapIndexed { index, (weight, reps) ->
            set("$id-$index", id, exerciseId, name, weight, reps, at = atMs + index)
        },
        exercises = listOf(sessionExercise(exerciseId, name, "Quads", loadType)),
    )

    @Test
    fun anEmptyBlockSaysSoRatherThanReportingZeroes() {
        val review = BlockReviewBuilder.build(block, emptyList(), WeightUnit.KG, zone)
        assertTrue(review.isEmpty)
        assertEquals(0, review.sessions)
        assertEquals(12, review.weeks)
    }

    @Test
    fun onlySessionsInsideTheBlockCount() {
        // A caller that forgets to filter turns a block review into a career one.
        val before = workout("before", dayMs(-2), sets = listOf(100.0 to 5))
        val inside = workout("inside", dayMs(1), sets = listOf(100.0 to 5))
        val after = workout("after", dayMs(13), sets = listOf(100.0 to 5))
        val review = BlockReviewBuilder.build(block, listOf(before, inside, after), WeightUnit.KG, zone)
        assertEquals(1, review.sessions)
    }

    @Test
    fun twoWorkoutsInOneDayAreOneDayTrained() {
        val morning = workout("am", dayMs(1), sets = listOf(100.0 to 5))
        val evening = workout("pm", dayMs(1) + 3_600_000L, sets = listOf(100.0 to 5))
        val review = BlockReviewBuilder.build(block, listOf(morning, evening), WeightUnit.KG, zone)
        assertEquals(2, review.sessions)
        assertEquals(1, review.daysTrained)
    }

    @Test
    fun aLiftThatGotHeavierIsAMover() {
        val early = workout("a", dayMs(0), sets = listOf(100.0 to 5))
        val late = workout("b", dayMs(8), sets = listOf(120.0 to 5))
        val review = BlockReviewBuilder.build(block, listOf(early, late), WeightUnit.KG, zone)
        val mover = review.movers.single()
        assertEquals("Squat", mover.exerciseName)
        assertEquals("116.7 kg", mover.fromLabel)
        assertEquals("140 kg", mover.toLabel)
        assertTrue(mover.gain > 0.15)
    }

    @Test
    fun aBodyweightLiftMovesInRepsOnTheSameList() {
        // Eight pull-ups to fifteen is progress, and it used to be invisible to everything that
        // measured in kilograms.
        val early = workout("a", dayMs(0), "ex-pu", "Pull-Up", LoadType.BODYWEIGHT, listOf(0.0 to 8))
        val late = workout("b", dayMs(8), "ex-pu", "Pull-Up", LoadType.BODYWEIGHT, listOf(0.0 to 15))
        val review = BlockReviewBuilder.build(block, listOf(early, late), WeightUnit.KG, zone)
        val mover = review.movers.single()
        assertEquals("8 reps", mover.fromLabel)
        assertEquals("15 reps", mover.toLabel)
    }

    @Test
    fun oneSessionIsAPositionNotADirection() {
        val only = workout("a", dayMs(1), sets = listOf(100.0 to 5))
        assertTrue(BlockReviewBuilder.build(block, listOf(only), WeightUnit.KG, zone).movers.isEmpty())
    }

    @Test
    fun standingStillIsNotMoving() {
        val early = workout("a", dayMs(0), sets = listOf(100.0 to 5))
        val late = workout("b", dayMs(8), sets = listOf(100.0 to 5))
        assertTrue(BlockReviewBuilder.build(block, listOf(early, late), WeightUnit.KG, zone).movers.isEmpty())
    }

    @Test
    fun goingBackwardsIsNotMoving() {
        val early = workout("a", dayMs(0), sets = listOf(120.0 to 5))
        val late = workout("b", dayMs(8), sets = listOf(100.0 to 5))
        assertTrue(BlockReviewBuilder.build(block, listOf(early, late), WeightUnit.KG, zone).movers.isEmpty())
    }

    @Test
    fun moreRepsAtALighterBarIsStillProgress() {
        // Five at 100 and three at 105 are not ordered by the number on the bar. A review that
        // called the second one a regression would be wrong, which is why the loaded measure is
        // an estimated max rather than the weight.
        val early = workout("a", dayMs(0), sets = listOf(100.0 to 3))
        val late = workout("b", dayMs(8), sets = listOf(100.0 to 8))
        val review = BlockReviewBuilder.build(block, listOf(early, late), WeightUnit.KG, zone)
        assertTrue(review.movers.single().gain > 0.0)
    }

    @Test
    fun recordsAreJudgedAgainstEverythingBeforeThemNotJustTheBlock() {
        // A best set in week one is only a best if it beat what came before. Starting the
        // comparison at the block's first day hands a returning lifter a record for every lift
        // they touch.
        val history = workout("old", dayMs(-3), sets = listOf(150.0 to 5))
        val weekOne = workout("a", dayMs(0), sets = listOf(100.0 to 5))
        val review = BlockReviewBuilder.build(block, listOf(history, weekOne), WeightUnit.KG, zone)
        assertEquals(0, review.recordsBroken)
    }

    @Test
    fun theWorkIsCountedInBothUnits() {
        val barbell = workout("a", dayMs(1), sets = listOf(100.0 to 5, 100.0 to 5))
        val pullUps = workout("b", dayMs(2), "ex-pu", "Pull-Up", LoadType.BODYWEIGHT, listOf(0.0 to 10))
        val review = BlockReviewBuilder.build(block, listOf(barbell, pullUps), WeightUnit.KG, zone)
        assertEquals(SetWork(volumeKg = 1000.0, bodyweightReps = 10), review.work)
        assertEquals(3, review.workingSets)
    }

    @Test
    fun onlyTheTopFewMoversAreShown() {
        val sessions = (1..5).flatMap { n ->
            listOf(
                workout("a$n", dayMs(0), "ex-$n", "Lift $n", sets = listOf(100.0 to 5)),
                workout("b$n", dayMs(8), "ex-$n", "Lift $n", sets = listOf((100.0 + n * 10) to 5)),
            )
        }
        val review = BlockReviewBuilder.build(block, sessions, WeightUnit.KG, zone)
        assertEquals(BlockReviewBuilder.MOVERS_SHOWN, review.movers.size)
        // Best first.
        assertEquals("Lift 5", review.movers.first().exerciseName)
    }
}
