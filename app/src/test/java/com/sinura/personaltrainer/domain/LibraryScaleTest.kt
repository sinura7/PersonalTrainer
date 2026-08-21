package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four pure pieces that make 98 lifts usable: escaping, nicknames, families, and order.
 */
class LikeEscaperTest {
    @Test
    fun percentIsNoLongerAWildcard() {
        // The reported shape: searching "100%" matched everything starting with 100.
        assertEquals("100\\%", LikeEscaper.escape("100%"))
    }

    @Test
    fun underscoreIsNoLongerASingleCharacterWildcard() {
        assertEquals("bench\\_press", LikeEscaper.escape("bench_press"))
    }

    @Test
    fun backslashIsEscapedBeforeTheWildcardsNotAfter() {
        // Order matters: escaping wildcards first, then backslashes, would find the backslash
        // this function just inserted and double it — turning \% into \\% , which is an escaped
        // backslash followed by a LIVE wildcard.
        assertEquals("\\\\", LikeEscaper.escape("\\"))
        assertEquals("\\\\\\%", LikeEscaper.escape("\\%"))
    }

    @Test
    fun ordinaryTypingIsUntouched() {
        assertEquals("bench press", LikeEscaper.escape("bench press"))
        assertEquals("", LikeEscaper.escape(""))
    }
}

class CatalogMetaSearchTest {
    @Test
    fun ohpFindsTheOverheadPress() {
        assertTrue(CatalogMeta.matchesSearchTerms("ohp", "ex-overhead-press"))
        assertTrue(CatalogMeta.matchesSearchTerms("OHP", "ex-overhead-press"))
    }

    @Test
    fun rdlFindsEveryRomanianDeadlift() {
        // The query is looked for INSIDE each term, so "rdl" reaches "db rdl" too. All three
        // RDLs in the catalog must answer to it, not just the one whose term is exactly "rdl".
        val hits = DefaultExercises.catalog()
            .map { it.id }
            .filter { CatalogMeta.matchesSearchTerms("rdl", it) }
        assertEquals(
            listOf(
                "ex-romanian-deadlift",
                "ex-dumbbell-romanian-deadlift",
                "ex-single-leg-romanian-deadlift",
            ).sorted(),
            hits.sorted(),
        )
    }

    @Test
    fun aBlankQueryMatchesNothing() {
        // An empty needle is a substring of every string; without this guard, clearing the
        // search box would make every lift an alias hit.
        assertFalse(CatalogMeta.matchesSearchTerms("", "ex-overhead-press"))
        assertFalse(CatalogMeta.matchesSearchTerms("   ", "ex-overhead-press"))
    }

    @Test
    fun aCustomHasNoNicknamesAndSortsLast() {
        assertEquals(emptySet<String>(), CatalogMeta.searchTerms("custom-1"))
        assertFalse(CatalogMeta.matchesSearchTerms("ohp", "custom-1"))
        assertEquals(Int.MAX_VALUE, CatalogMeta.sortRank("custom-1"))
    }
}

class LibraryGroupingTest {
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
    fun theBenchPressFamilyHoldsEveryBenchVariant() {
        val families = LibraryGrouping.group(catalog())
        val bench = families.single { it.movementKey == "bench-press" }
        assertEquals("Bench Press", bench.label)
        assertEquals(8, bench.members.size)
        assertFalse(bench.plain)
        // Ordered by rank, so the barbell bench leads its own family.
        assertEquals("ex-barbell-bench-press", bench.members.first().id)
    }

    @Test
    fun aSingleMemberFamilyRendersPlain() {
        val families = LibraryGrouping.group(catalog())
        val legPress = families.single { it.movementKey == "leg-press" }
        assertEquals(1, legPress.members.size)
        assertTrue("a one-lift family must not get a header", legPress.plain)
    }

    @Test
    fun familiesLeadWithTheirBestLiftNotTheirFirstLetter() {
        val families = LibraryGrouping.group(catalog())
        // Alphabetically this list starts at back-extension; by rank it starts at squat, which
        // is the whole reason families are ordered by member rank.
        assertEquals("squat", families.first().movementKey)
        assertTrue(
            "rollout must not outrank the squat",
            families.indexOfFirst { it.movementKey == "rollout" } > 0,
        )
    }

