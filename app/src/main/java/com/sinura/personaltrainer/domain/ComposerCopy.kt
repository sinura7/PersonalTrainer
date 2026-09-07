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

    /**
     * One Add set, judged as a whole so both boxes can complain at once.
     *
     * A blank weight is bodyweight — the field starts at 0 and the hint on every other weight
     * field in the app says "leave empty for bodyweight only" — so blank reads as 0 kg. That is
     * the only default here. Reps has none: a set with no rep count is not a set, and the old
     * `toIntOrNull() ?: 0` fall-through handed the ViewModel a zero it then had to refuse with
     * a banner at the top of a long list.
     */
    fun strengthEntry(weightText: String, repsText: String, unit: WeightUnit): StrengthEntry {
        val weight = NumericEntry.typedWeightKg(weightText, unit)
        val reps = NumericEntry.typedReps(repsText)
        val weightError = weight.messageOrNull
        val repsError = reps.messageOrNull ?: if (reps is NumericEntry.Typed.Blank) NumericEntry.REPS_RULE else null
        if (weightError != null || repsError != null) {
            return StrengthEntry.RefusedSet(weightError = weightError, repsError = repsError)
        }
        return StrengthEntry.ReadySet(
            weightKg = weight.valueOrNull ?: 0.0,
            reps = checkNotNull(reps.valueOrNull),
        )
    }

    /**
     * One Add cardio. Minutes is required and whole; distance is optional and, when typed,
     * must read as a number in the display unit. Neither box is ever rewritten to make it fit.
     */
    fun cardioEntry(minutesText: String, distanceText: String, unit: DistanceUnit): CardioEntry {
        val minutes = NumericEntry.typedWhole(input = minutesText, min = 1, rule = NumericEntry.MINUTES_RULE)
        val distance = NumericEntry.typedDistanceKm(distanceText, unit)
        val minutesError = minutes.messageOrNull
            ?: if (minutes is NumericEntry.Typed.Blank) NumericEntry.MINUTES_RULE else null
        val distanceError = distance.messageOrNull
        if (minutesError != null || distanceError != null) {
            return CardioEntry.RefusedCardio(minutesError = minutesError, distanceError = distanceError)
        }
        return CardioEntry.ReadyCardio(
            minutes = checkNotNull(minutes.valueOrNull),
            distanceKm = distance.valueOrNull,
        )
    }

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

/**
 * What one Add set attempt amounted to. [RefusedSet] carries a message per box that broke a rule.
 * The variants carry the entry's name so a `when` over them is unambiguous to read and to check.
 */
sealed interface StrengthEntry {
    data class ReadySet(val weightKg: Double, val reps: Int) : StrengthEntry

    data class RefusedSet(val weightError: String?, val repsError: String?) : StrengthEntry
}

/** What one Add cardio attempt amounted to. */
sealed interface CardioEntry {
    data class ReadyCardio(val minutes: Int, val distanceKm: Double?) : CardioEntry

    data class RefusedCardio(val minutesError: String?, val distanceError: String?) : CardioEntry
}
