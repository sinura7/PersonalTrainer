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

/** Newest finished session, including ones older than the heat window. */
fun List<SessionSummary>.latest(): SessionSummary? =
    maxByOrNull { it.finishedAt ?: it.date }

/**
 * The finished session of the same routine immediately before [of].
 *
 * Null when [of] has no routine, or when that routine has not been logged
 * before. A Pull between two Pushes is skipped: the question is how this
 * routine moved, not how the last visit to the gym compared.
 */
fun List<SessionSummary>.previousSameRoutine(of: SessionSummary): SessionSummary? {
    val routineId = of.routineId ?: return null
    return asSequence()
        .filter { it.routineId == routineId && it.id != of.id }
        .maxByOrNull { it.finishedAt ?: it.date }
}

/**
 * Signed work versus [previous], in the unit Home already prints.
 *
 * Volume when either session moved a bar; working sets when both are
 * unloaded. Null when the two sessions match, so the tile does not
 * invent a "+0 kg" that looks like progress.
 */
fun SessionSummary.signedWorkDelta(previous: SessionSummary, unit: WeightUnit): String? {
    if (volumeKg > 0.0 || previous.volumeKg > 0.0) {
        val delta = volumeKg - previous.volumeKg
        if (kotlin.math.abs(delta) < 0.05) return null
        val mag = WeightConverter.formatVolumeNumber(kotlin.math.abs(delta), unit)
        val sign = if (delta > 0.0) "+" else "−"
        return "$sign$mag ${unit.suffix}"
    }
    val setDelta = workingSets - previous.workingSets
    if (setDelta == 0) return null
    val sign = if (setDelta > 0) "+" else "−"
    return "$sign${kotlin.math.abs(setDelta)} sets"
}

fun SessionSummary.daysSince(todayEpoch: Long): Long =
    (todayEpoch - localEpochDay).coerceAtLeast(0L)

fun SessionSummary.hasLoggedWork(): Boolean =
    workingSets > 0 || volumeKg > 0.0 || cardioSeconds > 0L

/**
 * The Home last-session numeral. Summaries do not carry bodyweight-rep
 * totals, so a zero-volume day falls back to working sets or cardio
 * minutes instead of inventing kilograms.
 */
fun SessionSummary.homeWork(unit: WeightUnit): WorkColumn {
    if (volumeKg > 0.0) return SetCopy.workColumn(SetWork(volumeKg, 0), unit)
    if (workingSets > 0) return WorkColumn(workingSets.toString(), "sets")
    if (cardioSeconds > 0L) {
        return WorkColumn((cardioSeconds / 60L).coerceAtLeast(1L).toString(), "min")
    }
    return SetCopy.workColumn(SetWork.NONE, unit)
}

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
