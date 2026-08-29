package com.sinura.personaltrainer.domain

/**
 * Comparable analytics windows (P8.3 / FND-009).
 *
 * These are not [HeatWindow] chips. Body heat stays This week / Last 30
 * days. Day / year / all-time are progress horizons, not a third
 * silhouette.
 */
enum class AnalyticsHorizon(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    ALL_TIME("All"),
}

data class HorizonTotals(
    val horizon: AnalyticsHorizon,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val sessionCount: Int,
    val trainedDays: Int,
    val workingSets: Int,
    val volumeKg: Double,
    val activeMinutes: Int,
    val cardioSeconds: Long,
    val cardioDistanceMeters: Double,
)

object HorizonMath {
    fun range(
        horizon: AnalyticsHorizon,
        today: CivilDate,
        weekStart: Weekday,
        earliestEpochDay: Long?,
    ): Pair<Long, Long> {
        val end = today.epochDay
        val start = when (horizon) {
            AnalyticsHorizon.DAY -> today.epochDay
            AnalyticsHorizon.WEEK -> today.previousOrSame(weekStart).epochDay
            AnalyticsHorizon.MONTH -> CivilDate(today.year, today.month, 1).epochDay
            AnalyticsHorizon.YEAR -> CivilDate(today.year, 1, 1).epochDay
            AnalyticsHorizon.ALL_TIME -> earliestEpochDay ?: end
        }
        return start to end
    }

    fun totals(
        horizon: AnalyticsHorizon,
        projections: List<DailyProjection>,
        today: CivilDate,
        weekStart: Weekday,
    ): HorizonTotals {
        val earliest = projections.minOfOrNull { it.localEpochDay }
        val (start, end) = range(horizon, today, weekStart, earliest)
        val slice = projections.filter { it.localEpochDay in start..end }
        return HorizonTotals(
            horizon = horizon,
            startEpochDay = start,
            endEpochDay = end,
            sessionCount = slice.sumOf { it.sessionCount },
            trainedDays = slice.count { it.sessionCount > 0 },
            workingSets = slice.sumOf { it.workingSets },
            volumeKg = slice.sumOf { it.volumeKg },
            activeMinutes = slice.sumOf { it.activeMinutes },
            cardioSeconds = slice.sumOf { it.cardioSeconds },
            cardioDistanceMeters = slice.sumOf { it.cardioDistanceMeters },
        )
    }
}
