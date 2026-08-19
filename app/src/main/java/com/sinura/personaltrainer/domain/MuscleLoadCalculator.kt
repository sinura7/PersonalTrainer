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
                val mapping = mappingFor(set, session, exerciseCatalog)
                val volume = setVolumeKg(set.weightKg, set.reps)
                val inWindow = trainedAt >= windowStart && trainedAt <= nowMs

                mapping.primary.let { muscle ->
                    acc.getValue(muscle).recordLifetime(trainedAt)
                }
                mapping.secondaries.forEach { muscle ->
                    acc.getValue(muscle).recordLifetime(trainedAt)
                }

                if (inWindow) {
                    windowWorkingSets = true
                    acc.getValue(mapping.primary).recordWindow(session.id, volume, set)
                    mapping.secondaries.forEach { muscle ->
                        acc.getValue(muscle).recordWindow(
                            sessionId = session.id,
                            volumeKg = volume * SECONDARY_VOLUME_WEIGHT,
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

    private fun mappingFor(
        set: SetLog,
        session: WorkoutSession,
        catalog: Map<String, Exercise>,
    ): MuscleMapping {
        val raw = session.exercises.firstOrNull { it.exercise.id == set.exerciseId }?.exercise?.muscleGroup
            ?: catalog[set.exerciseId]?.muscleGroup
            ?: ""
        return MuscleNormalizer.normalize(raw)
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
