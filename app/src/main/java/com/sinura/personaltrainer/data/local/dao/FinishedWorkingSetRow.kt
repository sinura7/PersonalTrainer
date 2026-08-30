package com.sinura.personaltrainer.data.local.dao

/**
 * One finished working set of a requested lift, with the session's finish
 * time so Kotlin can pick the last session (and the RPE window) without a
 * round-trip per lift.
 */
data class FinishedWorkingSetRow(
    val exerciseId: String,
    val sessionId: String,
    val weightKg: Double,
    val reps: Int,
    val completedAt: Long,
    val rpe: Int?,
    val sessionFinishedAt: Long,
)
