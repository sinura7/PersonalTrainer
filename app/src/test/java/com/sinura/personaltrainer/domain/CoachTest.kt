package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachTest {
    @Test
    fun decideAgreesWithSetMicroRecForEveryV1Row() {
        for (row in SetMicroRecCalculatorTest.cases()) {
            val decision = Coach.decide(row.inputs)
            val rec = SetMicroRecCalculator.suggest(row.inputs)
            if (row.hidden) {
                assertNull(row.name, decision)
                assertNull(row.name, rec)
                continue
            }
            val got = checkNotNull(decision) { row.name }
            val expected = checkNotNull(rec) { row.name }
            assertEquals(row.name, expected.nextWeightKg, got.weightKg, 0.0001)
            assertEquals(row.name, expected.nextReps, got.reps)
            assertEquals(row.name, expected.nextRpe, got.rpe)
            assertEquals(row.name, expected.restSeconds, got.restSeconds)
            assertEquals(row.name, expected.anotherSetAdvised, got.anotherSetAdvised)
            assertEquals(row.name, expected.reasonCode, got.reasonCode)
            assertEquals(row.name, expected.trace, got.trace)
        }
    }

    @Test
    fun adjustedIsProgressionCalculatorAdjusted() {
        val viaCoach = Coach.adjusted(
            exerciseId = "ex-1",
            exerciseName = "Squat",
            lastWeightKg = 100.0,
            lastWorkingReps = 5,
            targetReps = 5,
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
            rpeEvidenceNewestFirst = listOf(8),
            lighterWeek = false,
        )
        val viaCalc = ProgressionCalculator.adjusted(
            exerciseId = "ex-1",
            exerciseName = "Squat",
            lastWeightKg = 100.0,
            lastWorkingReps = 5,
            targetReps = 5,
            loadType = LoadType.EXTERNAL,
            unit = WeightUnit.KG,
            rpeEvidenceNewestFirst = listOf(8),
            lighterWeek = false,
        )
        assertEquals(viaCalc, viaCoach)
    }
}
