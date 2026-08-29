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
)

object WeekBoard {
    fun forWeek(
        weekStartEpochDay: Long,
        occurrences: List<ScheduleOccurrence>,
        rules: List<ScheduleRule>,
        routineNames: Map<String, String> = emptyMap(),
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
        )
    }

    fun caption(items: List<AgendaItem>): String = when (items.size) {
        0 -> REST
        1 -> items.single().kindCaption
        else -> items.size.toString()
    }

    fun summary(cells: List<WeekBoardCell>): String {
        val planned = cells.sumOf { it.plannedCount }
        val done = cells.sumOf { it.resolvedCount }
        val plannedLabel = if (planned == 1) "1 planned" else "$planned planned"
        val doneLabel = if (done == 1) "1 done this week" else "$done done this week"
        return "$plannedLabel · $doneLabel"
    }

    fun spoken(cell: WeekBoardCell, today: Long, selected: Long): String {
        val name = CustomWeekPolicy.routineName(cell.weekday)
        val fill = when (cell.fill) {
            DayFill.EMPTY -> "rest"
            DayFill.NONE -> "${cell.caption}, none done"
            DayFill.PARTIAL -> "${cell.caption}, some done"
            DayFill.ALL -> "${cell.caption}, complete"
        }
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
