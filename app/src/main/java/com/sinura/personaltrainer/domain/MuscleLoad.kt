package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class HeatWindow(
    val label: String,
    val shortLabel: String,
) {
    LAST_7_DAYS("Last 7 days", "7 days"),
    LAST_14_DAYS("Last 14 days", "14 days"),
    CURRENT_WEEK("This week", "Week"),
    ;

    fun startMs(nowMs: Long, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        return when (this) {
            LAST_7_DAYS -> now.minusDays(7).toInstant().toEpochMilli()
            LAST_14_DAYS -> now.minusDays(14).toInstant().toEpochMilli()
            CURRENT_WEEK -> now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }
    }
}

enum class HeatBand {
    NONE,
    LOW,
    MODERATE,
    HIGH,
    ;

    val legendLabel: String
        get() = when (this) {
            NONE -> "None"
            LOW -> "Low"
            MODERATE -> "Moderate"
            HIGH -> "High"
        }

    companion object {
        fun fromHeat(heat: Double): HeatBand = when {
            heat <= 0.0 -> NONE
            heat < 0.34 -> LOW
            heat < 0.67 -> MODERATE
            else -> HIGH
        }
    }
}

data class ExerciseLoadContribution(
    val exerciseId: String,
    val exerciseName: String,
    val volumeKg: Double,
    val workingSets: Int,
)

data class MuscleLoadSummary(
    val muscle: CanonicalMuscle,
    val volumeKg: Double,
    val workingSets: Int,
    val sessionCount: Int,
    val lastTrainedAtMs: Long?,
    val daysSinceLastTrained: Int?,
    val heat: Double,
    val exercises: List<ExerciseLoadContribution>,
) {
    val band: HeatBand get() = HeatBand.fromHeat(heat)
    val trainedInWindow: Boolean get() = workingSets > 0
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
                workingSets = 0,
                sessionCount = 0,
                lastTrainedAtMs = null,
                daysSinceLastTrained = null,
                heat = 0.0,
                exercises = emptyList(),
            )

    val mapLoads: List<MuscleLoadSummary>
        get() = CanonicalMuscle.bodyMapOrder.map { load(it) }
}
