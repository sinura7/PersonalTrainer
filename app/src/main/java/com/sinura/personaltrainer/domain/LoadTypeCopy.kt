package com.sinura.personaltrainer.domain

/**
 * How a lift's load is named on a row, a chip, and the editor's weight well.
 *
 * Equipment is what you hold. Load type is how the resistance behaves. E-12 is the
 * second of those, and it used to be invisible: a plate-loaded leg press and a pin
 * stack were both tagged "Machine", and a custom created from the picker was always
 * plates even when the owner typed "push-up".
 */
object LoadTypeCopy {
    const val KICKER = "Load"
    const val TARGET_WEIGHT = "Target weight"

    /**
     * The row tag: kit, then load when the kit does not already say it.
     *
     * A barbell is plates. A machine is not — it might be a sled or a stack — so the
     * load is named. Bodyweight kit plus bodyweight load collapses to one word.
     */
    fun rowTag(equipment: EquipmentType, loadType: LoadType): String {
        val kit = equipment.label
        return when (loadType) {
            LoadType.STACK -> "$kit · Stack"
            LoadType.ASSISTED ->
                if (equipment == EquipmentType.MACHINE) "Assisted" else "$kit · Assisted"
            LoadType.BODYWEIGHT ->
                if (equipment == EquipmentType.BODYWEIGHT) "Bodyweight" else "$kit · Bodyweight"
            LoadType.BODYWEIGHT_PLUS -> "$kit · Added"
            LoadType.EXTERNAL ->
                if (equipment == EquipmentType.MACHINE) "$kit · Plates" else kit
        }
    }

    fun rowTag(exercise: Exercise): String = rowTag(exercise.equipment, exercise.loadType)

    /**
     * Library rows: "Custom" still wins on the owner's own lifts, with the load named
     * when it is not the plates default. Built-ins use [rowTag].
     */
    fun libraryTag(exercise: Exercise): String {
        if (!exercise.isCustom) return rowTag(exercise)
        return if (exercise.loadType == LoadType.EXTERNAL) {
            "Custom"
        } else {
            "Custom · ${exercise.loadType.label}"
        }
    }

    /**
     * Caption under the picker's create row once a muscle is chosen.
     */
    fun createCaption(muscleGroup: String, loadType: LoadType): String =
        "Adds a custom ${muscleGroup.trim()} lift · ${loadType.label}"

    /**
     * Label on the editor's weight well. Null means the well is not shown: a bodyweight
     * lift has no target kilograms, and a labelled empty box would invite the owner's
     * own bodyweight into a column that does not mean that.
     */
    fun editorWeightLabel(loadType: LoadType): String? = when (loadType) {
        LoadType.BODYWEIGHT -> null
        LoadType.STACK -> "Stack"
        LoadType.BODYWEIGHT_PLUS -> "Added"
        LoadType.ASSISTED -> "Assist"
        LoadType.EXTERNAL -> TARGET_WEIGHT
    }

    fun showsWeightWell(loadType: LoadType): Boolean =
        editorWeightLabel(loadType) != null
}
