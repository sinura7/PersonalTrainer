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

    fun surface(
        agenda: List<AgendaItem>,
        leftoverBelongs: Boolean = false,
        stillOpen: List<AgendaItem> = emptyList(),
    ): Surface =
        if (agenda.isEmpty() && stillOpen.isEmpty() && leftoverBelongs) {
            Surface.WEEK_FALLBACK
        } else {
            Surface.AGENDA
        }

    /**
     * The one Home Start tag. Prefers a still-planned workout on [agenda]
     * (non-aux strength or mixed) so Stretch cannot steal Volt while a
     * workout is undone. If today has nothing planned, the tag may name
     * a leftover from [stillOpen].
     */
    fun startTagOccurrenceId(
        agenda: List<AgendaItem>,
        stillOpen: List<AgendaItem> = emptyList(),
    ): String? = pickStart(agenda) ?: pickStart(stillOpen)

    /**
     * Summary shown before Home starts [item]. Confirm, then start.
     * Clock · kind, then the session order — never a truncated 1 · 2 · 3
     * preview that hides the rest of the work.
     */
    fun startConfirm(
        item: AgendaItem,
        routines: List<Routine>,
        clockFormat: ClockFormat,
        todayEpochDay: Long,
    ): StartSessionConfirm {
        val clock = ClockCopy.format(item.occurrence.hour, item.occurrence.minute, clockFormat)
        val pack = ScheduleKind.auxPackId(item.rule?.templateId)?.let { AuxiliaryPacks.byId(it) }
        val names = sessionLiftNames(item.rule?.routineId, routines)
        val routine = item.rule?.routineId?.let { id -> routines.firstOrNull { it.id == id } }
        val modality = item.rule?.modality ?: ScheduleModality.STRENGTH
        val leftover = MoveToToday.isLeftover(item.occurrence, todayEpochDay)
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
            if (leftover) {
                add("")
                add(
                    MoveToToday.leftoverNote(
                        Weekday.fromEpochDay(item.occurrence.localEpochDay),
                    ),
                )
            }
        }
        return if (leftover) {
            StartSessionConfirm(
                heading = "Do ${item.title} today?",
                body = lines.joinToString("\n").trim(),
                confirmLabel = MoveToToday.DO_IT_TODAY,
                leftover = true,
            )
        } else {
            StartSessionConfirm(
                heading = "Start ${item.title}?",
                body = lines.joinToString("\n").trim(),
                confirmLabel = CONFIRM,
                leftover = false,
            )
        }
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

    private fun pickStart(items: List<AgendaItem>): String? {
        val open = items.filter {
            it.occurrence.status == OccurrenceStatus.PLANNED ||
                it.occurrence.status == OccurrenceStatus.MISSED
        }
        val workout = open.firstOrNull { isPlannedWorkout(it) }
        if (workout != null) return workout.occurrence.id
        val strength = open.firstOrNull {
            (it.rule?.modality ?: ScheduleModality.STRENGTH) == ScheduleModality.STRENGTH
        }
        return strength?.occurrence?.id ?: open.firstOrNull()?.occurrence?.id
    }

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
    val leftover: Boolean = false,
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
