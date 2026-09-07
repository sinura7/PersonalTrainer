package com.sinura.personaltrainer.domain

/**
 * The four target boxes on one routine-editor lift card, read as the owner left them.
 *
 * Each box is judged on its own so the card can put the complaint under the box that earned
 * it. The rules are the ones the stored row enforces — sets and reps at least 1, rest in whole
 * seconds, weight zero or more with at most two decimals — and a box that breaks one is
 * [NumericEntry.Typed.Invalid] rather than quietly read as "leave this one alone". The
 * distinction matters: an *empty* box does mean leave it alone (the owner cleared it to
 * retype), but `8.5` in the reps box is a value the owner meant, and treating it as empty
 * would keep the stored 5 under a field that visibly says 8.5.
 */
data class TargetEntry(
    val sets: NumericEntry.Typed<Int>,
    val reps: NumericEntry.Typed<Int>,
    val rest: NumericEntry.Typed<Int>,
    val weightKg: NumericEntry.Typed<Double>,
) {
    val setsError: String? get() = sets.messageOrNull
    val repsError: String? get() = reps.messageOrNull
    val restError: String? get() = rest.messageOrNull
    val weightError: String? get() = weightKg.messageOrNull

    /** True when any box holds text that cannot be stored as written. */
    val hasError: Boolean
        get() = setsError != null || repsError != null || restError != null || weightError != null

    /** The first complaint in reading order — sets, reps, rest, weight — for the card's summary line. */
    val firstError: String?
        get() = setsError ?: repsError ?: restError ?: weightError

    /** Typed value, or null for an empty box. Only meaningful when [hasError] is false. */
    val typedSets: Int? get() = sets.valueOrNull
    val typedReps: Int? get() = reps.valueOrNull
    val typedRest: Int? get() = rest.valueOrNull

    /**
     * The weight to stage. A cleared weight box is a real answer ("no target"), which is why
     * blank maps to null here rather than to "keep the stored one"; a typed zero means the
     * same thing, as it always has on this card.
     */
    val typedWeightKg: Double? get() = weightKg.valueOrNull?.takeIf { it > 0.0 }

    companion object {
        fun read(
            setsText: String,
            repsText: String,
            restText: String,
            weightText: String,
            unit: WeightUnit,
        ): TargetEntry = TargetEntry(
            sets = NumericEntry.typedWhole(input = setsText, min = 1, rule = NumericEntry.SETS_RULE),
            reps = NumericEntry.typedReps(repsText),
            rest = NumericEntry.typedWhole(input = restText, min = 0, rule = NumericEntry.REST_RULE),
            weightKg = NumericEntry.typedWeightKg(weightText, unit),
        )
    }
}
