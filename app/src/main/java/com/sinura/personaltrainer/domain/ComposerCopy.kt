package com.sinura.personaltrainer.domain

/**
 * Gym-floor words for the backdated activity composer.
 *
 * Save is the one filled Volt. Add set / Add cardio are secondary acts.
 * Cardio type chips reuse [CardioCopy.name] so Run / Ride / Walk never fork.
 * Weights are stored in kg and labeled through [WeightUnit].
 */
object ComposerCopy {
    const val SAVE = "Save"
    const val SAVING = "Saving…"
    const val CANCEL = "Cancel"
    const val ADD_SET = "Add set"
    const val ADD_CARDIO = "Add cardio"
    const val REMOVE = "Remove"
    const val REPS = "Reps"
    const val MINUTES = "Minutes"

    const val VOLT = SAVE

    /** Secondary acts on the composer. Never a filled Volt. */
    val SECONDARY_ACTS = listOf(ADD_SET, ADD_CARDIO)

    fun typeChipLabel(type: CardioType): String = CardioCopy.name(type)

    fun untitledCardioTitle(type: CardioType): String = CardioCopy.name(type)

    fun weightFieldLabel(unit: WeightUnit): String = "Weight ${unit.suffix}"

    fun parseWeightToKg(input: String, unit: WeightUnit): Double =
        WeightConverter.parseDisplayToKg(input, unit, originalKg = null) ?: 0.0

    fun strengthLineSubtitle(reps: Int, weightKg: Double, unit: WeightUnit): String =
        "$reps reps · ${weightKg.toWeightLabel(unit)}"

    fun cardioLineSubtitle(minutes: Int, distanceKm: Double?): String {
        val distance = distanceKm?.let { " · $it km" }.orEmpty()
        return "$minutes min$distance"
    }
}
