package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestFloorCopyTest {
    @Test
    fun lastSetLineUsesSetCopyForLoadedLifts() {
        assertEquals(
            "Last set · 100 kg × 5",
            RestFloorCopy.lastSetLine(100.0, 5, LoadClass.LOADED, WeightUnit.KG),
        )
    }

    @Test
    fun sessionTargetLineIsTheProgressionStrip() {
        val hint = ProgressionHint(
            exerciseId = "squat",
            exerciseName = "Squat",
            lastWeightKg = 100.0,
            lastReps = 5,
            targetReps = 5,
            suggestedWeightKg = 102.5,
            action = ProgressionAction.INCREASE,
            loadType = LoadType.EXTERNAL,
        )
        assertEquals(
            ProgressionCopy.stripReason(hint, WeightUnit.KG),
            RestFloorCopy.sessionTargetLine(hint, WeightUnit.KG),
        )
    }

    @Test
    fun contextUsesSelectedLiftLastSetAndHint() {
        val squat = sessionExercise("squat", "Squat", "Legs")
        val logged = set(
            id = "set-1",
            sessionId = "s1",
            exerciseId = "squat",
            name = "Squat",
            weightKg = 100.0,
            reps = 5,
            at = 1_000L,
        )
        val current = session(
            id = "s1",
            finishedAt = null,
            sets = listOf(logged),
            exercises = listOf(squat),
            date = 1_000L,
        )
        val hint = ProgressionHint(
            exerciseId = "squat",
            exerciseName = "Squat",
            lastWeightKg = 100.0,
            lastReps = 5,
            targetReps = 5,
            suggestedWeightKg = 102.5,
            action = ProgressionAction.INCREASE,
            loadType = LoadType.EXTERNAL,
        )
        val floor = RestFloorCopy.context(
            session = current,
            selectedExerciseId = "squat",
            hint = hint,
            unit = WeightUnit.KG,
        )
        assertEquals("Squat", floor.exerciseName)
        assertEquals("Last set · 100 kg × 5", floor.lastSetLine)
        assertEquals(ProgressionCopy.stripReason(hint, WeightUnit.KG), floor.sessionTargetLine)
    }

    @Test
    fun missingSessionHasNoFloorCopy() {
        val floor = RestFloorCopy.context(null, null, null, WeightUnit.KG)
        assertNull(floor.exerciseName)
        assertNull(floor.lastSetLine)
        assertNull(floor.sessionTargetLine)
    }
}
