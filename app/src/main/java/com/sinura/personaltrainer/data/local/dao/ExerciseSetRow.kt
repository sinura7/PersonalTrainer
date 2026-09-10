package com.sinura.personaltrainer.data.local.dao

/**
 * One finished working set joined to its session's identity, as read by
 * [WorkoutDao.observeFinishedWorkingSets] and the activity sibling
 * [ActivityDao.observeFinishedWorkingSets].
 *
 * Column names here must match the `AS` aliases in those queries; Room maps by name.
 */
data class ExerciseSetRow(
    val setId: String,
    val sessionId: String,
    val sessionName: String?,
    val sessionDate: Long,
    val weightKg: Double,
    val reps: Int,
    val completedAt: Long,
)
