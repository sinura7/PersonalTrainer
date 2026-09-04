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
        parseDistanceToKm(input, DistanceUnit.KM)

    fun parseDistanceToKm(input: String, unit: DistanceUnit): Double? {
        val amount = NumericEntry.parseDecimal(input)?.takeIf { it > 0.0 } ?: return null
        return when (unit) {
            DistanceUnit.KM -> amount
            DistanceUnit.MI -> amount * DistanceUnit.METERS_PER_MILE / 1_000.0
        }
    }

    fun parseDistanceToMeters(input: String, unit: DistanceUnit): Double? =
        parseDistanceToKm(input, unit)?.times(1_000.0)

    fun strengthLineSubtitle(reps: Int, weightKg: Double, unit: WeightUnit): String =
        "$reps reps · ${weightKg.toWeightLabel(unit)}"

    fun cardioLineSubtitle(
        minutes: Int,
        distanceKm: Double?,
        indoor: Boolean = false,
        distance: DistanceUnit = DistanceUnit.KM,
    ): String {
        val distancePart = distanceKm?.let { km ->
            " · ${formatDistanceKm(km, distance)}"
        }.orEmpty()
        val place = if (indoor) " · ${CardioCopy.INDOOR}" else ""
        return "$minutes min$distancePart$place"
    }

    fun formatDistanceKm(km: Double, unit: DistanceUnit): String {
        return when (unit) {
            DistanceUnit.KM -> "$km ${unit.suffix}"
            DistanceUnit.MI -> {
                val miles = km * 1_000.0 / DistanceUnit.METERS_PER_MILE
                val rounded = kotlin.math.round(miles * 10.0) / 10.0
                "$rounded ${unit.suffix}"
            }
        }
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
