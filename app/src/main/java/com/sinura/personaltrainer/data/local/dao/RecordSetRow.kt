package com.sinura.personaltrainer.data.local.dao

/**
 * One finished working set with its lift's identity, as the two record queries read it:
 * [WorkoutDao.finishedWorkingSetRecords] for sessions and
 * [ActivityDao.observeCompletedStrengthSetRecords] for activities.
 *
 * Column names must match the `AS` aliases in those queries; Room maps by name. The name and
 * load type are nullable because a session row joins the library (a deleted lift has no row)
 * and an activity block stores both as optional snapshots. [exerciseId] is nullable only for
 * the activity side, where a strength block's lift is an optional column.
 */
data class RecordSetRow(
    val setId: String,
    val sessionId: String,
    val exerciseId: String?,
    val exerciseName: String?,
    val loadType: String?,
    val weightKg: Double,
    val reps: Int,
    val completedAt: Long,
)
