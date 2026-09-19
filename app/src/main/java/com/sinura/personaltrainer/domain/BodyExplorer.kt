package com.sinura.personaltrainer.domain

/**
 * The gym wall Body can show before anyone has logged a set.
 *
 * [OwnedLiftResolver] refuses to name a catalog lift you have not planned or trained —
 * that is the right honesty once there is a history to be faithful to. First launch has
 * no such history. The figure is still a map of the body, and the catalog already knows
 * which lifts train each muscle. This is that map, not a coach pretending you are behind.
 *
 * Equipment filtering is the same contract as the coach: empty kit means a full gym,
 * minus specialty benches. A poster family that the owner cannot load falls through to
 * any allowed lift for that muscle, rather than naming a barbell they do not have.
 */
object BodyExplorer {
    const val SHEET_LIMIT = 4

    /**
     * The movement family that stands for each mapped muscle on a gym wall.
     *
     * These are [DefaultExercises.MOVEMENT_FAMILIES] keys, not lift ids: the catalog
     * answers with the allowed equipment variant (barbell squat, goblet squat, …).
     */
    val POSTER_FAMILY: Map<CanonicalMuscle, String> = mapOf(
        CanonicalMuscle.CHEST to "bench-press",
        CanonicalMuscle.BACK to "row",
        CanonicalMuscle.SHOULDERS to "overhead-press",
        CanonicalMuscle.BICEPS to "curl",
        CanonicalMuscle.TRICEPS to "triceps-extension",
        CanonicalMuscle.QUADRICEPS to "squat",
        CanonicalMuscle.HAMSTRINGS to "romanian-deadlift",
        CanonicalMuscle.GLUTES to "hip-thrust",
        CanonicalMuscle.CALVES to "calf-raise",
        CanonicalMuscle.CORE to "plank",
    )

    /**
     * Muscles whose poster lift appears on first-launch Body, in floor order:
     * squat, press, pull, hinge. Shoulders, arms, and core stay a muscle tap away.
     */
    val COVERAGE_MUSCLES: List<CanonicalMuscle> = listOf(
        CanonicalMuscle.QUADRICEPS,
        CanonicalMuscle.CHEST,
        CanonicalMuscle.BACK,
        CanonicalMuscle.HAMSTRINGS,
    )

    /**
     * One pictured lift per coverage muscle, for the first-launch list under the figure.
     *
     * A muscle with no allowed lift is omitted rather than filled with something the
     * owner cannot do. Distinct by id so a dual-primary never appears twice.
     */
    fun coverage(
        catalog: Collection<Exercise>,
        preferences: CoachPreferences = CoachPreferences.DEFAULT,
    ): List<Exercise> = COVERAGE_MUSCLES.mapNotNull { muscle ->
        forMuscle(muscle, catalog, preferences, limit = 1).firstOrNull()
    }.distinctBy { it.id }

    /**
     * The lifts that train [muscle], as a short gym-wall sample — not the whole Library.
     *
     * One representative per movement family, poster family first, then floor kit order.
     * The sheet's **Find … lifts** is how you get the rest.
     */
    fun forMuscle(
        muscle: CanonicalMuscle,
        catalog: Collection<Exercise>,
        preferences: CoachPreferences = CoachPreferences.DEFAULT,
        limit: Int = SHEET_LIMIT,
    ): List<Exercise> {
        if (limit <= 0) return emptyList()
        val eligible = catalog.filter { exercise ->
            preferences.allows(exercise.equipment) &&
                OwnedLiftResolver.primaryMuscleOf(exercise) == muscle
        }
        if (eligible.isEmpty()) return emptyList()
        val representatives = eligible
            .groupBy { it.movementKey ?: it.id }
            .map { (_, family) -> pickRepresentative(family) }
        val poster = POSTER_FAMILY[muscle]
        return representatives.sortedWith(
            compareBy<Exercise> { if (it.movementKey == poster) 0 else 1 }
                .thenBy { if (it.isCustom) 1 else 0 }
                .thenBy { equipmentRank(it.equipment) }
                .thenBy { it.name },
        ).take(limit)
    }

    private fun pickRepresentative(family: List<Exercise>): Exercise =
        family.minWith(
            compareBy<Exercise> { if (it.isCustom) 1 else 0 }
                .thenBy { equipmentRank(it.equipment) }
                .thenBy { it.name },
        )

    /**
     * Free weight, then gym kit, then the rest — the same floor layout as
     * [EquipmentType.label]'s chip order, so a barbell squat beats a smith squat
     * when both are allowed.
     */
    private val EQUIPMENT_RANK: Map<EquipmentType, Int> = mapOf(
        EquipmentType.BARBELL to 0,
        EquipmentType.DUMBBELL to 1,
        EquipmentType.MACHINE to 2,
        EquipmentType.CABLE to 3,
        EquipmentType.SMITH to 4,
        EquipmentType.KETTLEBELL to 5,
        EquipmentType.BODYWEIGHT to 6,
        EquipmentType.BAND to 7,
        EquipmentType.OTHER to 8,
        EquipmentType.HYPER_PRO to 9,
    )

    internal fun equipmentRank(equipment: EquipmentType): Int =
        EQUIPMENT_RANK[equipment] ?: EQUIPMENT_RANK.size
}
