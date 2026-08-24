package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime

/**
 * One finished session without its set graph (P8.1 / FND-010).
 *
 * Home, Plan, History list, and daily projections read this. Heat and
 * the coach still receive a *windowed* full graph, never every set ever
 * logged.
 */
data class SessionSummary(
    val id: String,
    val routineId: String?,
    val routineName: String?,
    val date: Long,
    val finishedAt: Long?,
    val durationMinutes: Int,
    val workingSets: Int,
    val volumeKg: Double,
    val localEpochDay: Long,
    val cardioSeconds: Long = 0L,
    val cardioDistanceMeters: Double? = null,
    val kind: HistoryKind = HistoryKind.WORKOUT,
)

fun WorkoutSession.toSummary(
    time: TimePort = JvmTime,
    zoneId: String = time.defaultZoneId(),
): SessionSummary = SessionSummary(
    id = id,
    routineId = routineId,
    routineName = routineName,
    date = date,
    finishedAt = finishedAt,
    durationMinutes = durationMinutes,
    workingSets = workingSetCount(),
    volumeKg = work().volumeKg,
    localEpochDay = performedEpochDay(time, zoneId),
)

fun ActivitySession.toSummary(): SessionSummary = SessionSummary(
    id = id,
    routineId = null,
    routineName = title.takeIf { it.isNotBlank() },
    date = performedStart.instantMillis,
    finishedAt = performedEnd?.instantMillis,
    durationMinutes = cardioMinutes().coerceAtLeast(0),
    workingSets = strengthSetCount(),
    volumeKg = strengthWork().volumeKg,
    localEpochDay = localEpochDay,
    cardioSeconds = cardioMinutes().toLong() * 60L,
    cardioDistanceMeters = cardioBlocks.sumOf { it.distanceMeters ?: 0.0 }
        .takeIf { it > 0.0 },
    kind = HistoryKind.ACTIVITY,
)

fun SessionSummary.toHistoryEntry(): HistoryEntry = HistoryEntry(
    id = id,
    kind = kind,
    title = routineName ?: if (cardioSeconds > 0L) "Cardio" else "Workout",
    sortMillis = finishedAt ?: date,
    localEpochDay = localEpochDay,
    workingSets = workingSets,
    cardioMinutes = (cardioSeconds / 60L).toInt(),
    work = SetWork(volumeKg = volumeKg, bodyweightReps = 0),
    durationMinutes = durationMinutes,
)

/** Lightweight stub so week derivation can match routine/focus without sets. */
fun SessionSummary.toSessionStub(): WorkoutSession = WorkoutSession(
    id = id,
    routineId = routineId,
    routineName = routineName,
    date = date,
    notes = "",
    durationMinutes = durationMinutes,
    startedAt = date,
    finishedAt = finishedAt,
    exercises = emptyList(),
    sets = emptyList(),
)
