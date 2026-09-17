package com.sinura.personaltrainer.domain

/** Values frozen at the commit tap, including the duration shown by a set timer. */
data class WorkoutSetValues(
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
    val durationSeconds: Int?,
) {
    companion object {
        fun from(set: SetLog) = WorkoutSetValues(
            weightKg = set.weightKg,
            reps = set.reps,
            rpe = set.rpe,
            isWarmup = set.isWarmup,
            durationSeconds = set.durationSeconds,
        )
    }
}

/**
 * One logical save. New sets have a caller-owned ID; edits retain the old ID and
 * original values. Retrying never reads the editable draft or a running clock.
 * The original values also stop a delayed edit from overwriting a newer correction.
 */
data class WorkoutSetSave(
    val sessionId: String,
    val exerciseId: String,
    val setId: String,
    val completedAt: Long,
    val values: WorkoutSetValues,
    val original: WorkoutSetValues? = null,
) {
    val editing: Boolean get() = original != null
}

/** Recovery reads first; an unknown result never triggers an automatic write. */
enum class WorkoutSetSaveResolution { SAVED, UNSAVED, CONFLICT }
