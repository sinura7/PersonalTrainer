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

    /**
     * Most reps ever, at whatever the lift was carrying.
     *
     * The record a bodyweight lift is actually chasing. The three above are statements about a
     * bar: "heaviest" and "estimated max" are meaningless for a push-up, and "most reps at that
     * weight" collapses into this one when the weight is always nothing. A calisthenics lifter
     * could previously break no records at all, session after session, however much better they
     * got at the thing they were training.
     */
    REPS,
    ;

    /**
     * How the record reads in a list.
     *
     * "Heaviest" and "estimated max" are different claims — only one of them was actually
     * lifted — so the kind is spelled out rather than left to be inferred from the number.
     */
    val label: String
        get() = when (this) {
            WEIGHT -> "Heaviest"
            REPS_AT_WEIGHT -> "Most reps"
            ESTIMATED_ONE_REP_MAX -> "Est. 1RM"
            REPS -> "Most reps"
        }
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

    /**
     * The standing bests, one per kind, or no entry when history holds nothing comparable.
     *
     * @param loadClass which records this lift can even have. A push-up has no heaviest set and
     * no estimated max; what it has is a rep count. Required rather than defaulted, because a
     * default would silently give every bodyweight lift the barbell's three records and none of
     * its own — which is what it used to do.
     */
    fun bests(
        history: List<ExerciseSetRecord>,
        loadClass: LoadClass,
    ): Map<PersonalRecordKind, PersonalRecord> {
        val result = LinkedHashMap<PersonalRecordKind, PersonalRecord>()

        if (loadClass.repsAreTheMeasure) {
            history.bestBy { it.reps.toDouble() }?.let { best ->
                result[PersonalRecordKind.REPS] = best.toRecord(PersonalRecordKind.REPS, best.reps.toDouble())
            }
            // A vest has a heaviest, and it is worth chasing: the same eight pull-ups with ten
            // more kilograms on is a better set, and nothing else here would notice.
            if (loadClass == LoadClass.BODYWEIGHT_ADDED) {
                history.filter { it.weightKg > 0.0 }.bestBy { it.weightKg }?.let { best ->
                    result[PersonalRecordKind.WEIGHT] =
                        best.toRecord(PersonalRecordKind.WEIGHT, best.weightKg)
                }
            }
            return result
        }

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
     * Standing bests a candidate is judged against.
     *
     * The five numbers [detect] actually reads. One aggregate query per lift
     * produces them directly; the list overload folds history into the same
     * shape so both paths cannot drift.
     */
    data class RecordPriors(
        val priorSetCount: Int = 0,
        val maxWeightKg: Double? = null,
        val maxReps: Int? = null,
        val maxRepsAtCandidateWeight: Int? = null,
        val maxEstimatedOneRepMaxKg: Double? = null,
        /**
         * How many prior sets used at least as much help as the candidate.
         *
         * Only read for [LoadClass.BODYWEIGHT_ASSISTED], where the weight column is machine
         * assistance. Zero means every earlier attempt at this lift was *harder* than this
         * one, so a higher rep count now says nothing about the lifter.
         */
        val priorSetsAtEqualOrMoreAssistance: Int = 0,
    ) {
        val isEmpty: Boolean get() = priorSetCount <= 0

        companion object {
            fun from(
                priorHistory: List<ExerciseSetRecord>,
                candidateWeightKg: Double,
            ): RecordPriors {
                if (priorHistory.isEmpty()) return RecordPriors()
                return RecordPriors(
                    priorSetCount = priorHistory.size,
                    maxWeightKg = priorHistory.maxOf { it.weightKg },
                    maxReps = priorHistory.maxOf { it.reps },
                    maxRepsAtCandidateWeight = priorHistory
                        .filter { it.weightKg == candidateWeightKg }
                        .maxOfOrNull { it.reps },
                    maxEstimatedOneRepMaxKg = priorHistory
                        .mapNotNull { estimatedOneRepMaxKg(it.weightKg, it.reps) }
                        .maxOrNull(),
                    priorSetsAtEqualOrMoreAssistance = priorHistory
                        .count { it.weightKg >= candidateWeightKg },
                )
            }
        }
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
        loadClass: LoadClass,
    ): Set<PersonalRecordKind> = detect(
        candidate,
        RecordPriors.from(priorHistory, candidate.weightKg),
        loadClass,
    )

    fun detect(
        candidate: ExerciseSetRecord,
        priors: RecordPriors,
        loadClass: LoadClass,
    ): Set<PersonalRecordKind> {
        if (priors.isEmpty || candidate.reps <= 0) return emptySet()
        val broken = linkedSetOf<PersonalRecordKind>()

        if (loadClass.repsAreTheMeasure) {
            val maxReps = priors.maxReps ?: return emptySet()
            // On an assisted lift the rep count is only half the answer: the weight column is
            // machine help, so nine reps with twenty kilograms of assistance is not a better
            // set than eight with ten, and awarding "most reps ever" for turning the
            // assistance UP is the app congratulating someone for getting weaker. A rep record
            // needs both halves — more reps than ever before, AND at no more help than some
            // earlier set already used. When every earlier attempt was harder than this one,
            // the extra rep was bought rather than earned.
            val assistanceEarnsIt = loadClass != LoadClass.BODYWEIGHT_ASSISTED ||
                priors.priorSetsAtEqualOrMoreAssistance > 0
            if (candidate.reps > maxReps && assistanceEarnsIt) broken += PersonalRecordKind.REPS
            // A vest has a heaviest, and it is worth chasing: the same eight pull-ups with ten
            // more kilograms on is a better set, and nothing else here would notice.
            if (loadClass == LoadClass.BODYWEIGHT_ADDED && candidate.weightKg > 0.0) {
                val heaviest = priors.maxWeightKg ?: 0.0
                if (candidate.weightKg > heaviest) {
                    broken += PersonalRecordKind.WEIGHT
                }
            }
            return broken
        }

        val heaviest = priors.maxWeightKg ?: return emptySet()
        if (candidate.weightKg > heaviest) broken += PersonalRecordKind.WEIGHT

        val bestRepsAtWeight = priors.maxRepsAtCandidateWeight
        if (bestRepsAtWeight != null && candidate.reps > bestRepsAtWeight) {
            broken += PersonalRecordKind.REPS_AT_WEIGHT
        }

        val estimate = estimatedOneRepMaxKg(candidate.weightKg, candidate.reps)
        val bestEstimate = priors.maxEstimatedOneRepMaxKg
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
