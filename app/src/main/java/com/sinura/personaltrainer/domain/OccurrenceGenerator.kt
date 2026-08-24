package com.sinura.personaltrainer.domain

/**
 * Builds the dated occurrences for one week from recurrence rules.
 *
 * Does **not** shift missed days (ADR-012). Existing DONE / SKIPPED /
 * MISSED / MOVED rows are kept. A missing (rule, date) pair becomes a
 * new PLANNED occurrence. Week rollover is a fresh generate, not debt.
 */
object OccurrenceGenerator {

    fun occurrenceId(ruleId: String, epochDay: Long): String = "occ-$ruleId-$epochDay"

    fun generateWeek(
        weekStart: CivilDate,
        rules: List<ScheduleRule>,
        existing: List<ScheduleOccurrence>,
        time: TimePort,
        deviceZoneId: String,
        nowMs: Long,
    ): List<ScheduleOccurrence> {
        val weekEnd = weekStart.epochDay + 6
        val kept = existing.filter { it.localEpochDay in weekStart.epochDay..weekEnd }
        val existingKeys = kept.map { it.ruleId to it.localEpochDay }.toSet()
        val generated = mutableListOf<ScheduleOccurrence>()
        for (rule in rules.filter { it.enabled }) {
            val date = weekStart.plusDays(rule.weekday.ordinal.toLong())
            if ((rule.id to date.epochDay) in existingKeys) continue
            val zoneId = rule.resolveZoneId(deviceZoneId)
            val captured = time.resolveLocal(
                CivilDateTime(date, rule.hour, rule.minute),
                zoneId,
                overlap = DstOverlapChoice.EARLIER,
                gap = DstGapPolicy.SHIFT_FORWARD,
            )
            generated += ScheduleOccurrence(
                id = occurrenceId(rule.id, date.epochDay),
                ruleId = rule.id,
                status = OccurrenceStatus.PLANNED,
                captured = captured,
                hour = rule.hour,
                minute = rule.minute,
                completedActivityId = null,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            )
        }
        return (kept + generated).sortedWith(
            compareBy({ it.localEpochDay }, { it.minutesOfDay }, { it.id }),
        )
    }
}
