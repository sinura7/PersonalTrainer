package com.sinura.personaltrainer.data.local.entity

import androidx.room.Embedded

/**
 * Flat finished-work contents, without a session graph or aggregate serialization.
 * Set and planned-lift rows are separate UNION branches so neither multiplies the other.
 * A finished session with no sets still contributes its session row.
 */
data class FinishedWorkContentRow(
    @Embedded(prefix = "session_") val session: WorkoutSessionEntity,
    @Embedded(prefix = "set_") val set: SetLogEntity?,
    @Embedded(prefix = "slot_") val slot: SessionExerciseEntity?,
    @Embedded(prefix = "exercise_") val exercise: ExerciseEntity?,
)
