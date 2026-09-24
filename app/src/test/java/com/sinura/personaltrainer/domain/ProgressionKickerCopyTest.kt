package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionKickerCopyTest {
    @Test
    fun anAddedLoadIsTheStepInTheUnitAndAFailedSetBacksOff() {
        assertEquals("+2.5", ProgressionKickerCopy.fromMicroRec(rec(SetMicroRecCalculator.IN_TANK), LoadClass.LOADED, WeightUnit.KG))
        assertEquals("+5", ProgressionKickerCopy.fromMicroRec(rec(SetMicroRecCalculator.IN_TANK), LoadClass.LOADED, WeightUnit.LBS))
        assertEquals(
            "a bodyweight lift has no plate to add",
            ProgressionKickerCopy.PLUS_REP,
            ProgressionKickerCopy.fromMicroRec(rec(SetMicroRecCalculator.IN_TANK), LoadClass.BODYWEIGHT, WeightUnit.KG),
        )
        assertEquals(
            ProgressionKickerCopy.BACK_OFF,
            ProgressionKickerCopy.fromMicroRec(rec(SetMicroRecCalculator.FAILED_DROP), LoadClass.LOADED, WeightUnit.KG),
        )
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
        assertEquals("Use suggestion", SetMicroRecCopy.USE_SUGGESTION)
        assertEquals("Keep my numbers", SetMicroRecCopy.KEEP_MY_NUMBERS)
        assertFalse(
            SetMicroRecCopy.visibleOnEntry(rec(SetMicroRecCalculator.LIFT_DONE)),
        )
    }

    @Test
    fun aPinStackIncreaseIsFiveKgNotTheBarStep() {
        assertEquals(
            "+5",
            ProgressionKickerCopy.fromMicroRec(
                rec(SetMicroRecCalculator.IN_TANK).copy(loadType = LoadType.STACK),
                LoadClass.LOADED,
                WeightUnit.KG,
            ),
        )
        assertEquals(
            "+2",
            ProgressionKickerCopy.fromMicroRec(
                rec(SetMicroRecCalculator.IN_TANK).copy(loadType = LoadType.EXTERNAL, equipment = EquipmentType.DUMBBELL),
                LoadClass.LOADED,
                WeightUnit.KG,
            ),
        )
    }

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

    @Test
    fun deltaLineSaysHoldWhenTheNumbersRepeatAndNothingOnAFirstSet() {
        assertEquals("Hold the load", SetMicroRecCopy.deltaLine(rec = rec(SetMicroRecCalculator.QUALITY), loadClass = LoadClass.LOADED, unit = WeightUnit.KG))
        assertEquals("Hold the load", SetMicroRecCopy.deltaLine(rec = rec(SetMicroRecCalculator.TOP_SET), loadClass = LoadClass.LOADED, unit = WeightUnit.KG))
        assertNull(SetMicroRecCopy.deltaLine(rec = rec(SetMicroRecCalculator.FIRST_SET), loadClass = LoadClass.LOADED, unit = WeightUnit.KG))
        assertNull(SetMicroRecCopy.deltaLine(rec = rec(SetMicroRecCalculator.WARMUP_DONE), loadClass = LoadClass.LOADED, unit = WeightUnit.KG))
        assertEquals("+1 rep", SetMicroRecCopy.deltaLine(rec = rec(SetMicroRecCalculator.CLIMB_REPS), loadClass = LoadClass.LOADED, unit = WeightUnit.KG))
    }
}
