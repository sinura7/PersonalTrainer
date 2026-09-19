package com.sinura.personaltrainer.data.local.entity

/** SQL aggregate of a finished session. No set rows. */
data class SessionSummaryRow(
    val id: String,
    val routineId: String?,
    val routineName: String?,
    val date: Long,
    val finishedAt: Long?,
    val durationMinutes: Int,
    val workingSets: Int,
    val volumeKg: Double,
)

/** SQL aggregate of a completed activity. No set or interval rows. */
data class ActivitySummaryRow(
    val id: String,
    val title: String,
    val date: Long,
    val finishedAt: Long?,
    val localEpochDay: Long,
    val workingSets: Int,
    val volumeKg: Double,
    val cardioSeconds: Long,
    val cardioDistanceMeters: Double?,
)

/** One lift on a finished session, for History stills. Not a set row. */
data class SessionStillRow(
    val sessionId: String,
    val sortOrder: Int,
    val id: String,
    val name: String,
    val muscleGroup: String,
    val notes: String,
    val isCustom: Boolean,
    val equipment: String,
    val loadType: String,
    val movementKey: String?,
    val imageKey: String?,
)
