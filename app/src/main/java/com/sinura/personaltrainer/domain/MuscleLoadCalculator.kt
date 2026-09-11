package com.sinura.personaltrainer.domain

import kotlin.math.max

object MuscleLoadCalculator {
    const val SECONDARY_VOLUME_WEIGHT = 0.4

    /** Where each band starts, in weighted sets per week. Mirrored on [HeatBand]. */
    const val LOW_MIN_SETS = HeatBand.LOW_MIN_SETS
    const val PRODUCTIVE_MIN_SETS = HeatBand.PRODUCTIVE_MIN_SETS
    const val HIGH_MIN_SETS = HeatBand.HIGH_MIN_SETS

    /** Past this, more sets do not make the silhouette any hotter. */
    const val HIGH_SATURATION_SETS = 30.0

    /**
     * A wash starts at the first credited set. Four weekly sets is the coach's
     * "enough" floor, not the silhouette's on-switch — otherwise a real chest
     * day of three working sets looked like rest.
     *
     * [FRACTION_TOUCHED] matches the legend's Low swatch so one set is visible,
     * not parked on `heatColor`'s empty cutoff.
     */
    const val FRACTION_TOUCHED = 0.22
    const val FRACTION_LOW = FRACTION_TOUCHED
    const val FRACTION_PRODUCTIVE = 0.6733
    const val FRACTION_HIGH = 1.0

    /** How far back the coach looks, regardless of which window the map is showing. */
    const val COACH_TRAILING_DAYS = 14L

    /**
     * Kilograms that were genuinely external.
     *
     * This used to price a bodyweight set at a flat 40 kg stand-in so that everything could be
     * one number. [SetWork] replaced that: bodyweight lifts are measured in reps, and the only
     * kilograms reported are the ones that were really on the bar or in the vest. Kept as a
     * thin wrapper because tonnage alone is what several callers genuinely want.
     */
    fun setVolumeKg(weightKg: Double, reps: Int, loadClass: LoadClass): Double =
        SetWork.of(weightKg, reps, loadClass).volumeKg

    fun snapshot(
        sessions: List<WorkoutSession>,
        window: HeatWindow,
        nowMs: Long,
        time: TimePort,
        zoneId: String = time.defaultZoneId(),
        exerciseCatalog: Map<String, Exercise> = emptyMap(),
        weekStart: Weekday = Weekday.MONDAY,
    ): BodyHeatSnapshot {
        val windowStart = window.startMs(nowMs, time, weekStart, zoneId)
        val finished = sessions.filter { it.isFinished }
        val acc = CanonicalMuscle.entries.associateWith { MuscleAccumulator() }.toMutableMap()
        var anyWorkingSets = false
        var windowWorkingSets = false
        val windowSessionIds = mutableSetOf<String>()
        var lastFinishedAtMs: Long? = null

        finished.forEach { session ->
            val working = session.sets.filterNot { it.isWarmup }
            if (working.isNotEmpty()) {
                val finishedAt = session.finishedAt ?: session.date
                lastFinishedAtMs = maxOf(lastFinishedAtMs ?: finishedAt, finishedAt)
            }
            working.forEach { set ->
                anyWorkingSets = true
                val trainedAt = trainedAtMs(session, set)
                val credits = creditsFor(set, session, exerciseCatalog)
                val work = SetWork.of(set.weightKg, set.reps, loadClassFor(set, session, exerciseCatalog))
                val inWindow = trainedAt >= windowStart && trainedAt <= nowMs

                credits.forEach { (muscle, _) ->
                    acc.getValue(muscle).recordLifetime(trainedAt)
                }

                if (inWindow) {
                    windowWorkingSets = true
                    windowSessionIds += session.id
                    credits.forEach { (muscle, weight) ->
                        acc.getValue(muscle).recordWindow(
                            sessionId = session.id,
                            // Tonnage is split by junction weight; reps are not. Half a set of
                            // triceps is half the kilograms, but "six reps" cannot be three
                            // reps of triceps — a rep is a thing that happened, not a quantity
                            // to divide. Whole reps are credited to every muscle the lift
                            // trains, which is also how a rep count is read out loud.
                            volumeKg = work.volumeKg * weight,
                            bodyweightReps = work.bodyweightReps,
                            weightedSets = weight * setStimulus(set),
                            set = set,
                        )
                    }
                }
            }
        }

        val loads = CanonicalMuscle.entries.map { muscle ->
            val row = acc.getValue(muscle)
            val weeklySets = weeklySetsFor(row.windowWeightedSets, window)
            MuscleLoadSummary(
                muscle = muscle,
                volumeKg = row.windowVolumeKg,
                bodyweightReps = row.windowBodyweightReps,
                workingSets = row.windowSets,
                sessionCount = row.windowSessions.size,
                lastTrainedAtMs = row.lastTrainedAtMs,
                daysSinceLastTrained = daysSince(row.lastTrainedAtMs, nowMs, time, zoneId),
                weeklySets = weeklySets,
                heat = heatFraction(weeklySets),
                exercises = row.exercises.values
                    .sortedWith(compareByDescending<ExerciseLoadContribution> { it.volumeKg }.thenBy { it.exerciseName })
                    .toList(),
            )
        }
        return BodyHeatSnapshot(
            window = window,
            windowStartMs = windowStart,
            generatedAtMs = nowMs,
            loads = loads,
            hasAnyWorkingSets = anyWorkingSets,
            hasWindowWorkingSets = windowWorkingSets,
            windowSessions = windowSessionIds.size,
            lastFinishedAtMs = lastFinishedAtMs,
            daysSinceLastFinished = daysSince(lastFinishedAtMs, nowMs, time, zoneId),
        )
    }

