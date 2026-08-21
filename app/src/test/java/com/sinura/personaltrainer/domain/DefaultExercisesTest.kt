package com.sinura.personaltrainer.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog is data now, and data with judgment in it needs invariants a human cannot quietly
 * break. Each test here corresponds to something that would fail silently on a phone rather than
 * loudly in a build: a re-slugged id orphans history, a misspelled muscleKey stops crediting a
 * muscle, a movementKey outside the vocabulary breaks family grouping, and two built-ins sharing
 * a name makes the duplicate check unable to tell you which lift you are logging against.
 */
class DefaultExercisesTest {

    @Test
    fun catalogHasExactly37EntriesAtVersion2() {
        assertEquals(37, DefaultExercises.catalog().size)
        assertEquals(2, DefaultExercises.CATALOG_VERSION)
    }

    @Test
    fun everyEntryHasExactlyOnePrimaryWithWeightOne() {
        DefaultExercises.catalog().forEach { seed ->
            val primaries = seed.credits.filter { it.weight == 1.0 }
            assertEquals("${seed.id} must have exactly one primary", 1, primaries.size)
            assertEquals(
                "${seed.id}'s primary must come first",
                primaries.single(),
                seed.credits.first(),
            )
        }
    }

    @Test
    fun secondaryWeightsAreInHalfOpenRangeAndSumAtMostOne() {
        DefaultExercises.catalog().forEach { seed ->
            val secondaries = seed.credits.drop(1)
            secondaries.forEach { credit ->
                assertTrue(
                    "${seed.id}: ${credit.muscleKey} weight ${credit.weight} outside (0, 0.5]",
                    credit.weight > 0.0 && credit.weight <= 0.5,
                )
            }
            val total = secondaries.sumOf { it.weight }
            assertTrue("${seed.id}: secondaries sum to $total, above 1.0", total <= 1.0 + EPSILON)
        }
    }

    @Test
    fun nameKeysAreUniqueAmongBuiltIns() {
        val keys = DefaultExercises.catalog().map { MuscleNormalizer.nameKeyOf(it.name) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun idsMatchFrozenV1Slugs() {
        // Two assertions in one, deliberately. The literal list is what history points at and is
        // frozen forever; the slug comparison proves the literals were transcribed from v1's
        // generator rather than invented.
        assertEquals(FROZEN_IDS, DefaultExercises.catalog().map { it.id })
        DefaultExercises.catalog().forEach { seed ->
            assertEquals(
                "${seed.name} no longer slugs to its frozen id",
                seed.id,
                DefaultExercises.slugOf(seed.name),
            )
        }
    }

    @Test
    fun allMuscleKeysNormalizeToCanonicalNonOther() {
        val legal = CanonicalMuscle.entries
            .filter { it != CanonicalMuscle.OTHER }
            .map { it.name.lowercase() }
            .toSet()
        DefaultExercises.catalog().forEach { seed ->
            seed.credits.forEach { credit ->
                // The exact-match half is what makes spelling drift a build failure: "quads"
                // normalizes fine through the alias index and would pass the weaker check alone.
                assertTrue(
                    "${seed.id}: '${credit.muscleKey}' is not a canonical muscle key",
                    credit.muscleKey in legal,
                )
                val resolved = MuscleNormalizer.resolveKey(credit.muscleKey)
                assertTrue(
                    "${seed.id}: '${credit.muscleKey}' resolves to OTHER",
                    resolved != null && resolved != CanonicalMuscle.OTHER,
                )
            }
        }
    }

    @Test
    fun movementKeysComeFromTheClosedVocabulary() {
        DefaultExercises.catalog().forEach { seed ->
            assertTrue(
                "${seed.id}: '${seed.movementKey}' is not a known family",
                seed.movementKey in DefaultExercises.MOVEMENT_FAMILIES,
            )
        }
        // Every declared family is actually used; an unused one is a typo waiting to be adopted.
        val used = DefaultExercises.catalog().mapNotNull { it.movementKey }.toSet()
        assertEquals(DefaultExercises.MOVEMENT_FAMILIES, used)
    }

    private companion object {
        const val EPSILON = 1e-9

        val FROZEN_IDS = listOf(
            "ex-barbell-back-squat", "ex-front-squat", "ex-goblet-squat",
            "ex-bulgarian-split-squat", "ex-walking-lunge", "ex-leg-press", "ex-leg-extension",
            "ex-conventional-deadlift", "ex-romanian-deadlift", "ex-trap-bar-deadlift",
            "ex-hip-thrust", "ex-leg-curl", "ex-standing-calf-raise", "ex-barbell-bench-press",
            "ex-incline-bench-press", "ex-dumbbell-bench-press", "ex-push-up", "ex-chest-fly",
            "ex-overhead-press", "ex-seated-dumbbell-press", "ex-lateral-raise", "ex-face-pull",
            "ex-barbell-row", "ex-pendlay-row", "ex-one-arm-dumbbell-row", "ex-lat-pulldown",
            "ex-pull-up", "ex-chin-up", "ex-seated-cable-row", "ex-barbell-curl",
            "ex-dumbbell-curl", "ex-tricep-pushdown", "ex-skull-crusher",
            "ex-close-grip-bench-press", "ex-plank", "ex-hanging-leg-raise", "ex-cable-crunch",
        )
    }
}

/**
 * The review artifact and the catalog cannot drift apart: change one without the other and this
 * fails. Resolved relative to the module directory because the test lane runs with `app/` as its
 * working directory under Gradle.
 */
class CatalogReviewArtifactTest {
    @Test
    fun artifactMatchesCatalog() {
        val expected = CatalogReviewRenderer.render()
        val file = listOf(
            File("../docs/gameplan/artifacts/catalog-v2-review.md"),
            File("docs/gameplan/artifacts/catalog-v2-review.md"),
        ).firstOrNull { it.exists() }
        assertTrue("catalog-v2-review.md is not committed", file != null)
        assertEquals(
            "Re-render docs/gameplan/artifacts/catalog-v2-review.md from CatalogReviewRenderer",
            expected,
            file!!.readText(),
        )
    }
}
