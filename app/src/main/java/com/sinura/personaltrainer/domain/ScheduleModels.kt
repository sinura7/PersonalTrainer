package com.sinura.personaltrainer.domain

import java.time.DayOfWeek

enum class SplitStyle(
    val storageKey: String,
    val displayName: String,
    val blurb: String,
) {
    AUTO("auto", "Auto", "Picks a split from your days and routines"),
    UPPER_LOWER("upper_lower", "Upper / Lower", "Alternate upper and lower sessions"),
    PUSH_PULL_LEGS("ppl", "Push / Pull / Legs", "Push, pull, then legs"),
    FULL_BODY("full_body", "Full Body", "Hit the whole body each session"),
    CUSTOM("custom", "Custom", "Cycle the routines you already have"),
    ;

    companion object {
        fun fromStorage(value: String?): SplitStyle =
            entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) } ?: AUTO
    }
}

data class SchedulePreferences(
    val trainingDaysPerWeek: Int = DEFAULT_DAYS,
    val splitStyle: SplitStyle = SplitStyle.AUTO,
    val weekStart: DayOfWeek = DEFAULT_WEEK_START,
) {
    fun sanitized(): SchedulePreferences = copy(
        trainingDaysPerWeek = trainingDaysPerWeek.coerceIn(MIN_DAYS, MAX_DAYS),
        weekStart = weekStart,
    )

    companion object {
        const val MIN_DAYS = 2
        const val MAX_DAYS = 6
        const val DEFAULT_DAYS = 4

        /**
         * Named rather than spelled `DayOfWeek.MONDAY` at each site, because the guided setup
         * had its own Monday default in a parameter nobody passed, and it silently overwrote
         * the lifter's stored choice on every run.
         */
        val DEFAULT_WEEK_START: DayOfWeek = DayOfWeek.MONDAY
        val DEFAULT = SchedulePreferences()

        fun weekStartFromStorage(value: String?): DayOfWeek =
            DayOfWeek.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: DEFAULT_WEEK_START
    }
}

enum class SessionFocusKind(
    val label: String,
    val regionHint: MuscleRegion?,
) {
    UPPER("Upper", MuscleRegion.UPPER),
    LOWER("Lower Body", MuscleRegion.LOWER),
    PUSH("Push", MuscleRegion.UPPER),
    PULL("Pull", MuscleRegion.UPPER),
    LEGS("Legs", MuscleRegion.LOWER),
    FULL_BODY("Full Body", null),
    RECOVERY("Recovery lean", null),
    ;

    val isUpperFamily: Boolean get() = regionHint == MuscleRegion.UPPER
    val isLowerFamily: Boolean get() = regionHint == MuscleRegion.LOWER
}

enum class ScheduleConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

data class SuggestedTrainingDay(
    val epochDay: Long,
    val dayOfWeek: DayOfWeek,
    val isRest: Boolean,
    val focusKind: SessionFocusKind,
    val focusTitle: String,
    val routineId: String?,
    val routineName: String?,
    val reason: String,
    val emphasisMuscles: List<CanonicalMuscle>,
    val confidence: ScheduleConfidence,
    /**
     * The stored slot this day came from, or null for a planner proposal.
     *
     * This is how any surface tells "you decided this" from "the app is suggesting this"
     * without a second type or a parallel list. Defaulted so the planner's own construction
     * sites — which produce proposals — need no change.
     */
    val slotId: String? = null,
)

data class WeeklySchedulePlan(
    val weekStartEpochDay: Long,
    val generatedAtMs: Long,
    val preferences: SchedulePreferences,
    val resolvedSplit: SplitStyle,
    val days: List<SuggestedTrainingDay>,
    val thinHistory: Boolean,
    val summary: String,
) {
    val trainingDays: List<SuggestedTrainingDay> get() = days.filterNot { it.isRest }

    fun dayOn(epochDay: Long): SuggestedTrainingDay? = days.firstOrNull { it.epochDay == epochDay }

    fun nextTrainingOnOrAfter(epochDay: Long): SuggestedTrainingDay? =
        days.firstOrNull { !it.isRest && it.epochDay >= epochDay }
}