    /**
     * How hot a muscle looks, from how much work it actually got in the window.
     *
     * Rest is zero. Any credited set paints at [FRACTION_TOUCHED] so the figure
     * answers "what did I hit", not only "was it enough for the week". The
     * productive band stays a flat tone; past it the ramp climbs to saturation.
     */
    fun heatFraction(weeklySets: Double): Double = when {
        weeklySets <= 0.0 -> 0.0
        weeklySets < PRODUCTIVE_MIN_SETS -> lerp(
            from = FRACTION_TOUCHED,
            to = FRACTION_PRODUCTIVE,
            t = weeklySets / PRODUCTIVE_MIN_SETS,
        )
        weeklySets <= HIGH_MIN_SETS -> FRACTION_PRODUCTIVE
        weeklySets < HIGH_SATURATION_SETS -> lerp(
            from = FRACTION_PRODUCTIVE,
            to = FRACTION_HIGH,
            t = (weeklySets - HIGH_MIN_SETS) / (HIGH_SATURATION_SETS - HIGH_MIN_SETS),
        )
        else -> FRACTION_HIGH
    }

    /**
     * Window stimulus, as the map's dose number.
     *
     * Day / week / month are totals for that window. A rolling 30-day average
     * used to make the long chip comparable to the week chip; the month chip
     * is "this month so far", the same honesty as an early week.
     */
    fun weeklySetsFor(windowWeightedSets: Double, window: HeatWindow): Double {
        // Window is part of the signature so call sites stay honest about which
        // chip produced the number. All three windows use the raw total.
        return when (window) {
            HeatWindow.DAY,
            HeatWindow.CURRENT_WEEK,
            HeatWindow.CURRENT_MONTH,
            -> windowWeightedSets
        }
    }

    private fun lerp(from: Double, to: Double, t: Double): Double =
        from + (to - from) * t.coerceIn(0.0, 1.0)

