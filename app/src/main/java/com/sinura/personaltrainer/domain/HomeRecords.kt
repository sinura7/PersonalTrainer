package com.sinura.personaltrainer.domain

/** Completed records retain their captured civil date and identity, independent of today's plan. */
object HomeRecords {
    fun fallbackCompleted(day: SuggestedTrainingDay?, records: List<SessionSummary>): Boolean {
        if (day == null || day.isRest) return false
        val onDay = records.filter { it.localEpochDay == day.epochDay }
        if (day.slotId != null) return onDay.any { it.id == day.satisfiedBySessionId }
        return day.routineId != null && onDay.any { it.routineId == day.routineId }
    }

    fun unlinkedForDay(
        epochDay: Long,
        summaries: List<SessionSummary>,
        agenda: List<AgendaItem>,
    ): List<SessionSummary> {
        val linked = agenda.filter { it.occurrence.status == OccurrenceStatus.DONE }
            .mapNotNull { it.occurrence.completedActivityId }.toSet()
        return summaries.filter { it.localEpochDay == epochDay && it.id !in linked }
            .sortedWith(compareByDescending<SessionSummary> { it.date }.thenBy { it.id })
    }

    fun title(session: SessionSummary): String = session.routineName?.takeIf { it.isNotBlank() }
        ?: if (session.kind == HistoryKind.WORKOUT) "Workout" else "Activity"

    fun metrics(session: SessionSummary): String = buildList {
        if (session.kind == HistoryKind.WORKOUT || session.workingSets > 0) {
            add("${session.workingSets} working ${if (session.workingSets == 1) "set" else "sets"}")
        }
        val minutes = session.durationMinutes.coerceAtLeast(0)
        val duration = when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
        if (session.kind == HistoryKind.WORKOUT) add(duration)
        else if (minutes > 0) add("$duration cardio")
        if (isEmpty()) add("Activity recorded")
    }.joinToString(" · ")
}
