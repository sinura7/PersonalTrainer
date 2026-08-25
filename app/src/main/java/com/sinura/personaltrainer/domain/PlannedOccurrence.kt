package com.sinura.personaltrainer.domain

/**
 * Which dated occurrence the gym-floor "follow today's plan" start should bind.
 *
 * Home and Plan can start a slot-derived [SuggestedTrainingDay] without going
 * through [DailyAgenda]. If that start forgets the occurrence, finish cannot
 * mark it DONE and missed-work still prompts. Matching is pure so the bind
 * can be tested without Room.
 *
 * Cardio rows are never the strength plan. A day with two strength
 * occurrences only matches when routine or focus picks one.
 */
object PlannedOccurrence {
    fun matching(
        day: SuggestedTrainingDay,
        items: List<AgendaItem>,
    ): AgendaItem? {
        if (day.isRest) return null
        val planned = items.filter { item ->
            item.occurrence.localEpochDay == day.epochDay &&
                item.occurrence.status == OccurrenceStatus.PLANNED &&
                (item.rule?.modality ?: ScheduleModality.STRENGTH) == ScheduleModality.STRENGTH
        }
        day.routineId?.let { routineId ->
            planned.firstOrNull { it.rule?.routineId == routineId }?.let { return it }
        }
        planned.firstOrNull { it.rule?.focusKind == day.focusKind }?.let { return it }
        return planned.singleOrNull()
    }
}
