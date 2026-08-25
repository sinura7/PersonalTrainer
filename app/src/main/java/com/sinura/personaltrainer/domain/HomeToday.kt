package com.sinura.personaltrainer.domain

/**
 * Which today-surface Home may show.
 *
 * Occurrences are law after P7. When today has any agenda row, that card
 * is the only Start. The slot-week card remains only for the empty-agenda
 * leftover — Suggest / Replay / derived start when nothing was generated.
 */
object HomeToday {
    enum class Surface { AGENDA, WEEK_FALLBACK }

    fun surface(agenda: List<AgendaItem>): Surface =
        if (agenda.isNotEmpty()) Surface.AGENDA else Surface.WEEK_FALLBACK

    /**
     * The one Home Start tag. Prefers planned strength so TalkBack
     * still finds "today's planned session" on a two-a-day.
     */
    fun startTagOccurrenceId(agenda: List<AgendaItem>): String? {
        val planned = agenda.filter { it.occurrence.status == OccurrenceStatus.PLANNED }
        return planned.firstOrNull {
            (it.rule?.modality ?: ScheduleModality.STRENGTH) == ScheduleModality.STRENGTH
        }?.occurrence?.id ?: planned.firstOrNull()?.occurrence?.id
    }
}
