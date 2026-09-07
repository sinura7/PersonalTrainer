package com.sinura.personaltrainer.domain

/**
 * One finished working set, flattened with what a records list needs to rank it: which lift it
 * belongs to and how that lift is measured.
 *
 * History's Records section was built from the 32-day insight window, so a stronger lift older
 * than a month vanished and a weaker recent set was presented as the record. A standing best is
 * a claim about the whole log, and the whole log is too large to load as session graphs on every
 * visit. This is the projection that is cheap enough to read in full: one row per working set,
 * no session, exercise or muscle graph behind it.
 */
data class RecordSet(
    val exerciseId: String,
    val exerciseName: String,
    val loadClass: LoadClass,
    val set: ExerciseSetRecord,
)

/**
 * The standing bests, one per lift, newest first, capped at [limit].
 *
 * Per lift the row is the record that lift can actually hold: most reps where reps are the
 * measure, else the estimated one-rep max, else the heaviest set. See [prSummary] for why that
 * order. The lift's class and name come from its most recent set: an activity block snapshots
 * both when it is logged, a session reads the library, and where the two disagree the newest
 * statement wins rather than whichever happened to sort first.
 *
 * Horizon-independent by definition. History's chips retotal the period; the readout's "PRs"
 * count is the period-scoped number. A lifetime best is the one thing that must not shrink when
 * the owner narrows the view.
 */
fun standingRecords(sets: List<RecordSet>, limit: Int = 3): List<PrSummaryRow> {
    if (sets.isEmpty()) return emptyList()
    return sets
        .groupBy { it.exerciseId }
        .mapNotNull { (exerciseId, lift) ->
            val newest = lift.maxWithOrNull(
                compareBy<RecordSet> { it.set.completedAt }.thenBy { it.set.setId },
            ) ?: return@mapNotNull null
            val records = PersonalRecords.bests(lift.map { it.set }, newest.loadClass)
            val best = records[PersonalRecordKind.REPS]
                ?: records[PersonalRecordKind.ESTIMATED_ONE_REP_MAX]
                ?: records[PersonalRecordKind.WEIGHT]
                ?: return@mapNotNull null
            PrSummaryRow(
                exerciseId = exerciseId,
                exerciseName = newest.exerciseName,
                kind = best.kind,
                valueKg = best.weightKg,
                reps = best.reps,
                achievedAt = best.achievedAt,
            )
        }
        .sortedWith(compareByDescending<PrSummaryRow> { it.achievedAt }.thenBy { it.exerciseName })
        .take(limit)
}

/**
 * The same projection out of a session graph, for callers that already hold one.
 *
 * Only finished sessions and working sets count: a live session has not happened yet as far as
 * records go, and a warm-up is not an attempt. The class is the session's own copy of the lift,
 * for the reason [WorkoutSession.loadClassOf] gives.
 */
fun WorkoutSession.recordSets(): List<RecordSet> {
    if (!isFinished) return emptyList()
    val classes = exercises.associate { it.exercise.id to LoadClass.of(it.exercise.loadType) }
    return sets
        .filterNot { it.isWarmup }
        .map { set ->
            RecordSet(
                exerciseId = set.exerciseId,
                exerciseName = set.exerciseName,
                loadClass = classes[set.exerciseId] ?: LoadClass.LOADED,
                set = ExerciseSetRecord(
                    setId = set.id,
                    sessionId = set.sessionId,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    completedAt = set.completedAt,
                ),
            )
        }
}
