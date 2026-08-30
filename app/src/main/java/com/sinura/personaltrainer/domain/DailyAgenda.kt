package com.sinura.personaltrainer.domain

/**
 * Today's scheduled occurrences, ordered by local time (P7.5).
 *
 * A day may hold morning cardio, a main lift session, and later
 * accessory work as independent rows. Completion of one does not hide
 * or start the other. One live activity still blocks a second start.
 */
object DailyAgenda {
    fun forDay(
        epochDay: Long,
        occurrences: List<ScheduleOccurrence>,
        rules: List<ScheduleRule>,
        routineNames: Map<String, String> = emptyMap(),
    ): List<AgendaItem> {
        val byId = rules.associateBy { it.id }
        return occurrences
            .filter { it.localEpochDay == epochDay }
            .sortedWith(compareBy({ it.minutesOfDay }, { it.id }))
            .map { occurrence ->
                val rule = byId[occurrence.ruleId]
                AgendaItem(
                    occurrence = occurrence,
                    rule = rule,
                    routineName = rule?.routineId?.let { routineNames[it] },
                )
            }
    }

    fun startable(items: List<AgendaItem>): List<AgendaItem> =
        items.filter { it.occurrence.status == OccurrenceStatus.PLANNED }

    /**
     * Still undone from earlier days: planned or missed. Home lists these
     * on today so a leftover does not require paging back.
     *
     * Includes the previous week. On Monday, `today == weekStart` used to
     * return nothing, so last week's PLANNED rows sat in the database
     * with no Home surface.
     */
    fun stillOpen(
        todayEpochDay: Long,
        weekStartEpochDay: Long,
        occurrences: List<ScheduleOccurrence>,
        rules: List<ScheduleRule>,
        routineNames: Map<String, String> = emptyMap(),
    ): List<AgendaItem> {
        val from = weekStartEpochDay - Weekday.DAYS_IN_WEEK
        val byId = rules.associateBy { it.id }
        return occurrences
            .filter { occurrence ->
                occurrence.localEpochDay in from until todayEpochDay &&
                    MoveToToday.isLeftover(occurrence, todayEpochDay)
            }
            .sortedWith(compareBy({ it.localEpochDay }, { it.minutesOfDay }, { it.id }))
            .map { occurrence ->
                val rule = byId[occurrence.ruleId]
                AgendaItem(
                    occurrence = occurrence,
                    rule = rule,
                    routineName = rule?.routineId?.let { routineNames[it] },
                )
            }
    }

    /**
     * Days with two or more scheduled rows — the week-strip second mark.
     * MOVED rows are vacated slots (ADR-019): a day whose strength moved
     * away no longer has two-a-day.
     */
    fun twoADayEpochDays(occurrences: List<ScheduleOccurrence>): Set<Long> =
        occurrences
            .filterNot { it.status == OccurrenceStatus.MOVED }
            .groupingBy { it.localEpochDay }
            .eachCount()
            .filterValues { it >= 2 }
            .keys
}
