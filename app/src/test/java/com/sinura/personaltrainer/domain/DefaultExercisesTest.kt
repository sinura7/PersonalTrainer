package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.domain.EquipmentType
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
    fun catalogHasExactly129EntriesAtVersion6() {
        // Batch 1 (37) + batch 2 (33) + batch 3 (28) + batch 4 (3) + batch 5 (28 Hyper Pro).
        assertEquals(129, DefaultExercises.catalog().size)
        assertEquals(6, DefaultExercises.CATALOG_VERSION)
    }

    @Test
    fun hyperProBatchIsTheOfficialTwentyEight() {
        val hyper = DefaultExercises.catalog().filter { it.equipment == EquipmentType.HYPER_PRO }
        assertEquals(OFFICIAL_HYPER_PRO_NAMES.size, hyper.size)
        assertEquals(OFFICIAL_HYPER_PRO_NAMES, hyper.map { it.name })
        hyper.forEach { seed ->
            assertTrue(seed.id.startsWith("ex-hyper-pro-"))
        }
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
    fun assistedSeedsReachTheAssistanceMachineryTheyWereAddedFor() {
        // LoadType.ASSISTED existed for a release with no catalog row using it, so nothing
        // proved the wiring end to end. These three rows are the proof: a machine row whose
        // weight column still read "kg lifted", or whose progression added assistance on a
        // good set, would be a worse answer than not shipping the row at all.
        val assisted = DefaultExercises.catalog().filter { it.loadType == LoadType.ASSISTED }
        assertEquals(3, assisted.size)
        assisted.forEach { seed ->
            val loadClass = LoadClass.of(seed.loadType)
            assertEquals("${seed.id}", LoadClass.BODYWEIGHT_ASSISTED, loadClass)
            assertEquals("${seed.id}", WeightMeaning.ASSISTANCE, loadClass.weightMeaning)
            assertTrue("${seed.id} is measured in reps", loadClass.repsAreTheMeasure)
            // Less assistance on a good set, not more. The sign flip is the whole point.
            val next = ProgressionCalculator.suggestWeightKg(
                lastWeightKg = 30.0,
                lastWorkingReps = 8,
                targetReps = 8,
                stepKg = IncrementTable.stepKg(seed.loadType, WeightUnit.KG),
                weightMeaning = loadClass.weightMeaning,
            )
            assertTrue("${seed.id} suggested $next, which is not lighter assistance", next < 30.0)
        }
    }

    @Test
    fun assistedVariantsNeverOutrankTheLiftTheyLeadTo() {
        // The generator picks a family's lift by lowest sortRank, so an assisted row ranked
        // ahead of its sibling would quietly put the machine in every generated programme —
        // including for someone who can already do the free version.
        listOf(
            "ex-assisted-pull-up" to "ex-pull-up",
            "ex-assisted-chin-up" to "ex-chin-up",
            "ex-assisted-dip" to "ex-dip",
        ).forEach { (assisted, free) ->
            assertTrue(
                "$assisted outranks $free",
                CatalogMeta.sortRank(assisted) > CatalogMeta.sortRank(free),
            )
        }
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

        /** Official laundry list, in catalog order, from freakathlete.ca/pages/exercise-list. */
        val OFFICIAL_HYPER_PRO_NAMES = listOf(
            "Hyper Pro 45-Degree Back Extension",
            "Hyper Pro 90-Degree Back Extension",
            "Hyper Pro Bicep Curl",
            "Hyper Pro Bulgarian Split Squat",
            "Hyper Pro Calf Stretch",
            "Hyper Pro Couch Stretch",
            "Hyper Pro Elephant Walk",
            "Hyper Pro External Rotator",
            "Hyper Pro Face Pull",
            "Hyper Pro GHD Sit-Up",
            "Hyper Pro Glute Ham Raise",
            "Hyper Pro Hamstring Curl",
            "Hyper Pro Hip Thrust",
            "Hyper Pro Incline Pigeon",
            "Hyper Pro KOT Squat",
            "Hyper Pro Leg Extension",
            "Hyper Pro Leg Raise",
            "Hyper Pro Nordic Curl",
            "Hyper Pro Pullover",
            "Hyper Pro Push-Up",
            "Hyper Pro QL Raise",
            "Hyper Pro Reverse Hyper",
            "Hyper Pro Reverse Nordic",
            "Hyper Pro Russian Twist",
            "Hyper Pro Sit-Up",
            "Hyper Pro Standing Row",
            "Hyper Pro Trap 3 Raise",
            "Hyper Pro Woodchop",
        )

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

        /** The plan's per-bucket totals for the 129-lift catalog. */
        val BUCKET_COUNTS = mapOf(
            "Chest" to 14, "Back" to 20, "Hinge" to 9, "Shoulders" to 13, "Biceps" to 9,
            "Triceps" to 8, "Quads" to 17, "Hamstrings" to 10, "Glutes" to 9, "Calves" to 5,
            "Core" to 15,
        )

        val BUCKETS: Map<String, List<String>> = mapOf(
            "Chest" to listOf(
                "ex-barbell-bench-press", "ex-incline-bench-press", "ex-dumbbell-bench-press",
                "ex-push-up", "ex-chest-fly",
                "ex-incline-dumbbell-bench-press", "ex-machine-chest-press", "ex-dip",
                "ex-cable-fly", "ex-pec-deck", "ex-decline-bench-press",
                "ex-smith-machine-bench-press", "ex-assisted-dip",
                "ex-hyper-pro-push-up",
            ),
            "Back" to listOf(
                "ex-barbell-row", "ex-pendlay-row", "ex-one-arm-dumbbell-row", "ex-lat-pulldown",
                "ex-pull-up", "ex-chin-up", "ex-seated-cable-row",
                "ex-t-bar-row", "ex-machine-seated-row", "ex-chest-supported-dumbbell-row",
                "ex-inverted-row", "ex-close-grip-lat-pulldown", "ex-straight-arm-pulldown",
                "ex-barbell-shrug", "ex-dumbbell-shrug",
                "ex-assisted-pull-up", "ex-assisted-chin-up",
                "ex-hyper-pro-standing-row", "ex-hyper-pro-trap-3-raise",
                "ex-hyper-pro-pullover",
            ),
            // Hinge is a curation bucket, not a muscle: its lifts credit back and glutes.
            "Hinge" to listOf(
                "ex-conventional-deadlift", "ex-trap-bar-deadlift",
                "ex-sumo-deadlift", "ex-kettlebell-swing", "ex-back-extension",
                "ex-hyper-pro-45-degree-back-extension", "ex-hyper-pro-90-degree-back-extension",
                "ex-hyper-pro-reverse-hyper", "ex-hyper-pro-ql-raise",
            ),
            "Shoulders" to listOf(
                "ex-overhead-press", "ex-seated-dumbbell-press", "ex-lateral-raise", "ex-face-pull",
                "ex-push-press", "ex-arnold-press", "ex-machine-shoulder-press",
                "ex-cable-lateral-raise", "ex-machine-lateral-raise", "ex-reverse-pec-deck",
                "ex-dumbbell-rear-delt-fly",
                "ex-hyper-pro-face-pull", "ex-hyper-pro-external-rotator",
            ),
            "Biceps" to listOf(
                "ex-barbell-curl", "ex-dumbbell-curl",
                "ex-ez-bar-curl", "ex-hammer-curl", "ex-preacher-curl", "ex-incline-dumbbell-curl",
                "ex-cable-curl", "ex-machine-bicep-curl",
                "ex-hyper-pro-bicep-curl",
            ),
            "Triceps" to listOf(
                "ex-tricep-pushdown", "ex-skull-crusher", "ex-close-grip-bench-press",
                "ex-overhead-cable-triceps-extension", "ex-overhead-dumbbell-triceps-extension",
                "ex-machine-triceps-extension", "ex-diamond-push-up", "ex-bench-dip",
            ),
            "Quads" to listOf(
                "ex-barbell-back-squat", "ex-front-squat", "ex-goblet-squat",
                "ex-bulgarian-split-squat", "ex-walking-lunge", "ex-leg-press", "ex-leg-extension",
                "ex-hack-squat", "ex-smith-machine-squat", "ex-reverse-lunge",
                "ex-dumbbell-step-up", "ex-bodyweight-squat",
                "ex-hyper-pro-bulgarian-split-squat", "ex-hyper-pro-couch-stretch",
                "ex-hyper-pro-kot-squat", "ex-hyper-pro-leg-extension",
                "ex-hyper-pro-reverse-nordic",
            ),
            "Hamstrings" to listOf(
                "ex-romanian-deadlift", "ex-leg-curl",
                "ex-seated-leg-curl", "ex-dumbbell-romanian-deadlift",
                "ex-single-leg-romanian-deadlift", "ex-good-morning", "ex-nordic-ham-curl",
                "ex-hyper-pro-hamstring-curl", "ex-hyper-pro-nordic-curl",
                "ex-hyper-pro-elephant-walk",
            ),
            "Glutes" to listOf(
                "ex-hip-thrust",
                "ex-barbell-glute-bridge", "ex-machine-hip-thrust", "ex-hip-abduction-machine",
                "ex-cable-kickback", "ex-cable-pull-through",
                "ex-hyper-pro-glute-ham-raise", "ex-hyper-pro-hip-thrust",
                "ex-hyper-pro-incline-pigeon",
            ),
            "Calves" to listOf(
                "ex-standing-calf-raise",
                "ex-seated-calf-raise", "ex-leg-press-calf-raise", "ex-single-leg-calf-raise",
                "ex-hyper-pro-calf-stretch",
            ),
            "Core" to listOf(
                "ex-plank", "ex-hanging-leg-raise", "ex-cable-crunch",
                "ex-machine-crunch", "ex-decline-sit-up", "ex-side-plank", "ex-ab-wheel-rollout",
                "ex-dead-bug", "ex-russian-twist", "ex-farmer-s-carry",
                "ex-hyper-pro-ghd-sit-up", "ex-hyper-pro-leg-raise",
                "ex-hyper-pro-russian-twist", "ex-hyper-pro-sit-up",
                "ex-hyper-pro-woodchop",
            ),
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
            File("../docs/artifacts/$name"),
            File("docs/artifacts/$name"),
        ).firstOrNull { it.exists() }
        assertTrue("$name is not committed", file != null)
        assertEquals(
            "Re-render docs/artifacts/$name from CatalogReviewRenderer",
            expected,
            file!!.readText(),
        )
    }
}
