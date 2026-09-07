package com.sinura.personaltrainer.domain

/** One lift's contribution to a finished session. */
data class SessionHighlight(
    val exerciseId: String,
    val exerciseName: String,
    /** How this lift is measured, so the panel can read it back in its own units. */
    val loadClass: LoadClass = LoadClass.LOADED,
    val topSet: ExerciseSetRecord?,
    val workingSets: Int,
    val volumeKg: Double,
    val bodyweightReps: Int = 0,
    val records: Set<PersonalRecordKind>,
) {
    val work: SetWork get() = SetWork(volumeKg = volumeKg, bodyweightReps = bodyweightReps)
}

data class WorkoutSummary(
    val sessionId: String = "",
    val title: String = "",
    val performedAtMs: Long = 0L,
    val durationMinutes: Int = 0,
    val workingSets: Int = 0,
    val volumeKg: Double = 0.0,
    /** Reps of lifts measured in reps — push-ups, pull-ups, dips. Zero for a barbell day. */
    val bodyweightReps: Int = 0,
    /** Heaviest-worked lift first: the session's own headline, not the order it was logged in. */
    val highlights: List<SessionHighlight> = emptyList(),
    val notes: String = "",
) {
    val recordCount: Int get() = highlights.sumOf { it.records.size }
    val hasWork: Boolean get() = workingSets > 0
    val work: SetWork get() = SetWork(volumeKg = volumeKg, bodyweightReps = bodyweightReps)

    /**
     * The one number the receipt leads with, in the measure the session was actually made of.
     *
     * Total kilograms was the only hero, which told a push-up-only session it had lifted
     * "0 kg" — the app reporting a workout as worthless because its measure is reps. Kilograms
     * lead when any were moved (a mixed day is still mostly a barbell day and its reps sit in
     * a tile beside it); a rep total leads a bodyweight day; a session with neither has its
     * working-set count, which is the one thing every finished set contributes to. None of
     * these ranks the session — they name what it was made of.
     */
    val headline: SummaryHeadline
        get() = when {
            volumeKg > 0.0 -> SummaryHeadline.Volume(volumeKg)
            bodyweightReps > 0 -> SummaryHeadline.BodyweightReps(bodyweightReps)
            else -> SummaryHeadline.WorkingSets(workingSets)
        }
}

/** What the receipt's hero numeral is. See [WorkoutSummary.headline]. */
sealed interface SummaryHeadline {
    data class Volume(val kg: Double) : SummaryHeadline

    data class BodyweightReps(val reps: Int) : SummaryHeadline

    data class WorkingSets(val count: Int) : SummaryHeadline
}

/**
 * The words on the summary route, by the evidence behind each.
 *
 * Every state here is named after what the read actually established. "Workout saved" is
 * said only when the finished row was read back from Room — never because the route was
 * reached, and never for a row that could not be found. A read that threw says the summary
 * is unavailable and, unless the row was already in hand, that this screen cannot tell
 * whether the save landed; History is the place that can.
 */
object SummaryCopy {
    const val COMPLETE = "Workout complete"
    const val DONE = "Done"
    const val OPEN_SESSION = "See full session"

    /** The finished row was read and holds only warm-ups. */
    const val SAVED_NO_WORK_TITLE = "Workout saved"
    const val SAVED_NO_WORK_BODY = "It is in your history. Nothing to summarise from this one."

    /** A row was read that is not finished and has no working sets. Not a History claim. */
    const val NO_WORK_TITLE = "Nothing to summarise"
    const val NO_WORK_BODY = "This session has no working sets."

    /** The finished row was read; computing the summary over its history threw. */
    const val SAVED_SUMMARY_UNAVAILABLE_TITLE = "Workout saved. Summary unavailable."
    const val SAVED_SUMMARY_UNAVAILABLE_BODY =
        "The session is in your history, but its records and totals could not be worked out. Retry, or open the session."

    /** The row itself could not be read. Nothing is known either way. */
    const val UNAVAILABLE_TITLE = "Summary unavailable"
    const val UNAVAILABLE_BODY =
        "This workout could not be read, so this screen cannot say whether it was saved. Retry, or check History."

