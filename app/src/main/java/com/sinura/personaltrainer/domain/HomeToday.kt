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

    fun surface(agenda: List<AgendaItem>, leftoverBelongs: Boolean = false): Surface =
        if (agenda.isEmpty() && leftoverBelongs) Surface.WEEK_FALLBACK else Surface.AGENDA

    /**
     * The one Home Start tag. Prefers a still-planned workout (non-aux
     * strength or mixed) so Stretch cannot steal Volt while Friday is
     * undone. TalkBack still finds "today's planned session" on a day stack.
     */
    fun startTagOccurrenceId(agenda: List<AgendaItem>): String? {
        val planned = agenda.filter { it.occurrence.status == OccurrenceStatus.PLANNED }
        val workout = planned.firstOrNull { isPlannedWorkout(it) }
        if (workout != null) return workout.occurrence.id
        val strength = planned.firstOrNull {
            (it.rule?.modality ?: ScheduleModality.STRENGTH) == ScheduleModality.STRENGTH
        }
        return strength?.occurrence?.id ?: planned.firstOrNull()?.occurrence?.id
    }

    /**
     * Summary shown before Home starts [item]. Confirm, then start.
     * Clock · kind, then the session order — never a truncated 1 · 2 · 3
     * preview that hides the rest of the work.
     */
    fun startConfirm(
        item: AgendaItem,
        routines: List<Routine>,
        clockFormat: ClockFormat,
    ): StartSessionConfirm {
        val clock = ClockCopy.format(item.occurrence.hour, item.occurrence.minute, clockFormat)
        val pack = ScheduleKind.auxPackId(item.rule?.templateId)?.let { AuxiliaryPacks.byId(it) }
        val names = sessionLiftNames(item.rule?.routineId, routines)
        val routine = item.rule?.routineId?.let { id -> routines.firstOrNull { it.id == id } }
        val modality = item.rule?.modality ?: ScheduleModality.STRENGTH
        val lines = buildList {
            add("$clock · ${item.kindCaption}")
            pack?.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                add("")
                add(caption)
            }
            add("")
            when {
                names.isNotEmpty() -> {
                    names.forEachIndexed { index, name -> add("${index + 1} $name") }
                    if (pack?.caption == null) {
                        add("")
                        add(liftCountLine(names.size, routine?.let { estimatedSessionMinutes(it) }))
                    }
                }
                modality == ScheduleModality.STRENGTH -> add(SessionOrderCopy.EMPTY_PREVIEW)
                else -> add(SessionOrderCopy.READY)
            }
        }
        return StartSessionConfirm(
            heading = "Start ${item.title}?",
            body = lines.joinToString("\n").trim(),
            confirmLabel = CONFIRM,
        )
    }

    /**
     * The Start the sheet may offer when Body, History, or Plan open it.
     * Same preference as Home: a still-planned occurrence first (workout
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

    const val CONFIRM = "Start"

    private fun isPlannedWorkout(item: AgendaItem): Boolean {
        if (ScheduleKind.isAux(item.rule?.templateId)) return false
        val modality = item.rule?.modality ?: ScheduleModality.STRENGTH
        return modality == ScheduleModality.STRENGTH || modality == ScheduleModality.MIXED
    }

    private fun liftCountLine(count: Int, minutes: Int?): String {
        val lifts = if (count == 1) "1 lift" else "$count lifts"
        return if (minutes != null) "$lifts · about $minutes min" else lifts
    }
}

data class StartSessionConfirm(
    val heading: String,
    val body: String,
    val confirmLabel: String,
)

data class TodaySheetStart(
    val title: String,
    val preview: String? = null,
    val occurrenceId: String? = null,
    val leftover: SuggestedTrainingDay? = null,
)

/**
 * Rest is most of a strength session, so planned sets times planned rest
 * is close enough to be useful. [SET_WORK_SECONDS] covers the set itself
 * and the walk to the rack.
 */
internal fun estimatedSessionMinutes(routine: Routine): Int {
    val seconds = routine.exercises.sumOf { item ->
        item.targetSets.coerceAtLeast(1) * (item.restSeconds.coerceAtLeast(0) + SET_WORK_SECONDS)
    }
    return ((seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE).coerceAtLeast(1)
}

private const val SET_WORK_SECONDS = 40
private const val SECONDS_PER_MINUTE = 60
