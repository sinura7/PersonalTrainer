package com.sinura.personaltrainer.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One finished session, seen through the lens of a single exercise. */
data class ExerciseSessionSummary(
    val sessionId: String,
    val sessionName: String?,
    val performedAtMs: Long,
    /**
     * The set the session is judged on. Deliberately the same choice
     * [ProgressionBasis] makes, so the history a lifter reads and the suggestion the app gives
     * can never be anchored on different sets of the same workout.
     */
    val topSet: ExerciseSetRecord?,
    val workingSets: Int,
    val volumeKg: Double,
    /** Reps, for a lift measured in reps. Zero for a loaded one. See [SetWork]. */
    val bodyweightReps: Int = 0,
    /**
     * Null for anything measured in reps.
     *
     * An estimated one-rep max is a statement about a bar, arrived at by extrapolating from
     * weight and reps. A push-up has no bar; extrapolating from a rep count alone would produce
     * a kilogram figure out of thin air, which is the same invention this whole class removed.
     */
    val estimatedOneRepMaxKg: Double?,
    /** Every working set of this exercise in the session, in the order logged. */
    val sets: List<ExerciseSetRecord>,
)

/** Volume for one training week, for the tonnage trend. */
data class WeeklyTonnage(
    val weekStart: LocalDate,
    val volumeKg: Double,
    /** Reps, for a lift measured in reps. The trend plots whichever of the two is this lift's. */
    val bodyweightReps: Int = 0,
    val workingSets: Int,
)

data class ExerciseHistory(
    val exerciseId: String,
    /** Newest first, matching how history is read everywhere else in the app. */
    val sessions: List<ExerciseSessionSummary> = emptyList(),
    val records: Map<PersonalRecordKind, PersonalRecord> = emptyMap(),
    /** Oldest first, because a trend is read left to right. */
    val weeklyTonnage: List<WeeklyTonnage> = emptyList(),
    val lifetimeVolumeKg: Double = 0.0,
    val lifetimeWorkingSets: Int = 0,
) {
    val hasHistory: Boolean get() = sessions.isNotEmpty()
}

/**
 * One working set with just enough of its session attached to summarise it.
 *
 * The narrow shape exists so the detail screen can read a single indexed query for one
 * exercise instead of subscribing to the full-history deep graph — four screens already do
 * that, and the point of this one is a single lift.
 */
data class ExerciseSetEntry(
    val record: ExerciseSetRecord,
    val sessionName: String?,
    val sessionPerformedAtMs: Long,
)

/**
 * Everything the exercise detail screen shows, derived from finished sessions.
 *
 * Warm-ups are excluded throughout, for the same reason they are excluded from the heat map:
 * they are preparation, not work, and counting them would inflate both tonnage and records.
 */
object ExerciseHistoryBuilder {
    /**
     * @param loadClass resolved by the caller. Taken from the sessions themselves when they
     * hold the lift, so history logged against a since-edited exercise keeps its own units.
     */
    fun build(
        exerciseId: String,
        sessions: List<WorkoutSession>,
        loadClass: LoadClass = sessions.firstOrNull { session ->
            session.exercises.any { it.exercise.id == exerciseId }
        }?.loadClassOf(exerciseId) ?: LoadClass.LOADED,
        zone: ZoneId = ZoneId.systemDefault(),
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): ExerciseHistory = fromEntries(
        exerciseId = exerciseId,
        entries = sessions.filter { it.isFinished }.flatMap { session ->
            session.workingSetRecords(exerciseId).map { record ->
                ExerciseSetEntry(
                    record = record,
                    sessionName = session.routineName,
                    sessionPerformedAtMs = session.performedAtMs(),
                )
            }.toList()
        },
        loadClass = loadClass,
        zone = zone,
        weekStart = weekStart,
    )