    /**
     * What the coach reasons from, over a fixed trailing window.
     *
     * Deliberately NOT the display snapshot. The recommendations used to be computed from
     * whatever window the user had tapped, so flipping a display chip changed the advice —
     * the same training, on the same day, produced a different opinion about what to do next
     * depending on how you were looking at it. Advice that moves when you change the view is
     * not advice.
     *
     * Fourteen days because it is long enough to average out one missed session and short
     * enough to notice a month of neglect. Recency ([CoachMuscleLoad.daysSinceLastTrained])
     * still reads all of history, exactly as the display snapshot does: "42 days since a
     * working set" has to be true, not clipped to the window.
     *
     * Cost, stated plainly: one extra O(total sets) pass per insights emission, on the same
     * background dispatcher as the display snapshot. No caching and no extra queries — the
     * history is already in memory when this runs.
     */
    /**
     * @param lastTrainedByMuscle lifetime recency, for the muscles [sessions] cannot see.
     *
     * [sessions] is the windowed history — 32 days — so a muscle last trained before that
     * has no row in it and its recency came back null, which the coach renders as
     * "has no logged work" beside a body map correctly saying "35 days since". The display
     * snapshot has always overlaid this (`rememberLifetimeRecency`); the basis did not.
     */
    fun coachBasis(
        sessions: List<WorkoutSession>,
        nowMs: Long,
        time: TimePort,
        zoneId: String = time.defaultZoneId(),
        exerciseCatalog: Map<String, Exercise> = emptyMap(),
        lastTrainedByMuscle: Map<CanonicalMuscle, Long> = emptyMap(),
    ): CoachBasis {
        val basisStart = time.minusCivilDays(nowMs, zoneId, COACH_TRAILING_DAYS)
        val weighted = CanonicalMuscle.entries.associateWith { 0.0 }.toMutableMap()
        val lastTrained = mutableMapOf<CanonicalMuscle, Long>()
        var anyWorkingSets = false
        var basisWorkingSets = false

        sessions.filter { it.isFinished }.forEach { session ->
            session.sets.filterNot { it.isWarmup }.forEach { set ->
                anyWorkingSets = true
                val trainedAt = trainedAtMs(session, set)
                val credits = creditsFor(set, session, exerciseCatalog)
                credits.forEach { (muscle, weight) ->
                    lastTrained[muscle] = max(lastTrained[muscle] ?: 0L, trainedAt)
                    if (trainedAt >= basisStart && trainedAt <= nowMs) {
                        basisWorkingSets = true
                        weighted[muscle] = (weighted[muscle] ?: 0.0) + weight * setStimulus(set)
                    }
                }
            }
        }

        // Older than the window, so it never appeared in the loop above. Weighted load stays
        // untouched: this says WHEN a muscle was last worked, never how much.
        lastTrainedByMuscle.forEach { (muscle, trainedAt) ->
            if (trainedAt > 0L) {
                lastTrained[muscle] = max(lastTrained[muscle] ?: 0L, trainedAt)
                anyWorkingSets = true
            }
        }

        return CoachBasis(
            generatedAtMs = nowMs,
            loads = CanonicalMuscle.entries.associateWith { muscle ->
                CoachMuscleLoad(
                    muscle = muscle,
                    // Fourteen days of weighted sets, expressed per week, so the coach's
                    // thresholds are the same numbers the map's bands use.
                    weeklySets = (weighted[muscle] ?: 0.0) * 7.0 / COACH_TRAILING_DAYS,
                    daysSinceLastTrained = daysSince(lastTrained[muscle], nowMs, time, zoneId),
                )
            },
            hasAnyWorkingSets = anyWorkingSets,
            hasBasisWorkingSets = basisWorkingSets,
        )
    }

    fun daysSince(
        lastTrainedAtMs: Long?,
        nowMs: Long,
        time: TimePort,
        zoneId: String = time.defaultZoneId(),
    ): Int? {
        if (lastTrainedAtMs == null) return null
        val last = time.civilDate(lastTrainedAtMs, zoneId)
        val now = time.civilDate(nowMs, zoneId)
        return (now.epochDay - last.epochDay).toInt().coerceAtLeast(0)
    }

    /**
     * What one set is worth, per muscle.
     *
     * The order is catalog-first, and that is a deliberate reversal of v1. v1 read the muscle
     * group embedded in the session row before it read the catalog, so that an exercise renamed
     * or reclassified later would not retroactively rewrite what an old session meant. With a
     * junction table that reasoning inverts: the catalog now holds a real per-muscle split that
     * the session row cannot express at all, and the whole point of shipping it is that old
     * history is re-scored honestly — a deadlift logged last year credited the back fully and
     * the glutes at 0.4, which is not what a deadlift does.
     *
     * Each step down is a fallback for something the step above could not answer:
     *
     * 1. Catalog junction — the real answer, when the lift is in the library.
     * 2. Embedded junction — a session carrying its own credits (a restored backup, mid-restore).
     * 3. Embedded muscleGroup, derived — the v1 model, for a lift no longer in the library.
     * 4. Catalog muscleGroup, derived — the v1 model, from the catalog row.
     * 5. OTHER at full weight — off the body map, exactly as before.
     *
     * Step 3 is also what keeps the Body tab honest in the window between the migration
     * finishing and the first seed pass building the junction: no credits yet, same numbers as
     * yesterday.
     */
    /**
     * How this set is measured, resolved the same way [creditsFor] resolves its muscles: the
     * catalog first, then the copy embedded in the session. A lift deleted from the library
     * after it was trained still has its class recorded in the session that used it, which is
     * what stops old history silently changing units.
     */
    private fun loadClassFor(
        set: SetLog,
        session: WorkoutSession,
        catalog: Map<String, Exercise>,
    ): LoadClass {
        val loadType = catalog[set.exerciseId]?.loadType
            ?: session.exercises.firstOrNull { it.exercise.id == set.exerciseId }?.exercise?.loadType
        return LoadClass.of(loadType)
    }

    private fun creditsFor(
        set: SetLog,
        session: WorkoutSession,
        catalog: Map<String, Exercise>,
    ): List<Pair<CanonicalMuscle, Double>> {
        val embedded = session.exercises.firstOrNull { it.exercise.id == set.exerciseId }?.exercise
        val fromCatalog = catalog[set.exerciseId]

        resolveCredits(fromCatalog?.muscles, fromCatalog?.muscleGroup ?: embedded?.muscleGroup)
            ?.let { return it }
        resolveCredits(embedded?.muscles, embedded?.muscleGroup)?.let { return it }

        val fallbackGroup = embedded?.muscleGroup?.takeIf { it.isNotBlank() }
            ?: fromCatalog?.muscleGroup
            ?: ""
        val derived = MuscleNormalizer.deriveCredits(fallbackGroup)
        return derived.toPairs(fallbackGroup).ifEmpty {
            listOf(CanonicalMuscle.OTHER to 1.0)
        }
    }

