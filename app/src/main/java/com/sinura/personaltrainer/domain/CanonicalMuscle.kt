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

    /**
     * The one function that turns an exercise name into its `nameKey`.
     *
     * Public and delegating rather than re-implemented at each call site: four places compute a
     * nameKey — the seeder, the mappers, the backup insert path, and the duplicate-name check —
     * and a second implementation of "trim, lowercase, collapse whitespace" is exactly how the
     * `nameKey` index and the duplicate check drift apart. Punctuation is deliberately kept, so
     * "Push-Up" keys as `push-up` and stays distinct from a lift someone named "Push Up".
     *
     * The migration's SQL backfill `LOWER(TRIM(name))` is the one deliberate exception — it runs
     * before any Kotlin can — and the first reconciliation pass rewrites every row through here.
     */
    fun nameKeyOf(name: String): String = normalizeKey(name)

    /**
     * What a free-text muscle group is worth, as junction credits.
     *
     * This is the v1 model expressed in the v2 shape, and it is the fallback for everything the
     * catalog has no explicit junction for: a custom exercise the user typed, or a lift embedded
     * in an old session. Primary takes 1.0, each derived secondary takes the same flat
     * [MuscleLoadCalculator.SECONDARY_VOLUME_WEIGHT] the body map has always used — so an
     * exercise with no junction rows heats exactly as it did before v2.
     *
     * An unmappable group derives a single `other` credit, preserving today's off-map behaviour
     * rather than inventing a muscle for it.
     */
    fun deriveCredits(muscleGroup: String): List<MuscleCredit> {
        val mapping = normalize(muscleGroup)
        if (mapping.primary == CanonicalMuscle.OTHER) {
            return listOf(MuscleCredit(muscleKey = keyOf(CanonicalMuscle.OTHER), weight = 1.0))
        }
        return buildList {
            add(MuscleCredit(muscleKey = keyOf(mapping.primary), weight = 1.0))
            mapping.secondaries.forEach { muscle ->
                add(
                    MuscleCredit(
                        muscleKey = keyOf(muscle),
                        weight = MuscleLoadCalculator.SECONDARY_VOLUME_WEIGHT,
                    ),
                )
            }
        }
    }

    /** The storage spelling of a canonical muscle. Exactly `name.lowercase()`, always. */
    fun keyOf(muscle: CanonicalMuscle): String = muscle.name.lowercase()

    /**
     * Resolves a stored `muscleKey` back to its canonical muscle, or null when nothing in the
     * alias index can place it.
     *
     * Underscores become spaces before the lookup so a future sub-muscle key like `front_delt`
     * resolves through its parent's aliases; that is the contract every new key has to satisfy,
     * and the catalog invariant test makes an unmapped key a build failure rather than a muscle
     * that silently stops receiving credit.
     */
    /**
     * Reads a muscle back out of a navigation argument.
     *
     * Lives here rather than in the nav graph so it can be tested: the JVM test lane compiles
     * `domain/` and nothing Compose-shaped, and a parse rule proved only by a copy of itself in
     * a test file is not proved at all.
     *
     * The exact-name match is the contract. The alias fallback exists for a link created by an
     * older build — one still carrying a display label like "Quads" — sitting in a saved back
     * stack; it costs one lookup and turns a dead filter into a working one.
     */
    fun fromRouteArgument(raw: String?): CanonicalMuscle? {
        if (raw.isNullOrBlank()) return null
        CanonicalMuscle.entries.firstOrNull { it.name == raw }?.let { return it }
        return primaryOf(raw).takeIf { it != CanonicalMuscle.OTHER }
    }

    fun resolveKey(muscleKey: String): CanonicalMuscle? {
        val candidate = muscleKey.replace('_', ' ')
        val mapping = normalize(candidate)
        if (mapping.primary != CanonicalMuscle.OTHER) return mapping.primary
        // "other" is a legitimate key, not a failed lookup — distinguish the two.
        return CanonicalMuscle.OTHER.takeIf { normalizeKey(candidate) in it.aliases }
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
