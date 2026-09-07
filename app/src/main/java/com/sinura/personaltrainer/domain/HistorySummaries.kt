package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime

/**
 * A month of training, as History shows it.
 *
 * The log was a flat list of every session ever, newest first, with nothing between June and
 * May but another row. Once there are more than a few dozen sessions that list has no
 * landmarks at all: you cannot tell where a month ended, and "how much did I train in March"
 * takes a scroll and a squint at date captions.
 */
data class SessionMonthGroup(
    val month: CivilYearMonth,
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
    time: TimePort = JvmTime,
    zoneId: String = time.defaultZoneId(),
): List<SessionMonthGroup> = sessions
    .groupBy { session ->
        CivilYearMonth.from(time.civilDate(session.date, zoneId))
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
fun prSummary(sessions: List<WorkoutSession>, limit: Int = 3): List<PrSummaryRow> =
    // REPS first for a bodyweight lift, because it is the only record that lift can hold.
    // The list used to fall through both barbell kinds and drop the row entirely, so a
    // calisthenics lifter's recent-PR list was permanently empty however hard they trained.
    // The class comes from a session that actually holds the lift, not from the library:
    // a lift edited or deleted since must not restate months of past records in new units.
    // Both rules live in [standingRecords]; this is the graph-shaped way in.
    standingRecords(sessions.flatMap { it.recordSets() }, limit)
