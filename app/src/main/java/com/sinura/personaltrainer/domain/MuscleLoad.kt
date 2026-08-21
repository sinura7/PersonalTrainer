package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * The two windows the body map offers.
 *
 * There used to be four candidates and three shipped — 7 days, 14 days, this week — and they
 * are not different questions. "Last 7 days" and "this week" answer the same one badly: a
 * rolling week has no boundary you can plan against, so a Sunday session and a Monday session
 * look the same in it, and the number never settles. "This week so far" and "a month's
 * average" are genuinely different questions, and two chips is a choice rather than a menu.
 */
enum class HeatWindow(
    val label: String,
    val shortLabel: String,
) {
    CURRENT_WEEK("This week", "Week"),
    LAST_30_DAYS("Last 30 days", "30 days"),
    ;

    /**
     * @param weekStart first day of the training week, from SchedulePreferences. Only
     * [CURRENT_WEEK] uses it, but it must be the same value the weekly planner uses or
     * "this week" means two different things in two places.
     */
    fun startMs(nowMs: Long, zone: ZoneId, weekStart: DayOfWeek = DayOfWeek.MONDAY): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        return when (this) {
            LAST_30_DAYS -> now.minusDays(30).toInstant().toEpochMilli()
            CURRENT_WEEK -> now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(weekStart))
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }
    }

    companion object {
        /** Tolerant by design: a window stored by an older build must not break the map. */
        fun fromStorage(raw: String?): HeatWindow =
            entries.firstOrNull { it.name == raw } ?: CURRENT_WEEK
    }
}

/**
 * How much a muscle is actually getting, in the unit training is prescribed in.
 *
 * The old bands were cuts on a *relative* number: heat was volume divided by the window's
 * hardest-worked muscle, so the map answered "which of my muscles got the most" and never
 * "am I doing enough". Two consequences, both bad. A week where you trained one muscle hard
 * and everything else lightly painted that muscle max-orange and the rest cold — which is
 * true as a ranking and false as advice. And the colours moved when nothing about a muscle
 * changed: add a heavy leg day and your chest goes cooler without losing a single set.
 *
 * These cuts are absolute weekly weighted sets, which is the unit every training source
 * prescribes in, so the map can be wrong in a way you can check.
 */
enum class HeatBand {
    UNTRAINED,
    LOW,
    PRODUCTIVE,
    HIGH,
    ;

    val legendLabel: String
        get() = when (this) {
            UNTRAINED -> "Untrained"
            LOW -> "Low"
            PRODUCTIVE -> "Productive"
            HIGH -> "High"
        }

    companion object {
        const val LOW_MIN_SETS = 4.0
        const val PRODUCTIVE_MIN_SETS = 10.0
        const val HIGH_MIN_SETS = 20.0

        fun fromWeeklySets(sets: Double): HeatBand = when {
            sets < LOW_MIN_SETS -> UNTRAINED
            sets < PRODUCTIVE_MIN_SETS -> LOW
            sets <= HIGH_MIN_SETS -> PRODUCTIVE
            else -> HIGH
        }
    }
}

data class ExerciseLoadContribution(
    val exerciseId: String,
    val exerciseName: String,
    val volumeKg: Double,
    /** Reps of this lift, when reps rather than kilograms are what it is measured in. */
    val bodyweightReps: Int = 0,
    val workingSets: Int,
) {
    val work: SetWork get() = SetWork(volumeKg = volumeKg, bodyweightReps = bodyweightReps)
}

data class MuscleLoadSummary(
    val muscle: CanonicalMuscle,
    val volumeKg: Double,
    /**
     * Bodyweight reps credited to this muscle in the window.
     *
     * Carried alongside [volumeKg] rather than folded into it, because a muscle trained only
     * with push-ups has no honest kilogram total and used to be given an invented one. A
     * calisthenics chest reading "0 kg" would be a worse lie than the 40 kg stand-in was; it
     * reads as its rep count instead. The band is unaffected either way — [weeklySets] is what
     * it comes from, and always was.
     */
    val bodyweightReps: Int = 0,
    val workingSets: Int,
    val sessionCount: Int,
    val lastTrainedAtMs: Long?,
    val daysSinceLastTrained: Int?,
    /**
     * Weighted sets per week for this muscle in the window.
     *
     * "Weighted" because a set credits each muscle it trains by that lift's junction weight —
     * a bench press is one set of chest and half a set of triceps — and "per week" because the
     * 30-day window is averaged down so the two chips are comparable numbers rather than one
     * number that is four times bigger for being four times longer.
     */
    val weeklySets: Double,
    val heat: Double,
    val exercises: List<ExerciseLoadContribution>,
) {
    val band: HeatBand get() = HeatBand.fromWeeklySets(weeklySets)
    val trainedInWindow: Boolean get() = workingSets > 0
    val work: SetWork get() = SetWork(volumeKg = volumeKg, bodyweightReps = bodyweightReps)
}

data class BodyHeatSnapshot(
    val window: HeatWindow,
    val windowStartMs: Long,
    val generatedAtMs: Long,
    val loads: List<MuscleLoadSummary>,
    val hasAnyWorkingSets: Boolean,
    val hasWindowWorkingSets: Boolean,
) {
    fun load(muscle: CanonicalMuscle): MuscleLoadSummary =
        loads.firstOrNull { it.muscle == muscle }
            ?: MuscleLoadSummary(
                muscle = muscle,
                volumeKg = 0.0,
                bodyweightReps = 0,
                workingSets = 0,
                sessionCount = 0,
                lastTrainedAtMs = null,
                daysSinceLastTrained = null,
                weeklySets = 0.0,
                heat = 0.0,
                exercises = emptyList(),
            )

    val mapLoads: List<MuscleLoadSummary>
        get() = CanonicalMuscle.bodyMapOrder.map { load(it) }
}

/**
 * One muscle, as the coach sees it: how much work per week, and how long since any.
 *
 * Separate from [MuscleLoadSummary] because it is a different question with a different
 * window. The summary answers "what does the map show for the window I picked"; this answers
 * "what is actually happening", over a fixed 14 days, whatever the map is showing.
 */
data class CoachMuscleLoad(
    val muscle: CanonicalMuscle,
    val weeklySets: Double,
    val daysSinceLastTrained: Int?,
) {
    val band: HeatBand get() = HeatBand.fromWeeklySets(weeklySets)
}

data class CoachBasis(
    val generatedAtMs: Long,
    val loads: Map<CanonicalMuscle, CoachMuscleLoad>,
    val hasAnyWorkingSets: Boolean,
    /** Whether anything at all was trained inside the trailing window. */
    val hasBasisWorkingSets: Boolean,
) {
    fun load(muscle: CanonicalMuscle): CoachMuscleLoad =
        loads[muscle] ?: CoachMuscleLoad(muscle = muscle, weeklySets = 0.0, daysSinceLastTrained = null)
}
