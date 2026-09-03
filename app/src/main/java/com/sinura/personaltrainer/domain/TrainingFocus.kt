package com.sinura.personaltrainer.domain

/**
 * What the lifter wants Temper to record first.
 *
 * Asked at setup so a cardio-only install is not handed a lift week it
 * will never start. Nothing is written until "Use this plan."
 */
enum class TrainingFocus(val displayName: String, val blurb: String) {
    STRENGTH("Strength", "Lifts, sets, and a week you can start."),
    CARDIO("Cardio", "Runs, rides, and other timed work."),
    BOTH("Both", "Lifting days and cardio, kept as their own work."),
    ;

    companion object {
        fun fromStorage(raw: String?): TrainingFocus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: STRENGTH
    }
}
