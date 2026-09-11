package com.sinura.personaltrainer.domain

/**
 * One place to count and rank records over [RecordSet].
 *
 * Standing bests, the log-time badge and the horizon/block PR count all used
 * the same [PersonalRecords.detect] with four call sites. Priors for a live
 * set still come from the SQL aggregate; this only ranks rows already in
 * memory.
 */
object RecordsCalculator {
    fun standing(sets: List<RecordSet>, limit: Int = 3): List<PrSummaryRow> =
        standingRecords(sets, limit)

    fun detect(
        candidate: ExerciseSetRecord,
        priorHistory: List<ExerciseSetRecord>,
        loadClass: LoadClass,
    ): Set<PersonalRecordKind> = PersonalRecords.detect(
        candidate = candidate,
        priorHistory = priorHistory,
        loadClass = loadClass,
    )

    fun detect(
        candidate: ExerciseSetRecord,
        priors: PersonalRecords.RecordPriors,
        loadClass: LoadClass,
    ): Set<PersonalRecordKind> = PersonalRecords.detect(
        candidate = candidate,
        priors = priors,
        loadClass = loadClass,
    )

    /**
     * Records broken by [inRange], judged against [before] plus earlier sets
     * in the same range. A first set against an empty prior is a baseline,
     * not a broken record.
     */
    fun countBroken(inRange: List<RecordSet>, before: List<RecordSet>): Int {
        var total = 0
        val priorByExercise = before.groupBy { it.exerciseId }
        val byExercise = inRange.groupBy { it.exerciseId }
        byExercise.forEach { (exerciseId, pairs) ->
            val loadClass = pairs.first().loadClass
            val ordered = pairs.sortedBy { it.set.completedAt }
            val seen = priorByExercise[exerciseId]
                .orEmpty()
                .map { it.set }
                .sortedBy { it.completedAt }
                .toMutableList()
            ordered.forEach { attempt ->
                total += detect(
                    candidate = attempt.set,
                    priorHistory = seen,
                    loadClass = loadClass,
                ).size
                seen += attempt.set
            }
        }
        return total
    }

    fun countBroken(
        inBlock: List<CompletedTraining>,
        beforeBlock: List<CompletedTraining>,
    ): Int = countBroken(
        inRange = inBlock.flatMap { it.strength },
        before = beforeBlock.flatMap { it.strength },
    )
}
