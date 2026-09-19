package com.sinura.personaltrainer.domain

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
