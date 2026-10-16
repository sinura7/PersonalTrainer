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
            val nextReps = RpeLoadModel.repsAfterWeightIncrease(
                lastWeightKg = WeightConverter.toKg(lastWeight, WeightUnit.LBS),
                lastReps = lastReps,
                lastRpe = lastRpe,
                nextWeightKg = WeightConverter.toKg(nextWeight, WeightUnit.LBS),
                targetRir = 1,
            )
            assertTrue(
                "w=$lastWeight r=$lastReps @ $lastRpe -> $nextWeight x $nextReps",
                nextReps < lastReps,
            )
        }
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
        val reps = RpeLoadModel.repsAfterWeightIncrease(
            lastWeightKg = lastKg,
            lastReps = lastReps,
            lastRpe = lastRpe,
            nextWeightKg = nextKg,
            targetRir = targetRir,
        )
        assertEquals(expectedReps, reps)
    }
}
