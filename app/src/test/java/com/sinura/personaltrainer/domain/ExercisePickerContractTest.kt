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
            selectedOrder = listOf(squat.id),
            catalog = listOf(squat),
        )
        assertTrue(multi.multiSelect)
        assertEquals(setOf("ex-1"), multi.selectedIds)
        assertEquals(listOf(squat), multi.cart)
        assertEquals(1, LiftCart.cartNumber(multi.selectedOrder, squat.id))
        val created = ExercisePickerEvent.Created("Good morning", "Hamstrings")
        assertEquals("Good morning", created.name)
        assertEquals(ExercisePickerEvent.Confirmed, ExercisePickerEvent.Confirmed)
    }

    @Test
    fun cartFollowsSelectedOrderNotCatalogOrder() {
        val row = squat.copy(id = "ex-2", name = "Row")
        val state = ExercisePickerState(
            query = "",
            results = emptyList(),
            title = "Add lifts",
            mode = ExercisePickerMode.MULTI_ADD,
            selectedOrder = listOf(row.id, squat.id),
            catalog = listOf(squat, row),
        )
        assertEquals(listOf("ex-2", "ex-1"), state.cart.map { it.id })
    }

    @Test
    fun cartOmitsIdsTheCatalogCannotResolve() {
        val state = ExercisePickerState(
            query = "",
            results = emptyList(),
            title = "Add lifts",
            mode = ExercisePickerMode.MULTI_ADD,
            selectedOrder = listOf(squat.id, "ghost"),
            catalog = listOf(squat),
        )
        assertEquals(listOf(squat), state.cart)
        assertEquals(setOf("ex-1", "ghost"), state.selectedIds)
    }

    @Test
    fun cartResolvesFromSearchResultsWhenCatalogHasNotCaughtUp() {
        val state = ExercisePickerState(
            query = "squat",
            results = listOf(squat),
            title = "Add lifts",
            mode = ExercisePickerMode.MULTI_ADD,
            selectedOrder = listOf(" ", squat.id, squat.id),
            catalog = emptyList(),
        )
        assertEquals(listOf(squat), state.cart)
        assertEquals(setOf("ex-1"), state.selectedIds)
    }
}