    /**
     * @param loadClass how this lift is measured. Required rather than defaulted: every caller
     * has the exercise in hand, and a default here would quietly report a calisthenics history
     * in kilograms — the exact failure [SetWork] exists to end.
     */
    fun fromEntries(
        exerciseId: String,
        entries: List<ExerciseSetEntry>,
        loadClass: LoadClass,
        zone: ZoneId = ZoneId.systemDefault(),
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): ExerciseHistory {
        val summaries = entries
            .groupBy { it.record.sessionId }
            .map { (sessionId, group) -> summarise(sessionId, group, loadClass) }
            .sortedByDescending { it.performedAtMs }

        val allSets = summaries.flatMap { it.sets }
        val weekly = allSets
            .groupBy { record ->
                Instant.ofEpochMilli(record.completedAt)
                    .atZone(zone)
                    .toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(weekStart))
            }
            .map { (start, records) ->
                val work = SetWork.sum(
                    records.map { SetWork.of(it.weightKg, it.reps, loadClass) },
                )
                WeeklyTonnage(
                    weekStart = start,
                    volumeKg = work.volumeKg,
                    bodyweightReps = work.bodyweightReps,
                    workingSets = records.size,
                )
            }
            .sortedBy { it.weekStart }

        return ExerciseHistory(
            exerciseId = exerciseId,
            sessions = summaries,
            records = PersonalRecords.bests(allSets, loadClass),
            weeklyTonnage = weekly,
            lifetimeVolumeKg = weekly.sumOf { it.volumeKg },
            lifetimeWorkingSets = allSets.size,
        )
    }

    /**
     * Every working set of [exerciseId] logged before [beforeMs], oldest first.
     *
     * This is what a PR check compares against: "before" is by the moment the set was
     * completed, not by session, so re-opening an older session cannot make a set that was
     * already a record look like it broke itself.
     */
    fun priorSets(
        exerciseId: String,
        sessions: List<WorkoutSession>,
        beforeMs: Long,
    ): List<ExerciseSetRecord> = sessions
        .asSequence()
        .flatMap { session -> session.workingSetRecords(exerciseId) }
        .filter { it.completedAt < beforeMs }
        .sortedBy { it.completedAt }
        .toList()

    private fun summarise(
        sessionId: String,
        group: List<ExerciseSetEntry>,
        loadClass: LoadClass,
    ): ExerciseSessionSummary {
        val records = group.map { it.record }.sortedBy { it.completedAt }
        val work = SetWork.sum(records.map { SetWork.of(it.weightKg, it.reps, loadClass) })
        val top = ProgressionBasis.topWorkingSet(
            records.map { WorkingSetCandidate(it.weightKg, it.reps, it.completedAt) },
        )
        val topRecord = top?.let { chosen ->
            records.first {
                it.weightKg == chosen.weightKg &&
                    it.reps == chosen.reps &&
                    it.completedAt == chosen.completedAt
            }
        }
        return ExerciseSessionSummary(
            sessionId = sessionId,
            sessionName = group.first().sessionName,
            performedAtMs = group.first().sessionPerformedAtMs,
            topSet = topRecord,
            workingSets = records.size,
            volumeKg = work.volumeKg,
            bodyweightReps = work.bodyweightReps,
            estimatedOneRepMaxKg = if (loadClass.repsAreTheMeasure) {
                null
            } else {
                records.mapNotNull { PersonalRecords.estimatedOneRepMaxKg(it.weightKg, it.reps) }
                    .maxOrNull()
            },
            sets = records,
        )
    }

    /** date is the ordering key history uses everywhere; fall back for older rows. */
    private fun WorkoutSession.performedAtMs(): Long =
        listOf(date, finishedAt ?: 0L, startedAt).firstOrNull { it > 0L } ?: 0L

    private fun WorkoutSession.workingSetRecords(exerciseId: String): Sequence<ExerciseSetRecord> =
        sets.asSequence()
            .filter { it.exerciseId == exerciseId && !it.isWarmup }
            .map { set ->
                ExerciseSetRecord(
                    setId = set.id,
                    sessionId = set.sessionId,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    completedAt = set.completedAt,
                )
            }
}
