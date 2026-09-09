package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime

/**
 * The three windows the body map offers.
 *
 * Day / this week / this month are the same questions History totals, asked of
 * muscle load rather than session counts. Year and all-time stay History
 * chips — a second silhouette for those horizons is a different product.
 * A stored `LAST_30_DAYS` from an older build becomes this month.
 */
enum class HeatWindow(
    val label: String,
    val shortLabel: String,
) {
    DAY("Today", "Day"),
    CURRENT_WEEK("This week", "Week"),
    CURRENT_MONTH("This month", "Month"),
    ;

    /**
     * @param weekStart first day of the training week, from SchedulePreferences. Only
     * [CURRENT_WEEK] uses it, but it must be the same value the weekly planner uses or
     * "this week" means two different things in two places.
     */
    fun startMs(
        nowMs: Long,
        time: TimePort = JvmTime,
        weekStart: Weekday = Weekday.MONDAY,
        zoneId: String = time.defaultZoneId(),
    ): Long {
        val today = time.civilDate(nowMs, zoneId)
        return when (this) {
            DAY -> time.startOfDayMillis(today, zoneId)
            CURRENT_WEEK -> {
                val weekStartDate = today.previousOrSame(weekStart)
                time.startOfDayMillis(weekStartDate, zoneId)
            }
            CURRENT_MONTH -> time.startOfDayMillis(
                CivilDate(today.year, today.month, 1),
                zoneId,
            )
        }
    }

    companion object {
        /** Older Body chips stored this name. Map it so a restore still has a window. */
        const val LEGACY_LAST_30_DAYS = "LAST_30_DAYS"

        /** Tolerant by design: a window stored by an older build must not break the map. */
        fun fromStorage(raw: String?): HeatWindow = when (raw) {
            null -> CURRENT_WEEK
            LEGACY_LAST_30_DAYS -> CURRENT_MONTH
            else -> entries.firstOrNull { it.name == raw } ?: CURRENT_WEEK
        }
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

        /**
         * Coach dose: below four weekly sets is not enough, even if the
         * silhouette already shows that the muscle was touched.
         */
        fun fromWeeklySets(sets: Double): HeatBand = when {
            sets < LOW_MIN_SETS -> UNTRAINED
            sets < PRODUCTIVE_MIN_SETS -> LOW
            sets <= HIGH_MIN_SETS -> PRODUCTIVE
            else -> HIGH
        }

        /**
         * Map readout: any work in the window is Low, not Rest. Rest is
         * reserved for a muscle the window never touched.
         */
        fun fromWindowSets(sets: Double): HeatBand = when {
            sets <= 0.0 -> UNTRAINED
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
     * Weighted stimulus in this window: junction weight × effort (reps and RPE).
     *
     * Named weekly for the coach, which still reasons in sets per week. The map
     * uses the raw window total so Day / Week / Month answer "what did I hit
     * in this window", not a rate that lights Day on fire.
     */
    val weeklySets: Double,
    val heat: Double,
    val exercises: List<ExerciseLoadContribution>,
) {
    val band: HeatBand get() = HeatBand.fromWindowSets(weeklySets)
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
    /**
     * What the figure was built from, in units History can confirm: finished sessions
     * with a working set inside the window, and how long since the last strength
     * session anywhere in history finished. A blank figure over "No sessions this week ·
     * last finished 9 days ago" is a fact; a blank figure alone is a question.
     */
    val windowSessions: Int = 0,
    val lastFinishedAtMs: Long? = null,
    val daysSinceLastFinished: Int? = null,
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

    /**
     * Heat math stays on the windowed graph. "Have I ever trained" must
     * not: a 31-day gap would otherwise blank Body while History still
     * lists the last session.
     */
    fun rememberLifetimeWork(
        summaries: List<SessionSummary>,
        nowMs: Long = generatedAtMs,
        time: TimePort = JvmTime,
        zoneId: String = time.defaultZoneId(),
    ): BodyHeatSnapshot {
        val anyWork = hasAnyWorkingSets || summaries.any { it.hasLoggedWork() }
        // The newest strength finish in all of history, not just the source window: a
        // 40-day gap reads as "last finished 40 days ago", not as nothing. Cardio-only
        // days keep the "ever trained" flag but are not a finish the figure was built from.
        val lifetimeLast = summaries
            .filter { it.workingSets > 0 }
            .maxOfOrNull { it.finishedAt ?: it.date }
        val last = listOfNotNull(lastFinishedAtMs, lifetimeLast).maxOrNull()
        if (anyWork == hasAnyWorkingSets && last == lastFinishedAtMs) return this
        return copy(
            hasAnyWorkingSets = anyWork,
            lastFinishedAtMs = last,
            daysSinceLastFinished = MuscleLoadCalculator.daysSince(last, nowMs, time, zoneId),
        )
    }

    /**
     * Heat stays on the windowed graph. Recency must not: a muscle last
     * trained 40 days ago is not "Not trained yet."
     */
    fun rememberLifetimeRecency(
        lastTrainedByMuscle: Map<CanonicalMuscle, Long>,
        nowMs: Long,
        time: TimePort = JvmTime,
        zoneId: String = time.defaultZoneId(),
    ): BodyHeatSnapshot {
        if (lastTrainedByMuscle.isEmpty()) return this
        return copy(
            loads = loads.map { load ->
                val lifetime = lastTrainedByMuscle[load.muscle] ?: return@map load
                val current = load.lastTrainedAtMs
                if (current != null && current >= lifetime) load
                else load.copy(
                    lastTrainedAtMs = lifetime,
                    daysSinceLastTrained = MuscleLoadCalculator.daysSince(
                        lifetime,
                        nowMs,
                        time,
                        zoneId,
                    ),
                )
            },
        )
    }
}

/** Last logged working set per muscle, from the exercise recency query. */
object MuscleRecency {
    fun byMuscle(
        lastLoggedAtByExerciseId: Map<String, Long>,
        catalog: Map<String, Exercise>,
    ): Map<CanonicalMuscle, Long> {
        val out = mutableMapOf<CanonicalMuscle, Long>()
        lastLoggedAtByExerciseId.forEach { (exerciseId, atMs) ->
            val exercise = catalog[exerciseId] ?: return@forEach
            musclesOf(exercise).forEach { muscle ->
                val previous = out[muscle]
                if (previous == null || atMs > previous) out[muscle] = atMs
            }
        }
        return out
    }

    fun musclesOf(exercise: Exercise): List<CanonicalMuscle> {
        val fromJunction = exercise.muscles.mapNotNull { credit ->
            MuscleNormalizer.resolveKey(credit.muscleKey)
        }
        if (fromJunction.isNotEmpty()) return fromJunction.distinct()
        val derived = MuscleNormalizer.primaryOf(exercise.muscleGroup)
        return listOf(derived)
    }
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
