package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.TrainingGoal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddASetPolicyTest {
    private fun set(weight: Double, reps: Int, rpe: Int?) = LoggedSetView(
        weightKg = weight,
        reps = reps,
        rpe = rpe,
        isWarmup = false,
    )

    private fun baseContext(
        goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
        targetSets: Int = 3,
        working: List<LoggedSetView> = listOf(
            set(100.0, 8, 7),
            set(100.0, 8, 7),
            set(100.0, 8, 6),
        ),
        lighterWeek: Boolean = false,
        extraAccepted: Boolean = false,
        dismissed: Boolean = false,
        weeklySets: Double = 4.0,
        extraReason: String = SetMicroRecCalculator.RPE_HOLD,
        loadBlocked: Boolean = true,
        manualExtraAfterPlan: Boolean = false,
    ) = AddASetPolicy.Context(
        goal = goal,
        primaryMuscle = CanonicalMuscle.CHEST,
        targetSets = targetSets,
        workingLogged = working.size,
        todayWorking = working,
        lighterWeek = lighterWeek,
        extraSetAlreadyAccepted = extraAccepted,
        addASetDismissedForExercise = dismissed,
        weeklyHardSetsForMuscle = weeklySets,
        muscleWeeklySetsByWeek = listOf(6.0, 5.0, 4.0),
        blockWeekIndex = 4,
        blockOpenerSetsForLift = 3,
        lastSessionMatchingSets = working,
        lastWorkingSet = working.last(),
        targetReps = 8,
        extraSetReasonCode = extraReason,
        loadProgressionBlocked = loadBlocked,
        manualExtraAfterLastPlanned = manualExtraAfterPlan,
    )

    @Test
    fun manualExtraAfterLastPlannedBlocksAddASet() {
        assertNull(AddASetPolicy.evaluate(baseContext(manualExtraAfterPlan = true)))
    }

    @Test
    fun strengthGoalBlocksAddASet() {
        assertFalse(AddASetPolicy.passesHardGates(baseContext(goal = TrainingGoal.STRENGTH)))
    }

    @Test
    fun deloadWeekBlocksAddASet() {
        assertFalse(AddASetPolicy.passesHardGates(baseContext(lighterWeek = true)))
    }

    @Test
    fun capBlocksSecondAddASet() {
        assertFalse(AddASetPolicy.passesHardGates(baseContext(extraAccepted = true)))
    }

    @Test
    fun preferLoadFirstBlocksAddASetOffer() {
        val ctx = baseContext(
            extraReason = SetMicroRecCalculator.IN_TANK,
            loadBlocked = false,
        )
        assertTrue(AddASetPolicy.readinessMet(ctx))
        assertTrue(AddASetPolicy.preferLoadFirst(ctx))
        assertNull(AddASetPolicy.evaluate(ctx))
    }

    @Test
    fun blockedLoadWithEasyRpeOffersAddASet() {
        val ctx = baseContext(
            extraReason = SetMicroRecCalculator.RPE_HOLD,
            loadBlocked = true,
            weeklySets = 4.0,
        )
        assertNotNull(AddASetPolicy.evaluate(ctx))
    }

    @Test
    fun moreThanThreePlannedSetsBlocks() {
        assertFalse(
            AddASetPolicy.passesHardGates(
                baseContext(
                    targetSets = 4,
                    working = List(4) { set(100.0, 8, 7) },
                ),
            ),
        )
    }
}
