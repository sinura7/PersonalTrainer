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
)
