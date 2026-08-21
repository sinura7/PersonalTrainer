package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

object MuscleLoadCalculator {
    const val BODYWEIGHT_EQUIVALENT_KG = 40.0
    const val SECONDARY_VOLUME_WEIGHT = 0.4

    /** Where each band starts, in weighted sets per week. Mirrored on [HeatBand]. */
    const val LOW_MIN_SETS = HeatBand.LOW_MIN_SETS
    const val PRODUCTIVE_MIN_SETS = HeatBand.PRODUCTIVE_MIN_SETS
    const val HIGH_MIN_SETS = HeatBand.HIGH_MIN_SETS

    /** Past this, more sets do not make the silhouette any hotter. */
    const val HIGH_SATURATION_SETS = 30.0

    /**
     * Where each band sits on the 0..1 ramp `heatColor` paints.
     *
     * [FRACTION_LOW] is 0.05 rather than 0.02 on purpose: 0.02 is exactly `heatColor`'s
     * empty/not-empty cutoff, so a muscle that has just reached the LOW floor would render at
     * the boundary and read as untrained. [FRACTION_PRODUCTIVE] is where Heat3 sits exactly on
     * that ramp ((2/3) × 0.98 + 0.02), so the productive band is a flat, recognisable colour
     * rather than a gradient you have to compare against a legend.
     */
    const val FRACTION_LOW = 0.05
    const val FRACTION_PRODUCTIVE = 0.6733
    const val FRACTION_HIGH = 1.0

    /** How far back the coach looks, regardless of which window the map is showing. */
    const val COACH_TRAILING_DAYS = 14L

    fun setVolumeKg(weightKg: Double, reps: Int): Double {
        val load = if (weightKg > 0.0) weightKg else BODYWEIGHT_EQUIVALENT_KG
        return load * reps.coerceAtLeast(0)
    }

    fun snapshot(
        sessions: List<WorkoutSession>,
        window: HeatWindow,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        exerciseCatalog: Map<String, Exercise> = emptyMap(),
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): BodyHeatSnapshot {
        val windowStart = window.startMs(nowMs, zone, weekStart)
        val finished = sessions.filter { it.isFinished }
        val acc = CanonicalMuscle.entries.associateWith { MuscleAccumulator() }.toMutableMap()
        var anyWorkingSets = false
        var windowWorkingSets = false

        finished.forEach { session ->
            session.sets.filterNot { it.isWarmup }.forEach { set ->
                anyWorkingSets = true
                val trainedAt = trainedAtMs(session, set)
                val credits = creditsFor(set, session, exerciseCatalog)
                val volume = setVolumeKg(set.weightKg, set.reps)
                val inWindow = trainedAt >= windowStart && trainedAt <= nowMs

                credits.forEach { (muscle, _) ->
                    acc.getValue(muscle).recordLifetime(trainedAt)
                }

                if (inWindow) {
                    windowWorkingSets = true
                    credits.forEach { (muscle, weight) ->
                        acc.getValue(muscle).recordWindow(
                            sessionId = session.id,
                            volumeKg = volume * weight,
                            weightedSets = weight,
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
                workingSets = row.windowSets,
                sessionCount = row.windowSessions.size,
                lastTrainedAtMs = row.lastTrainedAtMs,
                daysSinceLastTrained = daysSince(row.lastTrainedAtMs, nowMs, zone),
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
        )
    }

    /**
     * How hot a muscle looks, from how much work it is actually getting.
     *
     * This replaces a relative normalisation — volume over the window's maximum — that made
     * the map answer the wrong question. Under the old rule the hottest muscle was always
     * fully hot, whatever you had done, and every other muscle's colour moved when it changed.
     * A week with one hard session and nothing else looked like a week of excellent balance
     * with one standout; a week of even, adequate training looked flat.
     *
     * Now the number means something on its own: below the LOW floor the muscle renders as
     * untrained, the productive band is a flat recognisable tone, and past it the ramp climbs
     * to saturation. Two different weeks that got a muscle the same work look the same.
     */
    fun heatFraction(weeklySets: Double): Double = when {
        weeklySets < LOW_MIN_SETS -> 0.0
        weeklySets < PRODUCTIVE_MIN_SETS -> lerp(
            from = FRACTION_LOW,
            to = FRACTION_PRODUCTIVE,
            t = (weeklySets - LOW_MIN_SETS) / (PRODUCTIVE_MIN_SETS - LOW_MIN_SETS),
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
     * A window's weighted-set total, expressed per week.
     *
     * The 30-day window is averaged rather than shown raw so the two chips are the same unit:
     * "18 weighted sets" has to mean the same thing on both, or the bands mean nothing on one
     * of them. `CURRENT_WEEK` is deliberately NOT scaled up — early in the week it reads low,
     * which is the honest answer to "this week so far".
     */
    fun weeklySetsFor(windowWeightedSets: Double, window: HeatWindow): Double = when (window) {
        HeatWindow.CURRENT_WEEK -> windowWeightedSets
        HeatWindow.LAST_30_DAYS -> windowWeightedSets * 7.0 / 30.0
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
    fun coachBasis(
        sessions: List<WorkoutSession>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        exerciseCatalog: Map<String, Exercise> = emptyMap(),
    ): CoachBasis {
        val basisStart = Instant.ofEpochMilli(nowMs).atZone(zone)
            .minusDays(COACH_TRAILING_DAYS).toInstant().toEpochMilli()
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
                        weighted[muscle] = (weighted[muscle] ?: 0.0) + weight
                    }
                }
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
                    daysSinceLastTrained = daysSince(lastTrained[muscle], nowMs, zone),
                )
            },
            hasAnyWorkingSets = anyWorkingSets,
            hasBasisWorkingSets = basisWorkingSets,
        )
    }

    fun daysSince(lastTrainedAtMs: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Int? {
        if (lastTrainedAtMs == null) return null
        val last = Instant.ofEpochMilli(lastTrainedAtMs).atZone(zone).toLocalDate()
        val now = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(last, now).toInt().coerceAtLeast(0)
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

    private class MuscleAccumulator {
        var windowVolumeKg: Double = 0.0
        /** Sets credited by junction weight: a bench press is 1.0 chest and 0.5 triceps. */
        var windowWeightedSets: Double = 0.0
        var windowSets: Int = 0
        val windowSessions: MutableSet<String> = linkedSetOf()
        var lastTrainedAtMs: Long? = null
        val exercises: MutableMap<String, ExerciseLoadContribution> = linkedMapOf()

        fun recordLifetime(trainedAt: Long) {
            lastTrainedAtMs = max(lastTrainedAtMs ?: 0L, trainedAt).takeIf { it > 0L }
        }

        fun recordWindow(sessionId: String, volumeKg: Double, weightedSets: Double, set: SetLog) {
            windowVolumeKg += volumeKg
            windowWeightedSets += weightedSets
            windowSets += 1
            windowSessions += sessionId
            val existing = exercises[set.exerciseId]
            exercises[set.exerciseId] = ExerciseLoadContribution(
                exerciseId = set.exerciseId,
                exerciseName = set.exerciseName.ifBlank { existing?.exerciseName ?: "Exercise" },
                volumeKg = (existing?.volumeKg ?: 0.0) + volumeKg,
                workingSets = (existing?.workingSets ?: 0) + 1,
            )
        }
    }
}
