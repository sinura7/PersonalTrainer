package com.sinura.personaltrainer.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * A month of training, as History shows it.
 *
 * The log was a flat list of every session ever, newest first, with nothing between June and
 * May but another row. Once there are more than a few dozen sessions that list has no
 * landmarks at all: you cannot tell where a month ended, and "how much did I train in March"
 * takes a scroll and a squint at date captions.
 */
data class SessionMonthGroup(
    val month: YearMonth,
    val sessions: List<WorkoutSession>,
)

/**
 * Groups finished sessions by the month they happened in, newest month first.
 *
 * Presentation-side on purpose: the query is unchanged and this is a pure function of its
 * result, so it is testable without a database and cannot get out of step with what the list
 * actually renders. Within a month the repository's own order is preserved — it is already
 * newest-first, and re-sorting here would be a second opinion about ordering that could
 * silently disagree with the flat list it replaces.
 */
fun groupSessionsByMonth(
    sessions: List<WorkoutSession>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<SessionMonthGroup> = sessions
    .groupBy { session ->
        YearMonth.from(Instant.ofEpochMilli(session.date).atZone(zone).toLocalDate())
    }
    .entries
    .sortedByDescending { it.key }
    .map { (month, rows) -> SessionMonthGroup(month = month, sessions = rows) }

/**
 * One standing record, ready to render.
 *
 * History could tell you everything about what you had done and nothing about what you had
 * done *best*. The records existed — [PersonalRecords] has computed them since the exercise
 * detail screen needed them — but only per lift, one screen at a time, so there was no place
 * in the app that answered "what have I actually hit recently".
 */
data class PrSummaryRow(
    val exerciseId: String,
    val exerciseName: String,
    val kind: PersonalRecordKind,
    val valueKg: Double,
    val reps: Int,
    val achievedAt: Long,
)

/**
 * The most recent standing records, one per lift, newest first.
 *
 * Estimated one-rep max is preferred over raw weight because it is the record that survives a
 * change in rep range: a heavy triple and a lighter set of eight are comparable through it and
 * not otherwise, so "your best bench" does not silently mean "the heaviest single you ever
 * happened to do". Where no e1RM is computable — a set at reps beyond the estimate's usable
 * range — the weight record stands in rather than the lift dropping out entirely.
 *
 * One row per exercise, so a lifter who broke three records in one squat session does not push
 * everything else off the list.
 */
fun prSummary(sessions: List<WorkoutSession>, limit: Int = 3): List<PrSummaryRow> {
    val byExercise = sessions
        .filter { it.isFinished }
        .flatMap { session -> session.sets.filterNot { it.isWarmup } }
        .groupBy { it.exerciseId }

    return byExercise.mapNotNull { (exerciseId, sets) ->
        val records = PersonalRecords.bests(
            sets.map { set ->
                ExerciseSetRecord(
                    setId = set.id,
                    sessionId = set.sessionId,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    completedAt = set.completedAt,
                )
            },
        )
        val best = records[PersonalRecordKind.ESTIMATED_ONE_REP_MAX]
            ?: records[PersonalRecordKind.WEIGHT]
            ?: return@mapNotNull null
        PrSummaryRow(
            exerciseId = exerciseId,
            exerciseName = sets.first().exerciseName,
            kind = best.kind,
            valueKg = best.weightKg,
            reps = best.reps,
            achievedAt = best.achievedAt,
        )
    }
        .sortedWith(compareByDescending<PrSummaryRow> { it.achievedAt }.thenBy { it.exerciseName })
        .take(limit)
}