    private fun resolveCredits(
        credits: List<MuscleCredit>?,
        muscleGroupForFallback: String?,
    ): List<Pair<CanonicalMuscle, Double>>? {
        if (credits.isNullOrEmpty()) return null
        return credits.toPairs(muscleGroupForFallback).takeIf { it.isNotEmpty() }
    }

    /**
     * Resolves stored muscle keys to canonical muscles, merging duplicates.
     *
     * A key the alias index cannot place falls back to the exercise's own muscle group rather
     * than to OTHER: a lift whose credits are unreadable is still a lift that trained something,
     * and dropping it off the body map is a worse answer than approximating it.
     */
    private fun List<MuscleCredit>.toPairs(
        muscleGroupForFallback: String?,
    ): List<Pair<CanonicalMuscle, Double>> {
        val merged = LinkedHashMap<CanonicalMuscle, Double>()
        forEach { credit ->
            val muscle = MuscleNormalizer.resolveKey(credit.muscleKey)
                ?: MuscleNormalizer.primaryOf(muscleGroupForFallback)
            merged[muscle] = (merged[muscle] ?: 0.0) + credit.weight
        }
        return merged.map { (muscle, weight) -> muscle to weight }
    }

    /**
     * When a set counts as having happened. Internal rather than private because the deload
     * signal buckets volume by weeks and has to use the same attribution — two different
     * answers to "what day was this" would put the same set in different weeks.
     */
    internal fun trainedAtMs(session: WorkoutSession, set: SetLog): Long {
        val candidates = listOf(set.completedAt, session.date, session.finishedAt ?: 0L, session.startedAt)
        return candidates.firstOrNull { it > 0L } ?: 0L
    }

    /**
     * How much one working set is worth on the figure.
     *
     * Junction weight already says *which* muscles. This says *how hard* that
     * set was: skipped RPE is a completed set (1.0); a grind at nine is more
     * than a six; a single is less muscle fill than a set of eight.
     */
    internal fun setStimulus(set: SetLog): Double {
        if (set.reps <= 0) return 0.0
        return rpeFactor(set.rpe) * repsFactor(set.reps)
    }

    private fun rpeFactor(rpe: Int?): Double = when (rpe) {
        null -> 1.0
        else -> when (rpe.coerceIn(1, 10)) {
            in 1..5 -> 0.55
            6 -> 0.70
            7 -> 0.85
            8 -> 1.00
            9 -> 1.10
            else -> 1.15
        }
    }

    private fun repsFactor(reps: Int): Double = when (reps) {
        in 1..4 -> 0.85
        in 5..12 -> 1.00
        in 13..20 -> 0.95
        else -> 0.80
    }

    private class MuscleAccumulator {
        var windowVolumeKg: Double = 0.0
        var windowBodyweightReps: Int = 0
        /** Sets credited by junction weight: a bench press is 1.0 chest and 0.5 triceps. */
        var windowWeightedSets: Double = 0.0
        var windowSets: Int = 0
        val windowSessions: MutableSet<String> = linkedSetOf()
        var lastTrainedAtMs: Long? = null
        val exercises: MutableMap<String, ExerciseLoadContribution> = linkedMapOf()

        fun recordLifetime(trainedAt: Long) {
            lastTrainedAtMs = max(lastTrainedAtMs ?: 0L, trainedAt).takeIf { it > 0L }
        }

        fun recordWindow(
            sessionId: String,
            volumeKg: Double,
            bodyweightReps: Int,
            weightedSets: Double,
            set: SetLog,
        ) {
            windowVolumeKg += volumeKg
            windowBodyweightReps += bodyweightReps
            windowWeightedSets += weightedSets
            windowSets += 1
            windowSessions += sessionId
            val existing = exercises[set.exerciseId]
            exercises[set.exerciseId] = ExerciseLoadContribution(
                exerciseId = set.exerciseId,
                exerciseName = set.exerciseName.ifBlank { existing?.exerciseName ?: "Exercise" },
                volumeKg = (existing?.volumeKg ?: 0.0) + volumeKg,
                bodyweightReps = (existing?.bodyweightReps ?: 0) + bodyweightReps,
                workingSets = (existing?.workingSets ?: 0) + 1,
            )
        }
    }
}
