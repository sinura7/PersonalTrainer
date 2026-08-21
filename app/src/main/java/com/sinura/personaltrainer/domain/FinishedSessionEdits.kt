package com.sinura.personaltrainer.domain

/**
 * Rules for editing a session after it is finished.
 *
 * The subtle part is time. [MuscleLoadCalculator] buckets a set by its `completedAt` before
 * anything else, and [PersonalRecords] tie-breaks records by the same field — so a set added
 * to last month's workout and stamped "now" would heat this week's body map and re-date a
 * record onto today. Every added set therefore lands inside the session's own lifetime, and
 * edits never rewrite an existing `completedAt` at all.
 */
object FinishedSessionEdits {

    /**
     * When a set added to a finished session should claim it happened.
     *
     * Just after the session's last set, clamped into `[startedAt, finishedAt]`. Total even
     * for degenerate input: a session whose `finishedAt` somehow precedes its `startedAt`
     * returns `startedAt` rather than throwing.
     */
    fun timestampForAddedSet(startedAt: Long, finishedAt: Long, lastCompletedAt: Long?): Long =
        ((lastCompletedAt ?: startedAt) + 1)
            .coerceAtLeast(startedAt)
            .coerceAtMost(maxOf(finishedAt, startedAt))
}
