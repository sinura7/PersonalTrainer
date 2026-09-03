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
    const val CHOOSE_LIFT = "Choose a lift"
    const val REMOVE = "Remove"
    const val REPS = "Reps"
    const val MINUTES = "Minutes"
    const val EARLIER = "Earlier"
    const val LATER = "Later"
    const val LEAVE = "Leave"
    const val KEEP_EDITING = "Keep editing"
    const val LEAVE_TITLE = "Leave this log?"
    const val LEAVE_BODY =
        "Nothing is written until you save. Going back drops this draft."
    const val LOGGED_AT_NOON = "Logged at noon. Future dates are refused."
    const val NO_LIFTS = "No lifts in the library yet."
    const val PICKER_TITLE = "Choose a lift"

    const val VOLT = SAVE

    /** Secondary acts on the composer. Never a filled Volt. */
    val SECONDARY_ACTS = listOf(ADD_SET, ADD_CARDIO, CHOOSE_LIFT)

    fun typeChipLabel(type: CardioType): String = CardioCopy.name(type)

    fun untitledCardioTitle(type: CardioType): String = CardioCopy.name(type)

    fun weightFieldLabel(unit: WeightUnit): String = "Weight ${unit.suffix}"

    fun parseWeightToKg(input: String, unit: WeightUnit): Double =
        WeightConverter.parseDisplayToKg(input, unit, originalKg = null) ?: 0.0

    fun parseDistanceKm(input: String): Double? =
        NumericEntry.parseDecimal(input)?.takeIf { it > 0.0 }

    fun strengthLineSubtitle(reps: Int, weightKg: Double, unit: WeightUnit): String =
        "$reps reps · ${weightKg.toWeightLabel(unit)}"

    fun cardioLineSubtitle(
        minutes: Int,
        distanceKm: Double?,
        indoor: Boolean = false,
    ): String {
        val distance = distanceKm?.let { " · $it km" }.orEmpty()
        val place = if (indoor) " · ${CardioCopy.INDOOR}" else ""
        return "$minutes min$distance$place"
    }

    fun canShiftLater(epochDay: Long, todayEpochDay: Long): Boolean = epochDay < todayEpochDay

    fun isDirty(
        title: String,
        strengthCount: Int,
        cardioCount: Int,
        epochDay: Long,
        todayEpochDay: Long,
    ): Boolean =
        title.isNotBlank() ||
            strengthCount > 0 ||
            cardioCount > 0 ||
            epochDay != todayEpochDay
}
