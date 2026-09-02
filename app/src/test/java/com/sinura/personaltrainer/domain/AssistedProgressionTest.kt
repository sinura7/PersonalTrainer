package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An assisted machine progresses by taking help away.
 *
 * The stored weight for an assisted lift is the assistance — weight the machine takes OFF the
 * lifter. Every other load type stores weight added. The suggestion used to move the number the
 * same way for both, so hitting your target reps on an assisted pull-up was answered with
 * "add 2.5 kg", i.e. more help as a reward for succeeding, and missing by three was answered by
 * taking help away from someone already failing. Both directions were exactly backwards.
 */
class AssistedProgressionTest {
    private val step = IncrementTable.STEP_KG

    private fun assisted(lastAssistKg: Double, reps: Int, target: Int) =
        ProgressionCalculator.suggestWeightKg(
            lastWeightKg = lastAssistKg,
            lastWorkingReps = reps,
            targetReps = target,
            displayStep = step,
            weightMeaning = WeightMeaning.ASSISTANCE,
            unit = WeightUnit.KG,
        )

    @Test
    fun hittingTargetTakesAssistanceAway() {
        assertEquals(17.5, assisted(20.0, 8, 8), 0.001)
    }

    @Test
    fun beatingTargetAlsoTakesAssistanceAway() {
        assertEquals(17.5, assisted(20.0, 12, 8), 0.001)
    }

    @Test
    fun missingBadlyGivesAssistanceBack() {
        // The lifter is failing. More help is the answer, not less.
        assertEquals(22.5, assisted(20.0, 4, 8), 0.001)
    }

    @Test
    fun comingCloseHoldsTheSameAssistance() {
        assertEquals(20.0, assisted(20.0, 7, 8), 0.001)
        assertEquals(20.0, assisted(20.0, 6, 8), 0.001)
    }

    @Test
    fun assistanceStopsAtNoneRatherThanGoingNegative() {
        // Zero assist is the whole point of the machine: the first unassisted rep.
        assertEquals(0.0, assisted(2.5, 8, 8), 0.001)
        assertEquals(0.0, assisted(0.0, 8, 8), 0.001)
    }

    @Test
    fun aLoadedLiftStillMovesTheOtherWay() {
        val loaded = ProgressionCalculator.suggestWeightKg(
            lastWeightKg = 100.0,
            lastWorkingReps = 5,
            targetReps = 5,
            displayStep = step,
            weightMeaning = WeightMeaning.LIFTED,
            unit = WeightUnit.KG,
        )
        assertEquals(102.5, loaded, 0.001)
    }

    @Test
    fun aVestMovesTheSameWayAsABar() {
        // Added load is added load, whatever it is strapped to.
        val added = ProgressionCalculator.suggestWeightKg(
            lastWeightKg = 20.0,
            lastWorkingReps = 8,
            targetReps = 8,
            displayStep = step,
            weightMeaning = WeightMeaning.ADDED,
            unit = WeightUnit.KG,
        )
        assertEquals(22.5, added, 0.001)
    }

    // ---- what the app says about it ----

    private fun hint(loadType: LoadType, reps: Int, target: Int) = ProgressionCalculator.hint(
        exerciseId = "ex-1",
        exerciseName = "Assisted Pull-Up",
        lastWeightKg = 20.0,
        lastWorkingReps = reps,
        targetReps = target,
        displayStep = step,
        loadType = loadType,
        unit = WeightUnit.KG,
    )

    @Test
    fun theStripSaysDropAssistNotAddWeight() {
        val reason = ProgressionCopy.stripReason(hint(LoadType.ASSISTED, 8, 8), WeightUnit.KG)
        assertEquals("Hit target. Drop 2.5 kg of assist.", reason)
    }

    @Test
    fun theStripSaysAddAssistWhenTheLiftIsBeingMissed() {
        val reason = ProgressionCopy.stripReason(hint(LoadType.ASSISTED, 4, 8), WeightUnit.KG)
        assertEquals("Missed target. Add 2.5 kg of assist.", reason)
    }

    @Test
    fun aHoldNamesTheAssistanceAsAssistance() {
        // "Keep 20 kg" reads as if twenty kilograms were lifted. They were subtracted.
        val reason = ProgressionCopy.stripReason(hint(LoadType.ASSISTED, 7, 8), WeightUnit.KG)
        assertTrue(reason, reason.contains("20 kg of assist"))
    }

    @Test
    fun theCoachCardAgreesWithTheStrip() {
        val reason = ProgressionCopy.coachReason(hint(LoadType.ASSISTED, 8, 8), WeightUnit.KG)
        assertTrue(reason, reason.contains("drop 2.5 kg of assist"))
        assertTrue(reason, reason.contains("20 kg of assist×8"))
    }

    @Test
    fun aBarbellLiftIsUnchanged() {
        assertEquals(
            "Hit target. Add 2.5 kg.",
            ProgressionCopy.stripReason(hint(LoadType.EXTERNAL, 5, 5), WeightUnit.KG),
        )
    }

    @Test
    fun aVestIsNamedAsAddedLoad() {
        val reason = ProgressionCopy.stripReason(hint(LoadType.BODYWEIGHT_PLUS, 6, 8), WeightUnit.KG)
        assertTrue(reason, reason.contains("+20 kg"))
    }
}
