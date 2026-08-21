package com.sinura.personaltrainer.domain

import java.time.ZoneId

/**
 * Volume climbing while strength does not.
 *
 * The one thing this detects is the shape of overreaching: three weeks of rising working
 * volume with nothing to show for it on the bar. Either half alone is normal — volume rises
 * when you add a session, and estimated one-rep maxes are flat most weeks — so both are
 * required, and both are measured over the same three-week span so they are describing the
 * same period rather than two adjacent ones.
 *
 * Deliberately conservative. It needs a real rise (15% between the oldest and newest week,
 * strictly monotonic), it needs a non-empty baseline (a comeback from zero is not
 * overreaching), and it needs at least one lift with a comparable estimated max in both
 * halves — otherwise "no lift improved" is a statement about missing data, not about training.
 */
data class DeloadFinding(
    /** Percent rise from the oldest week to the newest, rounded, for the copy. */
    val setRisePercent: Int,
    val topLifts: List<String>,
)

object DeloadSignal {
    const val DELOAD_RISE_RATIO = 1.15
    const val DELOAD_WEEKS = 3
    const val DELOAD_TOP_LIFTS = 3
    const val COMPARISON_DAYS = 14L

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    fun detect(
        history: List<WorkoutSession>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DeloadFinding? {
        val finished = history.filter { it.isFinished }
        if (finished.isEmpty()) return null

        // Three consecutive weeks ending now, counted in working sets. Bucketed by the same
        // trainedAt attribution the heat map uses, so a set cannot land in one week here and
        // another week there.
        //
        // Sets rather than tonnage, for the same reason the heat bands use them: tonnage is not
        // a unit every lift has. It used to price each bodyweight rep at a flat 40 kg, so a
        // week of extra push-ups showed up as hundreds of kilograms of "rising volume"; drop
        // that invention and tonnage instead reads zero for the same week. Sets are the honest
        // measure of how much work went in, and they are the same measure for every lift.
        val buckets = DoubleArray(DELOAD_WEEKS)
        finished.forEach { session ->
            session.sets.filterNot { it.isWarmup }.forEach { set ->
                val at = MuscleLoadCalculator.trainedAtMs(session, set)
                val weeksBack = ((nowMs - at) / WEEK_MS).toInt()
                if (at <= nowMs && weeksBack in 0 until DELOAD_WEEKS) {
                    buckets[weeksBack] += 1.0
                }
            }
        }
        val newest = buckets[0]
        val middle = buckets[1]
        val oldest = buckets[2]
        val rising = newest > middle && middle > oldest &&
            oldest > 0.0 && newest >= oldest * DELOAD_RISE_RATIO
        if (!rising) return null

        val comparisonStart = nowMs - COMPARISON_DAYS * 24 * 60 * 60 * 1000
        val previousStart = comparisonStart - COMPARISON_DAYS * 24 * 60 * 60 * 1000
        val recentSets = setsBetween(finished, previousStart, nowMs)

        val topLiftIds = recentSets
            .filter { it.at >= comparisonStart }
            .groupingBy { it.exerciseId }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(DELOAD_TOP_LIFTS)
            .map { it.key }
        if (topLiftIds.isEmpty()) return null

        var comparable = 0
        topLiftIds.forEach { exerciseId ->
            val recent = bestEstimate(recentSets, exerciseId, comparisonStart, nowMs)
            val previous = bestEstimate(recentSets, exerciseId, previousStart, comparisonStart)
            if (recent == null || previous == null) return@forEach
            comparable += 1
            // Any lift that actually improved means the volume is buying something.
            if (recent > previous) return null
        }
        if (comparable == 0) return null

        val names = topLiftIds.mapNotNull { id -> recentSets.firstOrNull { it.exerciseId == id }?.exerciseName }
        val rise = ((newest / oldest - 1.0) * 100.0).toInt()
        return DeloadFinding(setRisePercent = rise, topLifts = names)
    }

    private fun setsBetween(sessions: List<WorkoutSession>, fromMs: Long, toMs: Long): List<Scored> =
        sessions.flatMap { session ->
            session.sets.filterNot { it.isWarmup }.mapNotNull { set ->
                val at = MuscleLoadCalculator.trainedAtMs(session, set)
                if (at < fromMs || at >= toMs) return@mapNotNull null
                Scored(
                    exerciseId = set.exerciseId,
                    exerciseName = set.exerciseName,
                    at = at,
                    estimate = PersonalRecords.estimatedOneRepMaxKg(set.weightKg, set.reps),
                )
            }
        }

    private fun bestEstimate(
        sets: List<Scored>,
        exerciseId: String,
        fromMs: Long,
        toMs: Long,
    ): Double? = sets
        .filter { it.exerciseId == exerciseId && it.at >= fromMs && it.at < toMs }
        .mapNotNull { it.estimate }
        .maxOrNull()

    private data class Scored(
        val exerciseId: String,
        val exerciseName: String,
        val at: Long,
        val estimate: Double?,
    )
}
