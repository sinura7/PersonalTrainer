package com.sinura.personaltrainer.domain

/**
 * The Library's muscle filter, run on junction credits rather than on display text.
 *
 * The old filter compared the user's chip against `muscleGroup`, a free string. That made the
 * chip a text search wearing a chip's clothes: "Quads" matched a lift whose group happened to
 * read "Quads" and missed the sumo deadlift, which trains them hard but is filed under Glutes.
 * Credits already say which muscles a lift works and how much, so the filter reads those.
 *
 * Secondary-credit lifts are INCLUDED and ranked below the primaries. Someone filtering to
 * quads wants to see the sumo deadlift; they do not want it above the leg extension. Excluding
 * it entirely was the previous behaviour and is the thing being fixed.
 */
object LibraryFilter {
    fun matches(exercise: Exercise, muscle: CanonicalMuscle): Boolean =
        creditWeight(exercise, muscle) != null

    /**
     * How strongly this lift trains the muscle, or null when it does not.
     *
     * A custom with no junction rows falls back to the muscle group the user picked for it,
     * counted as a primary — that string is the only thing they ever told us, so ignoring it
     * would hide their own lifts from their own filter.
     */
    fun creditWeight(exercise: Exercise, muscle: CanonicalMuscle): Double? {
        if (exercise.muscles.isEmpty()) {
            return if (MuscleNormalizer.primaryOf(exercise.muscleGroup) == muscle) 1.0 else null
        }
        return exercise.muscles
            .filter { MuscleNormalizer.resolveKey(it.muscleKey) == muscle }
            .maxOfOrNull { it.weight }
    }

    /** Filtered and ordered: primaries first, then by how much credit, then catalog rank. */
    fun apply(exercises: List<Exercise>, muscle: CanonicalMuscle?): List<Exercise> {
        if (muscle == null) return exercises
        return exercises
            .mapNotNull { exercise -> creditWeight(exercise, muscle)?.let { exercise to it } }
            .sortedWith(
                compareByDescending<Pair<Exercise, Double>> { it.second }
                    .thenBy { CatalogMeta.sortRank(it.first.id) }
                    .thenBy { it.first.name.lowercase() },
            )
            .map { it.first }
    }

    /** The muscles the catalog can actually filter to, in canonical order. */
    fun musclesPresentIn(exercises: List<Exercise>): List<CanonicalMuscle> =
        CanonicalMuscle.entries.filter { muscle ->
            muscle != CanonicalMuscle.OTHER && exercises.any { matches(it, muscle) }
        }
}
