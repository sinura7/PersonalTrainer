package com.sinura.personaltrainer.data.local.entity

/**
 * Standing bests for one lift before a candidate set, aggregated in SQL.
 *
 * Personal-record detection only needs these five numbers, not the lift's
 * lifetime set graph.
 */
data class ExerciseRecordPriorsRow(
    val priorSetCount: Int,
    val maxWeightKg: Double?,
    val maxReps: Int?,
    val maxRepsAtWeight: Int?,
    val maxEstimatedOneRepMaxKg: Double?,
    /**
     * Best reps reached with at least as much assistance as the candidate used.
     *
     * Assisted lifts only; on every other class the weight column means something
     * else and this number is read by nothing.
     */
    val maxRepsAtEqualOrMoreAssistance: Int?,
)
