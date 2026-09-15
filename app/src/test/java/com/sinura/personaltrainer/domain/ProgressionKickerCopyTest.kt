package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionKickerCopyTest {
    @Test
    fun hintMapsToHoldPlusAndBackOff() {
        assertEquals(
            ProgressionKickerCopy.HOLD,
            ProgressionKickerCopy.fromHint(hint(ProgressionAction.HOLD), WeightUnit.KG),
        )
        assertEquals(
            "+2.5",
            ProgressionKickerCopy.fromHint(hint(ProgressionAction.INCREASE), WeightUnit.KG),
        )
        assertEquals(
            "+5",
            ProgressionKickerCopy.fromHint(hint(ProgressionAction.INCREASE), WeightUnit.LBS),
        )
        assertEquals(
            ProgressionKickerCopy.BACK_OFF,
            ProgressionKickerCopy.fromHint(hint(ProgressionAction.DECREASE), WeightUnit.KG),
        )
        assertEquals(
            ProgressionKickerCopy.PLUS_REP,
            ProgressionKickerCopy.fromHint(
                hint(ProgressionAction.INCREASE, LoadType.BODYWEIGHT),
                WeightUnit.KG,
            ),
        )
    }

    @Test
    fun rpeHoldUsesTheHoldKicker() {
        val held = hint(ProgressionAction.INCREASE).let { RpeModifier.apply(it, listOf(9, 10)) }
        assertEquals(ProgressionAction.HOLD, held.action)
        assertEquals(ProgressionKickerCopy.HOLD, ProgressionKickerCopy.fromHint(held, WeightUnit.KG))
    }

    @Test
    fun editingAndLiftDoneHaveNoKicker() {
        assertNull(
            ProgressionKickerCopy.fromMicroRec(
                rec(SetMicroRecCalculator.EDITING),
                LoadClass.LOADED,
                WeightUnit.KG,
            ),
        )
        assertNull(
            ProgressionKickerCopy.fromMicroRec(
                rec(SetMicroRecCalculator.LIFT_DONE),
                LoadClass.LOADED,
                WeightUnit.KG,
            ),
        )
        assertEquals(
            ProgressionKickerCopy.HOLD,
            ProgressionKickerCopy.fromMicroRec(
                rec(SetMicroRecCalculator.RPE_HOLD),
                LoadClass.LOADED,
                WeightUnit.KG,
            ),
        )
        assertEquals(
            ProgressionKickerCopy.PLUS_REP,
            ProgressionKickerCopy.fromMicroRec(
                rec(SetMicroRecCalculator.CLIMB_REPS),
                LoadClass.LOADED,
                WeightUnit.KG,
            ),
        )
    }

    @Test
    fun aClimbOnTheHintIsPlusOneNotHold() {
        assertEquals(
            ProgressionKickerCopy.PLUS_REP,
            ProgressionKickerCopy.fromHint(
                hint(ProgressionAction.HOLD).copy(suggestedReps = 6),
                WeightUnit.KG,
            ),
        )
    }

    @Test
    fun aPinStackIncreaseIsFiveKgNotTheBarStep() {
        assertEquals(
            "+5",
            ProgressionKickerCopy.fromHint(
                hint(
                    action = ProgressionAction.INCREASE,
                    loadType = LoadType.STACK,
                ),
                WeightUnit.KG,
            ),
        )
        assertEquals(
            "+2",
            ProgressionKickerCopy.fromHint(
                hint(
                    action = ProgressionAction.INCREASE,
                    loadType = LoadType.EXTERNAL,
                ).copy(equipment = EquipmentType.DUMBBELL),
                WeightUnit.KG,
            ),
        )
    }

    private fun hint(
        action: ProgressionAction,
        loadType: LoadType = LoadType.EXTERNAL,
    ) = ProgressionHint(
        exerciseId = "ex-bench",
        exerciseName = "Bench",
        lastWeightKg = 100.0,
        lastReps = 5,
        targetReps = 5,
        suggestedWeightKg = 102.5,
        action = action,
        loadType = loadType,
    )

    private fun rec(reason: String) = SetMicroRec(
        nextWeightKg = 100.0,
        nextReps = 5,
        nextRpe = 8,
        previewOnly = false,
        showApply = true,
        reasonCode = reason,
        trace = RuleTrace.forMicroRec(
            reasonCodes = listOf(reason),
            nextWeightKg = 100.0,
            nextReps = 5,
            nextRpe = 8,
            nowMs = 0L,
            todayEpochDay = 0L,
        ),
    )
}
