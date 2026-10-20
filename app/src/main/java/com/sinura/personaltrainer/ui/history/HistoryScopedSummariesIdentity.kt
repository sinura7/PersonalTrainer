package com.sinura.personaltrainer.ui.history

import com.sinura.personaltrainer.domain.SessionSummary

/**
 * Stable fingerprint of scoped summaries for [HorizonProgressKey].
 *
 * Hashed fields match what [SessionSummary] exposes for period totals and card copy.
 * [SessionSummary.stills] ids are included because exercise swap changes mover labels.
 * Per-set weight, reps, and RPE are not on summaries and remain a known gap.
 */
internal fun scopedSummariesIdentity(summaries: List<SessionSummary>): Int =
    summaries.asSequence()
        .sortedBy { it.id }
        .fold(1) { acc, summary ->
            var hash = acc
            hash = 31 * hash + summary.id.hashCode()
            hash = 31 * hash + (summary.routineId?.hashCode() ?: 0)
            hash = 31 * hash + (summary.routineName?.hashCode() ?: 0)
            hash = 31 * hash + summary.date.hashCode()
            hash = 31 * hash + (summary.finishedAt?.hashCode() ?: 0)
            hash = 31 * hash + summary.durationMinutes
            hash = 31 * hash + summary.volumeKg.hashCode()
            hash = 31 * hash + summary.workingSets
            hash = 31 * hash + summary.localEpochDay.hashCode()
            hash = 31 * hash + summary.kind.hashCode()
            hash = 31 * hash + summary.cardioSeconds.hashCode()
            hash = 31 * hash + (summary.cardioDistanceMeters?.hashCode() ?: 0)
            summary.stills.sortedBy { it.id }.forEach { still ->
                hash = 31 * hash + still.id.hashCode()
            }
            hash
        }
