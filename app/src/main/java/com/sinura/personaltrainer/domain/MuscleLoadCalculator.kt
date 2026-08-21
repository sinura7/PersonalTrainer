package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

object MuscleLoadCalculator {
    const val BODYWEIGHT_EQUIVALENT_KG = 40.0
    const val SECONDARY_VOLUME_WEIGHT = 0.4

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
                            set = set,
                        )
                    }
                }
            }
        }

        val maxVolume = acc.values.maxOfOrNull { it.windowVolumeKg } ?: 0.0
        val loads = CanonicalMuscle.entries.map { muscle ->
            val row = acc.getValue(muscle)
            MuscleLoadSummary(
                muscle = muscle,
                volumeKg = row.windowVolumeKg,
                workingSets = row.windowSets,
                sessionCount = row.windowSessions.size,
                lastTrainedAtMs = row.lastTrainedAtMs,
                daysSinceLastTrained = daysSince(row.lastTrainedAtMs, nowMs, zone),
                heat = normalizeHeat(row.windowVolumeKg, maxVolume),
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

    fun normalizeHeat(volumeKg: Double, maxVolumeKg: Double): Double {
        if (volumeKg <= 0.0 || maxVolumeKg <= 0.0) return 0.0
        return (volumeKg / maxVolumeKg).coerceIn(0.0, 1.0)
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

    private fun trainedAtMs(session: WorkoutSession, set: SetLog): Long {
        val candidates = listOf(set.completedAt, session.date, session.finishedAt ?: 0L, session.startedAt)
        return candidates.firstOrNull { it > 0L } ?: 0L
    }

    private class MuscleAccumulator {
        var windowVolumeKg: Double = 0.0
        var windowSets: Int = 0
        val windowSessions: MutableSet<String> = linkedSetOf()
        var lastTrainedAtMs: Long? = null
        val exercises: MutableMap<String, ExerciseLoadContribution> = linkedMapOf()

        fun recordLifetime(trainedAt: Long) {
            lastTrainedAtMs = max(lastTrainedAtMs ?: 0L, trainedAt).takeIf { it > 0L }
        }

        fun recordWindow(sessionId: String, volumeKg: Double, set: SetLog) {
            windowVolumeKg += volumeKg
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
