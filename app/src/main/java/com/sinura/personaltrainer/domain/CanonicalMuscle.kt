package com.sinura.personaltrainer.domain

enum class MuscleRegion {
    UPPER,
    LOWER,
    CORE,
    OTHER,
}

/**
 * Canonical muscle taxonomy used by the body map and recommendations.
 * Exercise `muscleGroup` strings are mapped here — they stay free-text in Room.
 */
enum class CanonicalMuscle(
    val displayName: String,
    val region: MuscleRegion,
    val catalogLabel: String,
    val aliases: Set<String>,
) {
    CHEST(
        displayName = "Chest",
        region = MuscleRegion.UPPER,
        catalogLabel = "Chest",
        aliases = setOf("chest", "pecs", "pec", "pectoral", "pectorals", "pec minor", "pec major"),
    ),
    BACK(
        displayName = "Back",
        region = MuscleRegion.UPPER,
        catalogLabel = "Back",
        aliases = setOf(
            "back", "lats", "lat", "latissimus", "upper back", "traps", "trapezius",
            "posterior chain", "erectors", "spinal erectors",
        ),
    ),
    SHOULDERS(
        displayName = "Shoulders",
        region = MuscleRegion.UPPER,
        catalogLabel = "Shoulders",
        aliases = setOf(
            "shoulders", "shoulder", "delts", "delt", "deltoids", "deltoid",
            "rear delts", "rear delt", "front delts", "side delts", "lateral delts",
        ),
    ),
    BICEPS(
        displayName = "Biceps",
        region = MuscleRegion.UPPER,
        catalogLabel = "Biceps",
        aliases = setOf("biceps", "bicep", "bis", "biceps brachii"),
    ),
    TRICEPS(
        displayName = "Triceps",
        region = MuscleRegion.UPPER,
        catalogLabel = "Triceps",
        aliases = setOf("triceps", "tricep", "tris", "triceps brachii"),
    ),
    QUADRICEPS(
        displayName = "Quadriceps",
        region = MuscleRegion.LOWER,
        catalogLabel = "Quads",
        aliases = setOf("quadriceps", "quads", "quad", "quadricep", "thighs", "thigh", "legs"),
    ),
    HAMSTRINGS(
        displayName = "Hamstrings",
        region = MuscleRegion.LOWER,
        catalogLabel = "Hamstrings",
        aliases = setOf("hamstrings", "hamstring", "hams"),
    ),
    GLUTES(
        displayName = "Glutes",
        region = MuscleRegion.LOWER,
        catalogLabel = "Glutes",
        aliases = setOf("glutes", "glute", "gluteus", "gluteus maximus"),
    ),
    CALVES(
        displayName = "Calves",
        region = MuscleRegion.LOWER,
        catalogLabel = "Calves",
        aliases = setOf("calves", "calf", "gastroc", "gastrocnemius", "soleus"),
    ),
    CORE(
        displayName = "Core",
        region = MuscleRegion.CORE,
        catalogLabel = "Core",
        aliases = setOf("core", "abs", "ab", "abdominals", "obliques", "oblique"),
    ),
    OTHER(
        displayName = "Other / Full Body",
        region = MuscleRegion.OTHER,
        catalogLabel = "Other",
        aliases = setOf("other", "full body", "fullbody", "full-body", "cardio", "olympic", "neck"),
    ),
    ;

    val appearsOnBodyMap: Boolean get() = this != OTHER

    val shortLabel: String
        get() = when (this) {
            QUADRICEPS -> "Quads"
            HAMSTRINGS -> "Hams"
            SHOULDERS -> "Delts"
            OTHER -> "Other"
            else -> displayName
        }

    companion object {
        val mapped: List<CanonicalMuscle> = entries.filter { it != OTHER }

        val bodyMapOrder: List<CanonicalMuscle> = listOf(
            CHEST, BACK, SHOULDERS, BICEPS, TRICEPS,
            QUADRICEPS, HAMSTRINGS, GLUTES, CALVES, CORE,
        )
    }
}

data class MuscleMapping(
    val primary: CanonicalMuscle,
    val secondaries: List<CanonicalMuscle> = emptyList(),
) {
    fun all(): List<CanonicalMuscle> = listOf(primary) + secondaries
}

object MuscleNormalizer {
    private val aliasIndex: Map<String, CanonicalMuscle> = buildMap {
        CanonicalMuscle.entries.forEach { muscle ->
            put(normalizeKey(muscle.displayName), muscle)
            put(normalizeKey(muscle.catalogLabel), muscle)
            muscle.aliases.forEach { alias -> put(normalizeKey(alias), muscle) }
        }
    }

    private val secondaryByPrimaryLabel: Map<String, List<CanonicalMuscle>> = mapOf(
        "posterior chain" to listOf(CanonicalMuscle.HAMSTRINGS, CanonicalMuscle.GLUTES),
        "legs" to listOf(CanonicalMuscle.HAMSTRINGS, CanonicalMuscle.GLUTES),
    )

    fun normalize(raw: String?): MuscleMapping {
        val key = normalizeKey(raw)
        if (key.isEmpty()) return MuscleMapping(CanonicalMuscle.OTHER)
        val primary = aliasIndex[key] ?: fuzzyMatch(key) ?: CanonicalMuscle.OTHER
        val secondaries = secondaryByPrimaryLabel[key]
            .orEmpty()
            .filter { it != primary }
        return MuscleMapping(primary, secondaries)
    }

    fun primaryOf(raw: String?): CanonicalMuscle = normalize(raw).primary

    fun matchesFilter(exerciseGroup: String, filter: String?): Boolean {
        if (filter.isNullOrBlank()) return true
        if (exerciseGroup.equals(filter, ignoreCase = true)) return true
        return primaryOf(exerciseGroup) == primaryOf(filter)
    }

    private fun fuzzyMatch(key: String): CanonicalMuscle? {
        aliasIndex.entries.firstOrNull { (alias, _) ->
            key.contains(alias) || alias.contains(key)
        }?.let { return it.value }
        return null
    }

    private fun normalizeKey(value: String?): String =
        value.orEmpty().trim().lowercase().replace(Regex("\\s+"), " ")
}
