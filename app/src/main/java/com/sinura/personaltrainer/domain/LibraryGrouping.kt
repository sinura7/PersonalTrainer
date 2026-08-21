package com.sinura.personaltrainer.domain

/**
 * One movement family and the lifts in it.
 *
 * [plain] marks a family with a single member. A "Leg Press (1)" header you must tap to reveal
 * one row is pure ceremony, so those render as an ordinary row instead — the grouping exists to
 * make 98 lifts scannable, and a header per lift would do the opposite.
 */
data class LibraryFamily(
    val movementKey: String,
    val label: String,
    val members: List<Exercise>,
) {
    val plain: Boolean get() = members.size == 1
}

/**
 * Groups the library by movement family.
 *
 * A flat alphabetical list of 98 lifts asks the reader to know the exact name of the variant
 * they want before they can find it. Grouped by family, "Bench Press (8)" is one line that
 * answers "what bench variants does this app have" — which is the question someone opening a
 * library is actually asking.
 *
 * Families are ordered by their best member rather than alphabetically, so Squat and Bench Press
 * lead and Dead Bug trails. Ordering families by name would put "Ab Wheel" first, which is the
 * failure mode this replaces.
 */
object LibraryGrouping {
    /**
     * Display labels for the family keys. A key with no entry here falls back to its first
     * member's name, so a family added to the catalog without a label degrades to something
     * readable rather than showing a slug.
     */
    val FAMILY_LABELS: Map<String, String> = mapOf(
        "squat" to "Squat",
        "lunge" to "Lunge",
        "step-up" to "Step-Up",
        "leg-press" to "Leg Press",
        "leg-extension" to "Leg Extension",
        "deadlift" to "Deadlift",
        "romanian-deadlift" to "Romanian Deadlift",
        "good-morning" to "Good Morning",
        "back-extension" to "Back Extension",
        "kettlebell-swing" to "Swing",
        "hip-thrust" to "Hip Thrust",
        "hip-abduction" to "Hip Abduction",
        "glute-kickback" to "Kickback",
        "pull-through" to "Pull-Through",
        "leg-curl" to "Leg Curl",
        "nordic-curl" to "Nordic Curl",
        "calf-raise" to "Calf Raise",
        "bench-press" to "Bench Press",
        "chest-fly" to "Chest Fly",
        "push-up" to "Push-Up",
        "dip" to "Dip",
        "overhead-press" to "Overhead Press",
        "lateral-raise" to "Lateral Raise",
        "rear-delt" to "Rear Delt",
        "row" to "Row",
        "pulldown" to "Pulldown",
        "pull-up" to "Pull-Up",
        "pullover" to "Pullover",
        "shrug" to "Shrug",
        "curl" to "Curl",
        "triceps-extension" to "Triceps Extension",
        "plank" to "Plank",
        "crunch" to "Crunch",
        "sit-up" to "Sit-Up",
        "leg-raise" to "Leg Raise",
        "rollout" to "Rollout",
        "twist" to "Twist",
        "dead-bug" to "Dead Bug",
        "carry" to "Carry",
    )

    /** The bucket for lifts with no family — customs, and anything a future catalog forgets. */
    const val UNGROUPED_KEY = ""
    const val UNGROUPED_LABEL = "Your lifts"

    fun group(exercises: List<Exercise>): List<LibraryFamily> {
        val families = exercises.groupBy { it.movementKey ?: UNGROUPED_KEY }
            .map { (key, members) ->
                LibraryFamily(
                    movementKey = key,
                    label = labelFor(key, members),
                    members = ExerciseOrdering.catalogOrder(members),
                )
            }
        // Ungrouped lifts are the user's own, and they go last whatever their rank would say —
        // a custom sorts at Int.MAX_VALUE anyway, but stating it here means a custom that later
        // gains a rank still cannot displace the catalog.
        val (ungrouped, keyed) = families.partition { it.movementKey == UNGROUPED_KEY }
        return keyed.sortedBy { family ->
            family.members.minOf { CatalogMeta.sortRank(it.id) }
        } + ungrouped
    }

    private fun labelFor(key: String, members: List<Exercise>): String = when {
        key == UNGROUPED_KEY -> UNGROUPED_LABEL
        else -> FAMILY_LABELS[key] ?: members.first().name
    }

    /**
     * The other lifts in this one's family — what "swap equipment" offers.
     *
     * [exclude] carries the ids already in the routine or session, because offering a swap to a
     * lift that is already three rows below is offering to create a duplicate.
     */
    fun siblings(
        exercise: Exercise,
        catalog: List<Exercise>,
        exclude: Set<String> = emptySet(),
    ): List<Exercise> {
        val key = exercise.movementKey ?: return emptyList()
        return ExerciseOrdering.catalogOrder(
            catalog.filter {
                it.movementKey == key && it.id != exercise.id && it.id !in exclude
            },
        )
    }
}
