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

    /** Days with two or more scheduled rows — the week-strip second mark. */
    fun twoADayEpochDays(occurrences: List<ScheduleOccurrence>): Set<Long> =
        occurrences.groupingBy { it.localEpochDay }.eachCount().filterValues { it >= 2 }.keys

    fun minutesOfDay(nowMs: Long, startOfDayMs: Long): Int =
        ((nowMs - startOfDayMs) / 60_000L).toInt().coerceIn(0, 24 * 60 - 1)
}
