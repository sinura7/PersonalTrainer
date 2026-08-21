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
    fun catalogHasExactly70EntriesAtVersion3() {
        // Batch 1 (37) + batch 2 (33). Batch 3 takes this to 98 at version 4.
        assertEquals(70, DefaultExercises.catalog().size)
        assertEquals(3, DefaultExercises.CATALOG_VERSION)
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
        //
        // Batch 1 is asserted as a PREFIX rather than the whole list: later batches append, and
        // an appended row must never be able to displace or re-order a frozen id. Checking the
        // prefix catches exactly that, and keeps working as the catalog grows.
        assertEquals(FROZEN_IDS, DefaultExercises.catalog().take(FROZEN_IDS.size).map { it.id })
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

    @Test
    fun idsAreUniqueAcrossEveryBatch() {
        val ids = DefaultExercises.catalog().map { it.id }
        assertEquals("a batch re-used an id", ids.size, ids.toSet().size)
    }

    @Test
    fun everyBuiltInHasCatalogMetadataAndRanksAreUnique() {
        // sortRank and searchTerms live in code beside the catalog rather than in the database,
        // so nothing but this test stops the two tables drifting. A lift missing from CatalogMeta
        // would silently sort last, behind every custom, with no nickname search.
        val ids = DefaultExercises.catalog().map { it.id }.toSet()
        assertEquals("CatalogMeta and the catalog disagree", ids, CatalogMeta.knownIds())

        val ranks = ids.map { CatalogMeta.sortRank(it) }
        assertEquals("two built-ins share a sortRank", ranks.size, ranks.toSet().size)
        assertTrue("a built-in has no sortRank", ranks.none { it == Int.MAX_VALUE })
    }

    @Test
    fun curationBucketsMatchThePlannedCounts() {
        // Buckets are a CURATION judgment — "how many chest lifts does the catalog offer" — and
        // are not derivable from junction data: the Hinge bucket's primaries are back and glutes,
        // not a "hinge" muscle. So the membership is stated here and the counts checked against
        // the plan, which is what stops a batch quietly shipping nine back lifts and three legs.
        BUCKETS.forEach { (bucket, ids) ->
            val expected = BUCKET_COUNTS.getValue(bucket)
            assertEquals("$bucket bucket", expected, ids.size)
        }
        val assigned = BUCKETS.values.flatten()
        assertEquals("a lift is in two buckets", assigned.size, assigned.toSet().size)
        assertEquals(
            "every built-in belongs to exactly one bucket",
            DefaultExercises.catalog().map { it.id }.toSet(),
            assigned.toSet(),
        )
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

        /** The plan's per-bucket totals at this catalog version. Batch 3 raises them to 98. */
        val BUCKET_COUNTS = mapOf(
            "Chest" to 12, "Back" to 15, "Hinge" to 2, "Shoulders" to 11, "Biceps" to 8,
            "Triceps" to 8, "Quads" to 7, "Hamstrings" to 2, "Glutes" to 1, "Calves" to 1,
            "Core" to 3,
        )

        val BUCKETS: Map<String, List<String>> = mapOf(
            "Chest" to listOf(
                "ex-barbell-bench-press", "ex-incline-bench-press", "ex-dumbbell-bench-press",
                "ex-push-up", "ex-chest-fly",
                "ex-incline-dumbbell-bench-press", "ex-machine-chest-press", "ex-dip",
                "ex-cable-fly", "ex-pec-deck", "ex-decline-bench-press",
                "ex-smith-machine-bench-press",
            ),
            "Back" to listOf(
                "ex-barbell-row", "ex-pendlay-row", "ex-one-arm-dumbbell-row", "ex-lat-pulldown",
                "ex-pull-up", "ex-chin-up", "ex-seated-cable-row",
                "ex-t-bar-row", "ex-machine-seated-row", "ex-chest-supported-dumbbell-row",
                "ex-inverted-row", "ex-close-grip-lat-pulldown", "ex-straight-arm-pulldown",
                "ex-barbell-shrug", "ex-dumbbell-shrug",
            ),
            // Hinge is a curation bucket, not a muscle: its lifts credit back and glutes.
            "Hinge" to listOf("ex-conventional-deadlift", "ex-trap-bar-deadlift"),
            "Shoulders" to listOf(
                "ex-overhead-press", "ex-seated-dumbbell-press", "ex-lateral-raise", "ex-face-pull",
                "ex-push-press", "ex-arnold-press", "ex-machine-shoulder-press",
                "ex-cable-lateral-raise", "ex-machine-lateral-raise", "ex-reverse-pec-deck",
                "ex-dumbbell-rear-delt-fly",
            ),
            "Biceps" to listOf(
                "ex-barbell-curl", "ex-dumbbell-curl",
                "ex-ez-bar-curl", "ex-hammer-curl", "ex-preacher-curl", "ex-incline-dumbbell-curl",
                "ex-cable-curl", "ex-machine-bicep-curl",
            ),
            "Triceps" to listOf(
                "ex-tricep-pushdown", "ex-skull-crusher", "ex-close-grip-bench-press",
                "ex-overhead-cable-triceps-extension", "ex-overhead-dumbbell-triceps-extension",
                "ex-machine-triceps-extension", "ex-diamond-push-up", "ex-bench-dip",
            ),
            "Quads" to listOf(
                "ex-barbell-back-squat", "ex-front-squat", "ex-goblet-squat",
                "ex-bulgarian-split-squat", "ex-walking-lunge", "ex-leg-press", "ex-leg-extension",
            ),
            "Hamstrings" to listOf("ex-romanian-deadlift", "ex-leg-curl"),
            "Glutes" to listOf("ex-hip-thrust"),
            "Calves" to listOf("ex-standing-calf-raise"),
            "Core" to listOf("ex-plank", "ex-hanging-leg-raise", "ex-cable-crunch"),
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
        val name = CatalogReviewRenderer.artifactName()
        val file = listOf(
            File("../docs/gameplan/artifacts/$name"),
            File("docs/gameplan/artifacts/$name"),
        ).firstOrNull { it.exists() }
        assertTrue("$name is not committed", file != null)
        assertEquals(
            "Re-render docs/gameplan/artifacts/$name from CatalogReviewRenderer",
            expected,
            file!!.readText(),
        )
    }
}
