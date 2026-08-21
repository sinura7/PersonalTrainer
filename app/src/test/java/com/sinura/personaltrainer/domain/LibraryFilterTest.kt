package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The muscle filter, which used to be a text search wearing a chip's clothes.
 */
class LibraryFilterTest {
    private fun catalog(): List<Exercise> = DefaultExercises.catalog().map { seed ->
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

    @Test
    fun quadsIncludesLiftsWhereQuadsAreOnlyASecondary() {
        val filtered = LibraryFilter.apply(catalog(), CanonicalMuscle.QUADRICEPS).map { it.id }
        assertTrue("the leg extension is the obvious one", "ex-leg-extension" in filtered)
        // The point of the change: the sumo deadlift credits quads at 0.5 but is filed under
        // Glutes, so the old muscleGroup-string filter could never find it.
        assertTrue("sumo deadlift trains quads", "ex-sumo-deadlift" in filtered)
        assertFalse("a bench press does not", "ex-barbell-bench-press" in filtered)
    }

    @Test
    fun primariesOutrankSecondaryCreditLifts() {
        val filtered = LibraryFilter.apply(catalog(), CanonicalMuscle.QUADRICEPS)
        val squat = filtered.indexOfFirst { it.id == "ex-barbell-back-squat" }
        val sumo = filtered.indexOfFirst { it.id == "ex-sumo-deadlift" }
        assertTrue("a quad primary must rank above a quad secondary", squat < sumo)
    }

    @Test
    fun primariesAmongThemselvesFallBackToCatalogRank() {
        val filtered = LibraryFilter.apply(catalog(), CanonicalMuscle.CHEST).map { it.id }
        assertEquals("ex-barbell-bench-press", filtered.first())
    }

    @Test
    fun aCustomWithNoJunctionRowsFallsBackToItsChosenMuscleGroup() {
        // "quads" is an alias, not a muscleKey — it must still find the QUADRICEPS filter,
        // because that free string is the only thing the user ever told us about this lift.
        val mine = Exercise(
            id = "custom-1", name = "My Own Lift", muscleGroup = "quads", notes = "",
            isCustom = true, muscles = emptyList(),
        )
        assertTrue(LibraryFilter.matches(mine, CanonicalMuscle.QUADRICEPS))
        assertFalse(LibraryFilter.matches(mine, CanonicalMuscle.CHEST))
        assertEquals(listOf("custom-1"), LibraryFilter.apply(listOf(mine), CanonicalMuscle.QUADRICEPS).map { it.id })
    }

    @Test
    fun aCustomWithNoRecognisableGroupMatchesNothing() {
        val mine = Exercise(
            id = "custom-2", name = "Thing", muscleGroup = "vibes", notes = "",
            isCustom = true, muscles = emptyList(),
        )
        assertNull(LibraryFilter.creditWeight(mine, CanonicalMuscle.CHEST))
        assertTrue(LibraryFilter.apply(listOf(mine), CanonicalMuscle.CHEST).isEmpty())
    }

    @Test
    fun noFilterIsNotAFilter() {
        val all = catalog()
        assertEquals(all, LibraryFilter.apply(all, null))
    }

    @Test
    fun everyCanonicalMuscleExceptOtherIsReachableFromTheCatalog() {
        // If a muscle has no lifts, its chip is a dead end. With 98 lifts that must not happen.
        val present = LibraryFilter.musclesPresentIn(catalog())
        assertEquals(
            CanonicalMuscle.entries.filter { it != CanonicalMuscle.OTHER },
            present,
        )
    }
}

/**
 * The nav argument's round trip. A filter that silently opens unfiltered is the failure mode.
 */
class LibraryRouteContractTest {
    @Test
    fun enumNamesRoundTrip() {
        CanonicalMuscle.entries.forEach { muscle ->
            assertEquals(muscle, parse(muscle.name))
        }
    }

    @Test
    fun aStaleDisplayLabelFromAnOlderBuildStillResolves() {
        // "Quads" is QUADRICEPS's catalogLabel, which is what the argument used to carry.
        assertEquals(CanonicalMuscle.QUADRICEPS, parse("Quads"))
        assertEquals(CanonicalMuscle.HAMSTRINGS, parse("Hamstrings"))
    }

    @Test
    fun nothingUnrecognisableBecomesAFilter() {
        assertNull(parse(null))
        assertNull(parse(""))
        assertNull(parse("   "))
        assertNull(parse("vibes"))
    }

    /** The real implementation. `Route.Library.parseMuscle` is a one-line delegate to it. */
    private fun parse(raw: String?): CanonicalMuscle? = MuscleNormalizer.fromRouteArgument(raw)
}
