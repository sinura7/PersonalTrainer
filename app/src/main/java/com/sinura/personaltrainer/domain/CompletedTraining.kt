package com.sinura.personaltrainer.domain


/**
 * A finished piece of training, whichever store holds it.
 *
 * Read model only (completed-training-convergence.md). IDs are the existing
 * row ids; nothing is rewritten. Strength sessions keep [Kind.STRENGTH_SESSION];
 * backdated, cardio-only and mixed days are [Kind.ACTIVITY].
 */
data class CompletedTraining(
    val id: String,
    val kind: Kind,
    val title: String?,
    val performedAtMs: Long,
    val localEpochDay: Long,
    val finishedAtMs: Long?,
    val summary: SessionSummary,
    val strength: List<RecordSet>,
    val cardioSeconds: Long,
    val cardioDistanceMeters: Double?,
) {
    enum class Kind { STRENGTH_SESSION, ACTIVITY }
}

fun WorkoutSession.toCompletedTraining(
    time: TimePort,
    zoneId: String = time.defaultZoneId(),
): CompletedTraining? {
    if (!isFinished) return null
    val summary = toSummary(time, zoneId)
    return CompletedTraining(
        id = id,
        kind = CompletedTraining.Kind.STRENGTH_SESSION,
        title = routineName,
        performedAtMs = performedAtMs(),
        localEpochDay = summary.localEpochDay,
        finishedAtMs = finishedAt,
        summary = summary,
        strength = recordSets(),
        cardioSeconds = 0L,
        cardioDistanceMeters = null,
    )
}

fun ActivitySession.toCompletedTraining(): CompletedTraining? {
    if (!isCompleted) return null
    val summary = toSummary()
    return CompletedTraining(
        id = id,
        kind = CompletedTraining.Kind.ACTIVITY,
        title = title.takeIf { it.isNotBlank() },
        performedAtMs = performedStart.instantMillis,
        localEpochDay = localEpochDay,
        finishedAtMs = performedEnd?.instantMillis,
        summary = summary,
        strength = recordSets(),
        cardioSeconds = summary.cardioSeconds,
        cardioDistanceMeters = summary.cardioDistanceMeters,
    )
}

internal fun CompletedTraining.strengthWork(): SetWork =
    SetWork.sum(
        strength.map { row ->
            SetWork.of(row.set.weightKg, row.set.reps, row.loadClass)
        },
    )
