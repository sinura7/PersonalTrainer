package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The preview's start weight follows the live Log's prefill order (spec section 6). */
class WorkoutPreviewStartWeightTest {
    private fun routine(targetKg: Double?) = Routine(
        id = "r", name = "Upper A", notes = "", createdAt = 0L, updatedAt = 0L,
        exercises = listOf(
            RoutineExercise(
                id = "re", routineId = "r",
                exercise = Exercise(id = "bench", name = "Bench Press", muscleGroup = "Chest", notes = "", isCustom = false),
                sortOrder = 0, targetSets = 3, targetReps = 8, targetWeightKg = targetKg, restSeconds = 90,
            ),
        ),
    )

    private fun start(targetKg: Double?, suggested: Map<String, Double>, last: Map<String, Double>) =
        WorkoutPreview.build(
            title = "Upper A", routine = routine(targetKg = targetKg), minutes = 40,
            lastWeightKg = last, unit = WeightUnit.KG, suggestedWeightKg = suggested,
        ).lifts.single().startWeightKg

    @Test
    fun suggestionComesFirst() {
        assertEquals(62.5, start(targetKg = 50.0, suggested = mapOf("bench" to 62.5), last = mapOf("bench" to 60.0))!!, 0.0)
    }

    @Test
    fun plannedTargetWhenThereIsNoSuggestion() {
        assertEquals(50.0, start(targetKg = 50.0, suggested = emptyMap(), last = mapOf("bench" to 60.0))!!, 0.0)
    }

    @Test
    fun lastTimeIsAHintNotAStartWeight() {
        assertNull(start(targetKg = null, suggested = emptyMap(), last = mapOf("bench" to 60.0)))
    }
}
