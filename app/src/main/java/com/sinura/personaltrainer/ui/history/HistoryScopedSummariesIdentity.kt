package com.sinura.personaltrainer.ui.history

import com.sinura.personaltrainer.domain.SessionSummary

/**
 * Stable fingerprint of scoped summaries for [HorizonProgressKey].
 *
 * Session summaries omit per-set weight, reps, and RPE; those edits can change
 * [SessionSummary.volumeKg] or [SessionSummary.workingSets] and are covered here.
 * A change that leaves summary totals unchanged but alters set-level detail only
 * is a known gap until summaries carry that detail.
 */
internal fun scopedSummariesIdentity(summaries: List<SessionSummary>): Int =
    summaries.asSequence()
        .sortedBy { it.id }
        .fold(1) { acc, summary ->
            var hash = acc
            hash = 31 * hash + summary.id.hashCode()
            hash = 31 * hash + (summary.finishedAt?.hashCode() ?: 0)
            hash = 31 * hash + summary.volumeKg.hashCode()
            hash = 31 * hash + summary.workingSets
            hash = 31 * hash + summary.localEpochDay.hashCode()
            hash = 31 * hash + summary.kind.hashCode()
            hash = 31 * hash + summary.cardioSeconds.hashCode()
            hash = 31 * hash + (summary.cardioDistanceMeters?.hashCode() ?: 0)
            hash
        }
