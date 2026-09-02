package com.sinura.personaltrainer.data.local.entity

/**
 * A cheap fingerprint of finished work.
 *
 * Room invalidates any query that *mentions* `set_logs` when a set is
 * logged, including an in-progress session whose rows never appear in
 * finished summaries. Collectors key off this fingerprint and skip the
 * expensive GROUP BY / session-graph reads until finished work actually
 * changes.
 */
data class FinishedWorkGeneration(
    val finishedSessionCount: Int,
    val durationSum: Int,
    val lastFinishedAt: Long?,
    val finishedWorkingSetCount: Int,
    val lastFinishedSetAt: Long?,
    /**
     * Sum of `weightKg * reps` over every finished working set.
     *
     * Editing a set changes neither the count nor a timestamp — `updateSet`
     * leaves `completedAt` and `setNumber` alone on purpose — so without a
     * value in the fingerprint, a corrected weight never reached Home, History
     * or the body map. This moves whenever a weight or a rep count does.
     */
    val finishedWorkingVolumeKg: Double,
    /**
     * Sum of reps over every finished working set.
     *
     * Not redundant with [finishedWorkingVolumeKg]. A bodyweight set logs zero
     * kilograms, so correcting ten push-ups to twelve moves nothing in the
     * volume sum and everything on the screens that count reps; and six reps at
     * 100 kg and five at 120 are the same 600 either way.
     */
    val finishedWorkingRepCount: Int,
)
