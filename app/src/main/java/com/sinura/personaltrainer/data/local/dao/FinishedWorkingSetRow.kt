package com.sinura.personaltrainer.data.local.dao

/**
 * One finished working set of a requested lift, with the session's finish
 * time so Kotlin can pick the last session (and the RPE window) without a
 * round-trip per lift.
 */
data class FinishedWorkingSetRow(
    val setId: String,
    val exerciseId: String,
    val sessionId: String,
    val sessionName: String?,
    val sessionDate: Long,
    val weightKg: Double,
    val reps: Int,
    val completedAt: Long,
    val rpe: Int?,
    val sessionFinishedAt: Long,
)
