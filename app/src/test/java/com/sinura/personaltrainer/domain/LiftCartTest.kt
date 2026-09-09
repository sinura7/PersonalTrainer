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

    @Test
    fun pickedIsTheStoredSessionPlusTheTapsStillInFlight() {
        val stored = listOf("squat", "row")
        assertEquals(stored, LiftCart.picked(stored, emptyList()))
        assertEquals(
            listOf("squat", "row", "bench"),
            LiftCart.picked(stored, listOf(PendingPick(id = "bench", adding = true))),
        )
        // A tap taking a lift out drops it from the numbering before the write lands, so
        // the row stops looking chosen on the same frame the finger leaves it.
        assertEquals(
            listOf("row"),
            LiftCart.picked(stored, listOf(PendingPick(id = "squat", adding = false))),
        )
        // An add for a lift the store already holds keeps its stored position, not a second.
        assertEquals(stored, LiftCart.picked(stored, listOf(PendingPick(id = "row", adding = true))))
    }

    @Test
    fun addsOnTapAnswersAgainstStoreAndFlight() {
        val stored = listOf("squat")
        assertFalse(LiftCart.addsOnTap(stored, emptyList(), "squat"))
        assertTrue(LiftCart.addsOnTap(stored, emptyList(), "row"))
        // The second tap on a row whose add has not landed yet must remove, not add again.
        assertFalse(LiftCart.addsOnTap(stored, listOf(PendingPick(id = "row", adding = true)), "row"))
        assertTrue(LiftCart.addsOnTap(stored, listOf(PendingPick(id = "squat", adding = false)), "squat"))
    }

    @Test
    fun recordReplacesTheIntentHeldForTheSameLift() {
        val once = LiftCart.record(pending = emptyList(), id = "squat", adding = true)
        assertEquals(listOf(PendingPick(id = "squat", adding = true)), once)
        val flipped = LiftCart.record(pending = once, id = " squat ", adding = false)
        assertEquals(listOf(PendingPick(id = "squat", adding = false)), flipped)
        val two = LiftCart.record(pending = flipped, id = "row", adding = true)
        assertEquals(listOf("squat", "row"), two.map { it.id })
        assertEquals(two, LiftCart.record(pending = two, id = "   ", adding = true))
    }

    @Test
    fun settleDropsOnlyTheTapsTheStoreAgreesWith() {
        val pending = listOf(
            PendingPick(id = "squat", adding = true),
            PendingPick(id = "row", adding = false),
            PendingPick(id = "bench", adding = true),
        )
        val settled = LiftCart.settle(listOf("squat", "row"), pending)
        assertEquals(listOf("row", "bench"), settled.map { it.id })
        assertTrue(LiftCart.settle(listOf("squat"), listOf(PendingPick("squat", true))).isEmpty())
        assertTrue(LiftCart.settle(emptyList(), listOf(PendingPick("squat", false))).isEmpty())
    }

    @Test
    fun forgetDropsOneLiftsTap() {
        val pending = listOf(PendingPick("squat", true), PendingPick("row", true))
        assertEquals(listOf("row"), LiftCart.forget(pending, " squat ").map { it.id })
        assertEquals(pending, LiftCart.forget(pending, "bench"))
    }

    @Test
    fun blankIdsAndDuplicatesAreIgnored() {
        assertEquals(listOf("squat"), LiftCart.sanitize(listOf(" squat ", "", "squat", " ")))
        assertEquals(
            listOf("squat"),
            LiftCart.picked(listOf(" squat ", "squat", " "), emptyList()),
        )
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
