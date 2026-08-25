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

    /**
     * The Start the sheet may offer when Body, History, or Plan open it.
     * Same preference as Home: a still-planned occurrence first (strength
     * preferred), else the leftover slot day's routine.
     */
    fun sheetStart(
        agenda: List<AgendaItem>,
        leftoverDay: SuggestedTrainingDay?,
        routines: List<Routine>,
    ): TodaySheetStart? {
        val tag = startTagOccurrenceId(agenda)
        if (tag != null) {
            val item = agenda.first { it.occurrence.id == tag }
            val names = sessionLiftNames(item.rule?.routineId, routines)
            return TodaySheetStart(
                title = item.title,
                preview = names.takeIf { it.isNotEmpty() }?.let { SessionOrderCopy.numberedPreview(it) },
                occurrenceId = tag,
            )
        }
        val day = leftoverDay?.takeUnless { it.isRest } ?: return null
        val names = leftoverLiftNames(day, routines)
        return TodaySheetStart(
            title = day.routineName ?: day.focusTitle,
            preview = names.takeIf { it.isNotEmpty() }?.let { SessionOrderCopy.numberedPreview(it) },
            leftover = day,
        )
    }
}

data class TodaySheetStart(
    val title: String,
    val preview: String? = null,
    val occurrenceId: String? = null,
    val leftover: SuggestedTrainingDay? = null,
)
