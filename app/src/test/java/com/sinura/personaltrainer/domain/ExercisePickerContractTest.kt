package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePickerContractTest {
    private val squat = Exercise(
        id = "ex-1",
        name = "Squat",
        muscleGroup = "Quads",
        notes = "",
        isCustom = false,
    )

    @Test
    fun singleAddIsOneTapAndIgnoresSiblings() {
        val state = ExercisePickerState(
            query = "",
            results = listOf(squat),
            title = "Add a lift",
            mode = ExercisePickerMode.SINGLE_ADD,
            siblings = listOf(squat),
        )
        assertFalse(state.multiSelect)
        assertFalse(state.showSiblings)
    }

    @Test
    fun swapShowsSiblingsAndMultiAddConfirms() {
        val swap = ExercisePickerState(
            query = "",
            results = listOf(squat),
            title = "Swap lift",
            mode = ExercisePickerMode.SWAP,
            siblings = listOf(squat),
        )
        assertTrue(swap.showSiblings)
        val multi = ExercisePickerState(
            query = "squat",
            results = listOf(squat),
            title = "Add lifts",
            mode = ExercisePickerMode.MULTI_ADD,
            selectedIds = setOf(squat.id),
        )
        assertTrue(multi.multiSelect)
        assertEquals(setOf("ex-1"), multi.selectedIds)
        val created = ExercisePickerEvent.Created("Good morning", "Hamstrings")
        assertEquals("Good morning", created.name)
        assertEquals(ExercisePickerEvent.Confirmed, ExercisePickerEvent.Confirmed)
    }
}
