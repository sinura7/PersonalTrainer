package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RpeLoadModelTest {
    @Test
    fun goldenTable() {
        assertGolden(
            lastWeightLb = 135.0,
            lastReps = 10,
            lastRpe = 7,
            stepLb = 5.0,
            targetRir = 1,
            expectedWeightLb = 140.0,
            expectedReps = 9,
        )
        assertGolden(
            lastWeightLb = 135.0,
            lastReps = 10,
            lastRpe = 9,
            stepLb = 5.0,
            targetRir = 1,
            expectedWeightLb = 140.0,
            expectedReps = 8,
        )
        assertGolden(
            lastWeightLb = 100.0,
            lastReps = 12,
            lastRpe = 8,
            stepLb = 5.0,
            targetRir = 2,
            expectedWeightLb = 105.0,
            expectedReps = 9,
        )
    }

    @Test
    fun weightUpImpliesRepsDown() {
        val random = Random(42)
        repeat(200) {
            val lastWeight = 40.0 + random.nextDouble() * 120.0
            val lastReps = random.nextInt(3, 12)
            val lastRpe = random.nextInt(6, 9)
            val step = 2.5
            val nextWeight = lastWeight + step
            val suggestion = RpeLoadModel.suggestAfterWeightIncrease(
                lastWeightKg = WeightConverter.toKg(lastWeight, WeightUnit.LBS),
                lastReps = lastReps,
                lastRpe = lastRpe,
                proposedNextWeightKg = WeightConverter.toKg(nextWeight, WeightUnit.LBS),
                targetRir = 1,
            )
            if (suggestion.nextWeightKg > WeightConverter.toKg(lastWeight, WeightUnit.LBS)) {
                assertTrue(
                    "w=$lastWeight r=$lastReps @ $lastRpe -> ${suggestion.nextReps}",
                    suggestion.nextReps < lastReps,
                )
            }
        }
    }

    @Test
    fun lightDumbbellJumpHoldsLoadWhenRepsWouldCollapse() {
        val lastKg = WeightConverter.toKg(2.5, WeightUnit.KG)
        val nextKg = WeightConverter.toKg(4.5, WeightUnit.KG)
        val suggestion = RpeLoadModel.suggestAfterWeightIncrease(
            lastWeightKg = lastKg,
            lastReps = 12,
            lastRpe = 6,
            proposedNextWeightKg = nextKg,
            targetRir = 1,
        )
        assertEquals(RpeLoadModel.MATH_NOTE_HOLD_LOAD, suggestion.mathNote)
        assertEquals(lastKg, suggestion.nextWeightKg, 0.0001)
        assertEquals(12, suggestion.nextReps)
    }

    @Test
    fun capNoteExplainsForcedRepDrop() {
        val line = RpeLoadModel.oneLineMath(
            lastWeightKg = 100.0,
            lastReps = 5,
            lastRpe = 6,
            nextWeightKg = 102.5,
            nextReps = 4,
            unit = WeightUnit.KG,
            mathNote = RpeLoadModel.MATH_NOTE_CAP,
        )
        assertTrue(line.contains("capped"))
    }

    private fun assertGolden(
        lastWeightLb: Double,
        lastReps: Int,
        lastRpe: Int,
        stepLb: Double,
        targetRir: Int,
        expectedWeightLb: Double,
        expectedReps: Int,
    ) {
        val lastKg = WeightConverter.toKg(lastWeightLb, WeightUnit.LBS)
        val nextKg = WeightConverter.toKg(expectedWeightLb, WeightUnit.LBS)
        val suggestion = RpeLoadModel.suggestAfterWeightIncrease(
            lastWeightKg = lastKg,
            lastReps = lastReps,
            lastRpe = lastRpe,
            proposedNextWeightKg = nextKg,
            targetRir = targetRir,
        )
        assertEquals(expectedWeightLb, WeightConverter.toDisplayValue(suggestion.nextWeightKg, WeightUnit.LBS), 0.01)
        assertEquals(expectedReps, suggestion.nextReps)
    }
}
