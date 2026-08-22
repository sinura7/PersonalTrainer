package com.sinura.personaltrainer.domain

data class ExerciseUsage(
    val routineCount: Int,
    val historySetCount: Int,
    val sessionCount: Int,
) {
    val isReferenced: Boolean
        get() = routineCount > 0 || historySetCount > 0 || sessionCount > 0

    fun reason(): String {
        val parts = buildList {
            if (routineCount == 1) add("1 routine")
            if (routineCount > 1) add("$routineCount routines")
            if (historySetCount > 0) add("logged workout history")
            if (sessionCount > 0 && historySetCount == 0) add("an in-progress or past session")
        }
        return if (parts.isEmpty()) {
            "this exercise is still in use"
        } else {
            parts.joinToString(" and ")
        }
    }
}

object MuscleGroups {
    const val OTHER = "Other"
    const val MISSING_MESSAGE = "Pick a muscle."

    val catalog: List<String> = listOf(
        "Quads",
        "Hamstrings",
        "Glutes",
        "Posterior chain",
        "Calves",
        "Chest",
        "Back",
        "Shoulders",
        "Rear delts",
        "Biceps",
        "Triceps",
        "Core",
        OTHER,
    )

    val chips: List<String> = catalog.filterNot { it.equals(OTHER, ignoreCase = true) }

    /**
     * A custom lift needs a group. Blank used to become [OTHER] in the repository,
     * so a picker-created lift was a junk bucket until someone noticed.
     */
    fun resolved(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

    /** Seed a new draft from the library filter, or leave it empty so no chip is pre-selected. */
    fun forNewDraft(seed: CanonicalMuscle?): String = seed?.catalogLabel.orEmpty()

    fun otherSelected(muscleGroup: String): Boolean {
        val trimmed = muscleGroup.trim()
        if (trimmed.isEmpty()) return false
        return chips.none { it.equals(trimmed, ignoreCase = true) }
    }

    fun presentIn(exercises: List<Exercise>): List<String> {
        val used = exercises.map { it.muscleGroup.trim() }.filter { it.isNotEmpty() }.toSet()
        return (catalog.filter { it in used } + used.filter { it !in catalog.toSet() }.sorted())
            .distinct()
    }

    fun editorOptions(exercises: List<Exercise>): List<String> {
        val extra = exercises.map { it.muscleGroup.trim() }.filter { it.isNotEmpty() && it !in catalog }
        return (catalog + extra.distinct().sorted()).distinct()
    }
}