    @Test
    fun customsWithNoFamilyLandInOneBucketAtTheEnd() {
        val mine = Exercise(
            id = "custom-1", name = "My Own Lift", muscleGroup = "Chest", notes = "",
            isCustom = true, movementKey = null,
        )
        val families = LibraryGrouping.group(catalog() + mine)
        val last = families.last()
        assertEquals(LibraryGrouping.UNGROUPED_KEY, last.movementKey)
        assertEquals(LibraryGrouping.UNGROUPED_LABEL, last.label)
        assertEquals(listOf("custom-1"), last.members.map { it.id })
    }

    @Test
    fun siblingsAreTheRestOfTheFamilyMinusWhatIsAlreadyThere() {
        val all = catalog()
        val barbell = all.single { it.id == "ex-barbell-bench-press" }
        val siblings = LibraryGrouping.siblings(barbell, all)
        assertEquals(7, siblings.size)
        assertFalse("a lift is never its own sibling", siblings.any { it.id == barbell.id })

        val excluded = LibraryGrouping.siblings(
            barbell,
            all,
            exclude = setOf("ex-incline-bench-press", "ex-dumbbell-bench-press"),
        )
        assertEquals(5, excluded.size)
    }

    @Test
    fun aLiftWithNoFamilyHasNoSiblings() {
        val mine = Exercise(
            id = "custom-1", name = "My Own Lift", muscleGroup = "Chest", notes = "",
            isCustom = true, movementKey = null,
        )
        assertEquals(emptyList<Exercise>(), LibraryGrouping.siblings(mine, catalog() + mine))
    }
}

class ExerciseOrderingTest {
    private fun lift(id: String, name: String) = Exercise(
        id = id, name = name, muscleGroup = "Chest", notes = "", isCustom = false,
    )

    @Test
    fun aLiftLoggedYesterdayBeatsAHigherRankedOneNeverLogged() {
        val squat = lift("ex-barbell-back-squat", "Barbell Back Squat")   // rank 100
        val deadBug = lift("ex-dead-bug", "Dead Bug")                      // rank 625
        val ordered = ExerciseOrdering.pickerOrder(
            listOf(squat, deadBug),
            lastLoggedById = mapOf("ex-dead-bug" to 1_700_000_000_000L),
        )
        assertEquals(listOf("ex-dead-bug", "ex-barbell-back-squat"), ordered.map { it.id })
    }

    @Test
    fun neverLoggedLiftsFallBackToCatalogRank() {
        val deadBug = lift("ex-dead-bug", "Dead Bug")
        val squat = lift("ex-barbell-back-squat", "Barbell Back Squat")
        val ordered = ExerciseOrdering.pickerOrder(listOf(deadBug, squat), emptyMap())
        assertEquals(listOf("ex-barbell-back-squat", "ex-dead-bug"), ordered.map { it.id })
    }

    @Test
    fun theMoreRecentOfTwoLoggedLiftsComesFirst() {
        val a = lift("ex-barbell-back-squat", "Barbell Back Squat")
        val b = lift("ex-dead-bug", "Dead Bug")
        val ordered = ExerciseOrdering.pickerOrder(
            listOf(a, b),
            mapOf("ex-barbell-back-squat" to 1L, "ex-dead-bug" to 2L),
        )
        assertEquals(listOf("ex-dead-bug", "ex-barbell-back-squat"), ordered.map { it.id })
    }

    @Test
    fun customsSortAfterBuiltInsAndThenByName() {
        val zebra = lift("custom-z", "Zebra Lift")
        val apple = lift("custom-a", "Apple Lift")
        val squat = lift("ex-barbell-back-squat", "Barbell Back Squat")
        val ordered = ExerciseOrdering.catalogOrder(listOf(zebra, apple, squat))
        assertEquals(listOf("ex-barbell-back-squat", "custom-a", "custom-z"), ordered.map { it.id })
    }
}
