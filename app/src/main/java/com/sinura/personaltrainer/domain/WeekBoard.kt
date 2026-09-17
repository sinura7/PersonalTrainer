package com.sinura.personaltrainer.domain

/**
 * How full a civil day is, counted on planned blocks (ADR-017).
 *
 * Not "how many workouts". A cardio and a stretch on Saturday are two
 * blocks. Both done is ALL. One done is PARTIAL. None done is NONE.
 * Empty is rest. Volt is not a fill colour.
 */
enum class DayFill {
    EMPTY,
    NONE,
    PARTIAL,
    ALL,
    ;

    companion object {
        fun of(items: List<AgendaItem>): DayFill {
            if (items.isEmpty()) return EMPTY
            val resolved = items.count { it.occurrence.status.isResolved }
            return when {
                resolved == 0 -> NONE
                resolved == items.size -> ALL
                else -> PARTIAL
            }
        }
    }
}

val OccurrenceStatus.isResolved: Boolean
    get() = this == OccurrenceStatus.DONE || this == OccurrenceStatus.SKIPPED

/**
 * One cell on the shared Home / Plan week strip.
 *
 * Caption is a kind word or a count, never a weekday-named routine.
 * Saturday showing "Friday" was leftover slot-week `routineName`.
 */
data class WeekBoardCell(
    val epochDay: Long,
    val weekday: Weekday,
    val fill: DayFill,
    val caption: String,
    val plannedCount: Int,
    val resolvedCount: Int,
    val completedCount: Int = resolvedCount,
    val skippedCount: Int = 0,
    val missedCount: Int = 0,
    val recordedCount: Int = 0,
)

object WeekBoard {
    fun forWeek(
        weekStartEpochDay: Long,
        occurrences: List<ScheduleOccurrence>,
        rules: List<ScheduleRule>,
        routineNames: Map<String, String> = emptyMap(),
        recordedDays: Map<Long, Int> = emptyMap(),
    ): List<WeekBoardCell> = (0 until Weekday.DAYS_IN_WEEK).map { offset ->
        val epochDay = weekStartEpochDay + offset
        // MOVED rows are vacated slots: the work now lives on another day
        // (ADR-019), so this day neither owes it (fill) nor counts it
        // (planned/summary). Counting them showed "8 planned" for a 7-block
        // week after one move and left the vacated day red forever. The
        // agenda LIST keeps the row as an honest readout; only the math skips it.
        val items = DailyAgenda.forDay(epochDay, occurrences, rules, routineNames)
            .filterNot { it.occurrence.status == OccurrenceStatus.MOVED }
        val resolved = items.count { it.occurrence.status.isResolved }
        WeekBoardCell(
            epochDay = epochDay,
            weekday = Weekday.fromEpochDay(epochDay),
            fill = DayFill.of(items),
            caption = caption(items),
            plannedCount = items.size,
            resolvedCount = resolved,
            completedCount = items.count { it.occurrence.status == OccurrenceStatus.DONE },
            skippedCount = items.count { it.occurrence.status == OccurrenceStatus.SKIPPED },
            missedCount = items.count { it.occurrence.status == OccurrenceStatus.MISSED },
            recordedCount = recordedDays[epochDay] ?: 0,
        )
    }

    fun caption(items: List<AgendaItem>): String = when (items.size) {
        0 -> REST
        1 -> items.single().kindCaption
        else -> items.size.toString()
    }

    fun summary(cells: List<WeekBoardCell>): String {
        val planned = cells.sumOf { it.plannedCount }
        val done = cells.sumOf { it.completedCount }
        val skipped = cells.sumOf { it.skippedCount }
        val plannedLabel = if (planned == 1) "1 planned" else "$planned planned"
        val doneLabel = if (done == 1) "1 done this week" else "$done done this week"
        return "$plannedLabel · $doneLabel" + if (skipped > 0) " · $skipped skipped" else ""
    }

    /** Short visible labels; detailed activity names belong in the selected day's board. */
    fun statusLabel(cell: WeekBoardCell): String = when {
        cell.plannedCount > cell.resolvedCount &&
            (cell.completedCount > 0 || cell.recordedCount > 0) -> "More"
        cell.skippedCount > 0 && cell.completedCount + cell.recordedCount > 0 -> "Mixed"
        cell.missedCount > 0 -> "Missed"
        cell.plannedCount > cell.resolvedCount -> "Plan"
        cell.skippedCount > 0 -> "Skip"
        cell.completedCount > 0 || cell.recordedCount > 0 -> "Done"
        else -> "Rest"
    }

    fun spoken(cell: WeekBoardCell, today: Long, selected: Long, proposal: SuggestedTrainingDay? = null): String {
        val name = CustomWeekPolicy.routineName(cell.weekday)
        val fill = buildList {
            if (cell.plannedCount > 0) add("${cell.plannedCount} planned")
            if (cell.completedCount > 0) add("${cell.completedCount} completed")
            if (cell.skippedCount > 0) add("${cell.skippedCount} skipped")
            if (cell.missedCount > 0) add("${cell.missedCount} missed")
            if (cell.recordedCount > 0) add("${cell.recordedCount} recorded sessions")
            if (isEmpty()) {
                add(if (proposal != null && !proposal.isRest) "Suggested ${proposal.focusTitle}; not yet planned" else "rest")
            }
        }.joinToString(", ")
        val extra = buildList {
            if (cell.epochDay == selected) add("selected")
            if (cell.epochDay == today) add("today")
        }
        return buildString {
            append(name)
            append(" ")
            append(CivilDate.fromEpochDay(cell.epochDay).dayOfMonth)
            append(", ")
            append(fill)
            if (extra.isNotEmpty()) {
                append(", ")
                append(extra.joinToString(", "))
            }
        }
    }

    /**
     * The leftover slot-week card may speak for this civil day only when
     * the pin actually belongs here. A Friday-named routine parked on
     * Saturday by D2 shift does not belong.
     */
    fun leftoverBelongsOn(
        epochDay: Long,
        leftover: SuggestedTrainingDay?,
        occurrences: List<ScheduleOccurrence>,
        rules: List<ScheduleRule>,
    ): Boolean {
        val day = leftover?.takeUnless { it.isRest } ?: return false
        if (day.epochDay != epochDay) return false
        val cellWeekday = Weekday.fromEpochDay(epochDay)
        val named = weekdayNamed(day.routineName)
        if (named != null && named != cellWeekday) return false
        val rid = day.routineId ?: return true
        val matching = rules.filter { it.routineId == rid }
        if (matching.isEmpty()) return true
        if (matching.none { it.weekday == cellWeekday }) return false
        val ids = matching.map { it.id }.toSet()
        val occs = occurrences.filter { it.ruleId in ids }
        if (occs.isEmpty()) return true
        return occs.any { it.localEpochDay == epochDay }
    }

    fun weekStartEpochDay(todayEpochDay: Long, weekStart: Weekday): Long =
        CivilDate.fromEpochDay(todayEpochDay).previousOrSame(weekStart).epochDay

    private fun weekdayNamed(name: String?): Weekday? {
        if (name.isNullOrBlank()) return null
        return Weekday.entries.firstOrNull {
            CustomWeekPolicy.routineName(it).equals(name, ignoreCase = true)
        }
    }

    const val REST = "Rest"
}