    /** The read succeeded and found no row. */
    const val MISSING_TITLE = "Session not found"
    const val MISSING_BODY =
        "That workout is not on this phone. It may have been discarded, or replaced by a restore."

    const val TOTAL_VOLUME = "Total volume"
    const val BODYWEIGHT_REPS = "Bodyweight reps"
    const val WORKING_SETS = "Working sets"
    const val DURATION = "Duration"
}

/**
 * What a finished workout amounted to.
 *
 * Finishing used to pop silently back to Home: the app knew the session was a personal best
 * and said nothing. This is the one moment a training app has the lifter's full attention, and
 * it was spending it on a screen transition.
 */
object WorkoutSummaryBuilder {
    /**
     * @param priorByExercise every working set of each exercise logged **before** this session.
     * A missing entry means no prior history, which is not the same as an empty session — a
     * lift with no history sets no records.
     */
    fun build(
        session: WorkoutSession,
        priorByExercise: Map<String, List<ExerciseSetRecord>>,
    ): WorkoutSummary {
        val working = session.sets.filterNot { it.isWarmup }
        val highlights = working
            .groupBy { it.exerciseId }
            .map { (exerciseId, sets) ->
                val records = sets
                    .map { set ->
                        ExerciseSetRecord(
                            setId = set.id,
                            sessionId = set.sessionId,
                            weightKg = set.weightKg,
                            reps = set.reps,
                            completedAt = set.completedAt,
                        )
                    }
                    .sortedBy { it.completedAt }
                val loadClass = session.loadClassOf(exerciseId)
                val work = SetWork.sum(records.map { SetWork.of(it.weightKg, it.reps, loadClass) })
                SessionHighlight(
                    exerciseId = exerciseId,
                    exerciseName = sets.first().exerciseName,
                    loadClass = loadClass,
                    topSet = topSetOf(records, loadClass),
                    workingSets = records.size,
                    volumeKg = work.volumeKg,
                    bodyweightReps = work.bodyweightReps,
                    records = recordsBroken(records, priorByExercise[exerciseId].orEmpty(), loadClass),
                )
            }
            .sortedWith(compareByDescending<SessionHighlight> { it.volumeKg }.thenBy { it.exerciseName })

        return WorkoutSummary(
            sessionId = session.id,
            title = session.routineName?.takeIf { it.isNotBlank() } ?: "Workout",
            performedAtMs = listOf(session.date, session.finishedAt ?: 0L, session.startedAt)
                .firstOrNull { it > 0L } ?: 0L,
            durationMinutes = session.durationMinutes,
            workingSets = working.size,
            volumeKg = highlights.sumOf { it.volumeKg },
            bodyweightReps = highlights.sumOf { it.bodyweightReps },
            highlights = highlights,
            notes = session.notes,
        )
    }

    /**
     * Walks the session's sets in the order they were logged, growing the comparison history as
     * it goes.
     *
     * Every set is judged against everything before it, this session's own earlier sets
     * included. Judging them all against the pre-session history instead would call three
     * ascending sets three separate weight records.
     */
    private fun recordsBroken(
        sessionSets: List<ExerciseSetRecord>,
        prior: List<ExerciseSetRecord>,
        loadClass: LoadClass,
    ): Set<PersonalRecordKind> {
        val seen = prior.toMutableList()
        val broken = linkedSetOf<PersonalRecordKind>()
        sessionSets.forEach { candidate ->
            broken += PersonalRecords.detect(candidate, seen, loadClass)
            seen += candidate
        }
        return broken
    }

    private fun topSetOf(records: List<ExerciseSetRecord>, loadClass: LoadClass): ExerciseSetRecord? {
        val top = ProgressionBasis.topWorkingSet(
            records.map { WorkingSetCandidate(it.weightKg, it.reps, it.completedAt) },
            loadClass.weightMeaning,
        ) ?: return null
        return records.first {
            it.weightKg == top.weightKg && it.reps == top.reps && it.completedAt == top.completedAt
        }
    }
}
