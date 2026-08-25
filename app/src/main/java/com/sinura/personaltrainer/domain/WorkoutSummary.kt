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
    /** Heaviest-worked lift first: the session's own headline, not the order it was logged in. */
    val highlights: List<SessionHighlight> = emptyList(),
    val notes: String = "",
) {
    val recordCount: Int get() = highlights.sumOf { it.records.size }
    val hasWork: Boolean get() = workingSets > 0
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
