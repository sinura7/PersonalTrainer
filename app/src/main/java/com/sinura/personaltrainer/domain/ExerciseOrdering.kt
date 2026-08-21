package com.sinura.personaltrainer.domain

/**
 * The order lifts appear in, which is a different question in the picker than in the library.
 *
 * The library is a catalog: you are browsing what exists, so it reads in curated order. The
 * picker is a tool: you opened it to add a lift you are about to do, and the best predictor of
 * that is what you have been training. With 98 lifts, alphabetical was going to put Ab Wheel
 * Rollout above Barbell Back Squat on the screen you use mid-workout.
 */
object ExerciseOrdering {
    /**
     * Picker order with an empty query: most recently logged first, never-logged last.
     *
     * Never-logged lifts fall back to [CatalogMeta.sortRank] rather than to alphabetical, so a
     * fresh install still opens on the squat rather than the ab wheel; customs, which have no
     * rank, sort after the built-ins and then by name.
     */
    fun pickerOrder(
        exercises: List<Exercise>,
        lastLoggedById: Map<String, Long>,
    ): List<Exercise> = exercises.sortedWith(
        compareByDescending<Exercise> { lastLoggedById[it.id] ?: Long.MIN_VALUE }
            .then(catalogOrder),
    )

    /** Flat catalog order: curated rank, then name. Used whenever a query or filter is active. */
    fun catalogOrder(exercises: List<Exercise>): List<Exercise> = exercises.sortedWith(catalogOrder)

    private val catalogOrder: Comparator<Exercise> =
        compareBy<Exercise> { CatalogMeta.sortRank(it.id) }.thenBy { it.name.lowercase() }
}
