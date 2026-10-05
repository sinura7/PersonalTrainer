package com.sinura.personaltrainer.domain

/**
 * Finished sessions on Home's day board.
 *
 * Planned rows and [ScheduleOccurrence.completedActivityId] are the link
 * between a DONE block and History. Anything logged on a day without that
 * link is off-plan and gets its own quiet Done row so the day is not empty.
 */
object HomeLogged {
    const val SECTION = "Done"

    fun byDay(summaries: List<SessionSummary>): Map<Long, List<SessionSummary>> =
        summaries.groupBy { it.localEpochDay }

    /**
     * Finished on [epochDay], not already pictured by a DONE planned row on
     * that day.
     */
    fun offPlanForDay(
        epochDay: Long,
        agenda: List<AgendaItem>,
        summaries: List<SessionSummary>,
    ): List<SessionSummary> {
        val linked = agenda
            .asSequence()
            .filter { it.occurrence.localEpochDay == epochDay }
            .filter { it.occurrence.status == OccurrenceStatus.DONE }
            .mapNotNull { it.occurrence.completedActivityId }
            .toSet()
        return summaries
            .asSequence()
            .filter { it.localEpochDay == epochDay && it.id !in linked }
            .sortedByDescending { it.finishedAt ?: it.date }
            .toList()
    }

    fun showEmptyCopy(
        agenda: List<AgendaItem>,
        stillOpen: List<AgendaItem>,
        offPlan: List<SessionSummary>,
    ): Boolean {
        if (stillOpen.isNotEmpty()) return false
        if (offPlan.isNotEmpty()) return false
        if (agenda.any { it.occurrence.status == OccurrenceStatus.DONE }) return false
        return agenda.isEmpty()
    }

    fun title(summary: SessionSummary): String =
        summary.routineName?.takeIf { it.isNotBlank() }
            ?: if (summary.kind == HistoryKind.ACTIVITY) {
                "Cardio"
            } else {
                "Workout"
            }

    /** One caption line: sets, work, minutes — same columns as History, compressed. */
    fun statsLine(summary: SessionSummary, unit: WeightUnit): String {
        val work = summary.homeWork(unit)
        val parts = buildList {
            if (summary.workingSets > 0) add("${summary.workingSets} sets")
            if (summary.hasLoggedWork()) add("${work.value} ${work.label}")
            if (summary.durationMinutes > 0) add("${summary.durationMinutes} min")
        }
        return parts.joinToString(" · ").ifBlank { "Logged" }
    }

    fun openSummary(
        summary: SessionSummary,
        onOpenSession: (String) -> Unit,
        onOpenActivity: (String) -> Unit,
    ) {
        if (summary.kind == HistoryKind.ACTIVITY) {
            onOpenActivity(summary.id)
        } else {
            onOpenSession(summary.id)
        }
    }

    fun openCompletedId(
        activityId: String,
        summariesById: Map<String, SessionSummary>,
        modality: ScheduleModality?,
        onOpenSession: (String) -> Unit,
        onOpenActivity: (String) -> Unit,
    ) {
        val summary = summariesById[activityId]
        when {
            summary != null -> openSummary(summary, onOpenSession, onOpenActivity)
            modality == ScheduleModality.CARDIO -> onOpenActivity(activityId)
            else -> onOpenSession(activityId)
        }
    }
}
