package com.sinura.personaltrainer.domain

/** One working set of a single exercise, flattened out of its session. */
data class ExerciseSetRecord(
    val setId: String,
    val sessionId: String,
    val weightKg: Double,
    val reps: Int,
    val completedAt: Long,
)

enum class PersonalRecordKind {
    /** Heaviest working set ever for this lift, at any rep count. */
    WEIGHT,

    /** Most reps ever at exactly this weight. */
    REPS_AT_WEIGHT,

    /** Best estimated one-rep max. */
    ESTIMATED_ONE_REP_MAX,
}

data class PersonalRecord(
    val kind: PersonalRecordKind,
    val setId: String,
    val sessionId: String,
    val weightKg: Double,
    val reps: Int,
    /** Kilograms for [PersonalRecordKind.WEIGHT] and e1RM; a rep count for reps-at-weight. */
    val value: Double,
    val achievedAt: Long,
)

/**
 * Personal records, computed from history rather than stored.
 *
 * Nothing is written down: every finished set is already in Room, so a stored PR table would
 * be a second source of truth that could disagree with the sets it summarises — and would go
 * wrong the moment a set is edited or deleted. Recomputing is cheap at the scale of one
 * person's training history.
 */
object PersonalRecords {
    /**
     * Above this, the Epley estimate stops meaning anything. Returning null past it is more
     * honest than printing a number nobody should train off.
     */
    const val MAX_REPS_FOR_ESTIMATE = 12

    /**
     * Epley, with the single exact.
     *
     * Epley reads `w * (1 + reps/30)`, which for a true single returns 1.03 × the weight —
     * an estimate strictly worse than the measurement. A one-rep set *is* the one-rep max.
     *
     * Null for a bodyweight set: with no external load there is nothing to extrapolate from,
     * and treating 0 kg as a real load would put every bodyweight lift at an e1RM of zero.
     */
    fun estimatedOneRepMaxKg(weightKg: Double, reps: Int): Double? = when {
        weightKg <= 0.0 || reps <= 0 -> null
        reps == 1 -> weightKg
        reps > MAX_REPS_FOR_ESTIMATE -> null
        else -> weightKg * (1.0 + reps / 30.0)
    }

    /** The standing bests, one per kind, or no entry when history holds nothing comparable. */
    fun bests(history: List<ExerciseSetRecord>): Map<PersonalRecordKind, PersonalRecord> {
        val result = LinkedHashMap<PersonalRecordKind, PersonalRecord>()

        history.bestBy { it.weightKg }?.let { best ->
            result[PersonalRecordKind.WEIGHT] = best.toRecord(PersonalRecordKind.WEIGHT, best.weightKg)
        }
        history.bestBy { estimatedOneRepMaxKg(it.weightKg, it.reps) }?.let { best ->
            val estimate = estimatedOneRepMaxKg(best.weightKg, best.reps)
            if (estimate != null) {
                result[PersonalRecordKind.ESTIMATED_ONE_REP_MAX] =
                    best.toRecord(PersonalRecordKind.ESTIMATED_ONE_REP_MAX, estimate)
            }
        }
        // Reps-at-weight is per weight, so the headline is the best rep count at the heaviest
        // weight the lifter has actually worked at — the one they are trying to add reps to.
        val topWeight = result[PersonalRecordKind.WEIGHT]?.weightKg
        if (topWeight != null) {
            history.filter { it.weightKg == topWeight }
                .bestBy { it.reps.toDouble() }
                ?.let { best ->
                    result[PersonalRecordKind.REPS_AT_WEIGHT] =
                        best.toRecord(PersonalRecordKind.REPS_AT_WEIGHT, best.reps.toDouble())
                }
        }
        return result
    }

    /**
     * Which records [candidate] breaks, given everything logged before it.
     *
     * A record has to be *beaten*, not equalled: repeating last week's top set is not a PR, and
     * announcing it as one would make the badge meaningless within a fortnight. A lift with no
     * prior history sets no records either — there is nothing to beat on the first attempt.
     */
    fun detect(
        candidate: ExerciseSetRecord,
        priorHistory: List<ExerciseSetRecord>,
    ): Set<PersonalRecordKind> {
        if (priorHistory.isEmpty() || candidate.reps <= 0) return emptySet()
        val broken = linkedSetOf<PersonalRecordKind>()

        val heaviest = priorHistory.maxOf { it.weightKg }
        if (candidate.weightKg > heaviest) broken += PersonalRecordKind.WEIGHT

        val bestRepsAtWeight = priorHistory
            .filter { it.weightKg == candidate.weightKg }
            .maxOfOrNull { it.reps }
        if (bestRepsAtWeight != null && candidate.reps > bestRepsAtWeight) {
            broken += PersonalRecordKind.REPS_AT_WEIGHT
        }

        val estimate = estimatedOneRepMaxKg(candidate.weightKg, candidate.reps)
        val bestEstimate = priorHistory.mapNotNull { estimatedOneRepMaxKg(it.weightKg, it.reps) }.maxOrNull()
        if (estimate != null && bestEstimate != null && estimate > bestEstimate) {
            broken += PersonalRecordKind.ESTIMATED_ONE_REP_MAX
        }
        return broken
    }

    /**
     * Ties go to the earlier set: the record belongs to whoever got there first, so a repeat
     * of a standing best does not quietly re-date it to today.
     */
    private inline fun List<ExerciseSetRecord>.bestBy(
        crossinline metric: (ExerciseSetRecord) -> Double?,
    ): ExerciseSetRecord? = this
        .mapNotNull { record -> metric(record)?.let { record to it } }
        .minWithOrNull(
            compareByDescending<Pair<ExerciseSetRecord, Double>> { it.second }
                .thenBy { it.first.completedAt }
                .thenBy { it.first.setId },
        )
        ?.first

    private fun ExerciseSetRecord.toRecord(kind: PersonalRecordKind, value: Double) = PersonalRecord(
        kind = kind,
        setId = setId,
        sessionId = sessionId,
        weightKg = weightKg,
        reps = reps,
        value = value,
        achievedAt = completedAt,
    )
}
