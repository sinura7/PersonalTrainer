package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftCartTest {
    private val squat = Exercise(
        id = "squat",
        name = "Squat",
        muscleGroup = "Quads",
        notes = "",
        isCustom = false,
    )
    private val row = squat.copy(id = "row", name = "Row", muscleGroup = "Back")
    private val bench = squat.copy(id = "bench", name = "Bench", muscleGroup = "Chest")

    @Test
    fun toggleAppendsThenRemovesWithoutShufflingTheRest() {
        val afterSquat = LiftCart.toggle(emptyList(), "squat")
        val afterRow = LiftCart.toggle(afterSquat, "row")
        val afterBench = LiftCart.toggle(afterRow, "bench")
        assertEquals(listOf("squat", "row", "bench"), afterBench)
        assertEquals(listOf("squat", "bench"), LiftCart.toggle(afterBench, "row"))
        assertEquals(afterRow, LiftCart.toggle(afterBench, "bench"))
    }

    @Test
    fun blankIdsAndDuplicatesAreIgnored() {
        assertEquals(emptyList<String>(), LiftCart.toggle(emptyList(), "  "))
        assertEquals(listOf("squat"), LiftCart.sanitize(listOf(" squat ", "", "squat", " ")))
        assertEquals(listOf("squat", "row"), LiftCart.toggle(listOf("squat", "squat"), "row"))
        assertEquals(listOf("squat"), LiftCart.toggle(listOf("squat", "squat"), "  "))
    }

    @Test
    fun cartNumberIsOneBasedTapOrder() {
        val order = listOf("squat", "row", "bench")
        assertEquals(1, LiftCart.cartNumber(order, "squat"))
        assertEquals(2, LiftCart.cartNumber(order, "row"))
        assertEquals(3, LiftCart.cartNumber(order, "bench"))
        assertNull(LiftCart.cartNumber(order, "curl"))
        assertNull(LiftCart.cartNumber(emptyList(), "squat"))
        assertNull(LiftCart.cartNumber(order, "  "))
    }

    @Test
    fun mergeSourcesKeepsPrimaryAndAppendsUnknownExtras() {
        val created = squat.copy(id = "new", name = "Good morning")
        val merged = LiftCart.mergeSources(listOf(squat, row), listOf(squat, created))
        assertEquals(listOf("squat", "row", "new"), merged.map { it.id })
    }

    @Test
    fun planConfirmPreservesOrderSkipsAlreadyAndBlocksMissing() {
        val sources = listOf(squat, row, bench)
        val ok = LiftCart.planConfirm(
            order = listOf("row", "squat", "bench"),
            sources = sources,
            already = setOf("squat"),
        )
        assertFalse(ok.blocked)
        assertEquals(listOf("row", "bench"), ok.toAdd.map { it.id })

        val allKnown = LiftCart.planConfirm(
            order = listOf("squat"),
            sources = sources,
            already = setOf("squat"),
        )
        assertTrue(allKnown.nothingNew)

        val missing = LiftCart.planConfirm(
            order = listOf("row", "ghost"),
            sources = sources,
            already = emptySet(),
        )
        assertTrue(missing.blocked)
        assertEquals(listOf("ghost"), missing.missingIds)
        assertTrue(missing.toAdd.isEmpty())
    }

    @Test
    fun visibleResultsGapFillsUntilTheCatalogCatchesUp() {
        val created = squat.copy(id = "new", name = "Good morning")
        assertEquals(
            listOf("new", "squat"),
            LiftCart.visibleResults(listOf(squat), listOf(created), "").map { it.id },
        )
        assertEquals(
            listOf("squat", "new"),
            LiftCart.visibleResults(listOf(squat, created), listOf(created), "").map { it.id },
        )
        assertEquals(
            listOf("squat"),
            LiftCart.visibleResults(listOf(squat), listOf(created), "squ").map { it.id },
        )
        assertEquals(
            listOf("new"),
            LiftCart.visibleResults(emptyList(), listOf(created), "good").map { it.id },
        )
    }
}
