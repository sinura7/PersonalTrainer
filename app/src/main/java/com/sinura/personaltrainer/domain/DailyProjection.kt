package com.sinura.personaltrainer.domain

/**
 * One local day's rolled-up work. Deterministic: the same summaries and
 * the same captured local dates always produce the same projection.
 */
data class DailyProjection(
    val localEpochDay: Long,
    val sessionCount: Int,
    val workingSets: Int,
    val volumeKg: Double,
    val activeMinutes: Int,
    val cardioSeconds: Long,
    val cardioDistanceMeters: Double,
    val sessionIds: List<String>,
)

object DailyProjectionBuilder {
    fun project(summaries: List<SessionSummary>): List<DailyProjection> =
        summaries
            .groupBy { it.localEpochDay }
            .toSortedMap()
            .map { (day, rows) ->
                DailyProjection(
                    localEpochDay = day,
                    sessionCount = rows.size,
                    workingSets = rows.sumOf { it.workingSets },
                    volumeKg = rows.sumOf { it.volumeKg },
                    activeMinutes = rows.sumOf { it.durationMinutes },
                    cardioSeconds = rows.sumOf { it.cardioSeconds },
                    cardioDistanceMeters = rows.sumOf { it.cardioDistanceMeters ?: 0.0 },
                    sessionIds = rows.map { it.id },
                )
            }
}
