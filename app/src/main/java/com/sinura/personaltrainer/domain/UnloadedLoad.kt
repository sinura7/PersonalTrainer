package com.sinura.personaltrainer.domain

/**
 * Which lifts may log a working set at 0 kg.
 *
 * A barbell at 0 kg is a typo. A walking lunge at 0 kg is empty hands — the same
 * movement with no dumbbells. The catalog already said that for
 * [LoadType.BODYWEIGHT], [LoadType.BODYWEIGHT_PLUS], and [LoadType.ASSISTED];
 * [LoadType.EXTERNAL] dumbbell lunges and step-ups were filed as always-loaded,
 * so the floor clamped them onto a 5 lb plate and [SetLogRules] refused the log.
 *
 * The families here are read off the seed, not guessed from gym folklore:
 * every built-in whose [LoadType] already allows 0, plus EXTERNAL rows whose
 * kit is a dumbbell (or a band) and whose [SeedExercise.movementKey] is a
 * family that is the same lift with empty hands — `lunge` and `step-up`.
 * A goblet squat stays loaded: without the bell it is the Bodyweight Squat row.
 * A dumbbell bench stays loaded: without the bells it is a push-up.
 */
object UnloadedLoad {
    /**
     * Families that stay the same lift when the hands are empty.
     *
     * Catalog rows: Walking Lunge, Reverse Lunge, Bulgarian Split Squat
     * (`lunge`); Dumbbell Step-Up (`step-up`). Hyper Pro Bulgarian Split Squat
     * is already [LoadType.BODYWEIGHT_PLUS].
     */
    val EMPTY_HANDS_FAMILIES: Set<String> = setOf("lunge", "step-up")

    fun allowsZeroWorkingWeight(
        loadType: LoadType?,
        equipment: EquipmentType? = null,
        movementKey: String? = null,
    ): Boolean = when (loadType) {
        LoadType.BODYWEIGHT, LoadType.BODYWEIGHT_PLUS, LoadType.ASSISTED -> true
        LoadType.STACK, null -> false
        LoadType.EXTERNAL -> emptyHandsExternal(equipment, movementKey)
    }

    fun allowsZeroWorkingWeight(seed: SeedExercise): Boolean =
        allowsZeroWorkingWeight(seed.loadType, seed.equipment, seed.movementKey)

    fun allowsZeroWorkingWeight(exercise: Exercise): Boolean =
        allowsZeroWorkingWeight(exercise.loadType, exercise.equipment, exercise.movementKey)

    /**
     * Session dose for empty-hands dumbbell lunges and step-ups.
     *
     * Logging is [LoadType.BODYWEIGHT_PLUS]; the set/rep/rest table they
     * already had is the EXTERNAL compound row. Hyper Pro siblings stay on
     * the BODYWEIGHT_PLUS table — they were never EXTERNAL.
     */
    fun sizesLikeExternalCompound(exercise: Exercise): Boolean =
        exercise.equipment == EquipmentType.DUMBBELL &&
            exercise.movementKey in EMPTY_HANDS_FAMILIES

    fun sizesLikeExternalCompound(seed: SeedExercise): Boolean =
        seed.equipment == EquipmentType.DUMBBELL && seed.movementKey in EMPTY_HANDS_FAMILIES

    /**
     * Every built-in that may log 0, in catalog order.
     *
     * Tests pin the names so a later catalog row cannot quietly inherit — or
     * lose — the empty-hands rule.
     */
    fun catalog(): List<SeedExercise> =
        DefaultExercises.catalog().filter { allowsZeroWorkingWeight(it) }

    fun names(): List<String> = catalog().map { it.name }

    private fun emptyHandsExternal(
        equipment: EquipmentType?,
        movementKey: String?,
    ): Boolean {
        if (equipment == EquipmentType.BODYWEIGHT) return true
        if (equipment != EquipmentType.DUMBBELL && equipment != EquipmentType.BAND) {
            return false
        }
        return movementKey in EMPTY_HANDS_FAMILIES
    }
}
