package com.sinura.personaltrainer.data.local.entity

/**
 * Standing bests for one lift before a candidate set, aggregated in SQL.
 *
 * Personal-record detection only needs these four numbers, not the lift's
 * lifetime set graph.
 */
data class ExerciseRecordPriorsRow(
    val priorSetCount: Int,
    val maxWeightKg: Double?,
    val maxReps: Int?,
    val maxRepsAtWeight: Int?,
    val maxEstimatedOneRepMaxKg: Double?,
)
