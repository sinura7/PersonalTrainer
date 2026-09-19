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
        assertEquals(LoadType.EXTERNAL, created.loadType)
        val bodyweight = ExercisePickerEvent.Created("Push-up", "Chest", LoadType.BODYWEIGHT)
        assertEquals(LoadType.BODYWEIGHT, bodyweight.loadType)
        // Multi-add has no confirm event: a tap is the write, and Dismissed is the way out.
        assertEquals(ExercisePickerEvent.Dismissed, ExercisePickerEvent.Dismissed)
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

    @Test
    fun chestHidesBackSquat() {
        val catalog = pickerCatalog()
        val chest = picker(catalog).visibleFor(CanonicalMuscle.CHEST)
        assertTrue(chest.none { it.id == "ex-barbell-back-squat" })
        assertTrue(chest.any { it.id == "ex-barbell-bench-press" })
        assertEquals(catalog, picker(catalog).visibleFor(null))
        val tagged = picker(
            listOf(
                squat,
                squat.copy(id = "ex-bench", name = "Barbell Bench Press", muscleGroup = "Chest"),
            ),
        ).visibleFor(CanonicalMuscle.CHEST)
        assertTrue(tagged.none { it.id == squat.id })
        assertTrue(tagged.any { it.muscleGroup == "Chest" })
    }

    @Test
    fun searchInsideChestStillFindsBench() {
        val searched = pickerCatalog().filter { it.name.contains("press", ignoreCase = true) }
        val chest = picker(searched, query = "press").visibleFor(CanonicalMuscle.CHEST)
        assertTrue(chest.any { it.id == "ex-barbell-bench-press" })
        assertTrue(chest.none { it.id == "ex-overhead-press" })
        assertTrue(chest.none { it.id == "ex-barbell-back-squat" })
    }

    @Test
    fun muscleChipsMatchBody() {
        assertEquals(
            listOf(
                "Chest", "Back", "Shoulders", "Biceps", "Triceps",
                "Quadriceps", "Hamstrings", "Glutes", "Calves", "Core",
            ),
            CanonicalMuscle.bodyMapOrder.map { it.displayName },
        )
    }

    private fun picker(results: List<Exercise>, query: String = "") = ExercisePickerState(
        query = query,
        results = results,
        title = "Add lifts",
        mode = ExercisePickerMode.MULTI_ADD,
    )

    private fun pickerCatalog(): List<Exercise> = DefaultExercises.catalog().map { seed ->
        Exercise(
            id = seed.id,
            name = seed.name,
            muscleGroup = seed.muscleGroup,
            notes = "",
            isCustom = false,
            equipment = seed.equipment,
            loadType = seed.loadType,
            movementKey = seed.movementKey,
            muscles = seed.credits,
        )
    }
}
