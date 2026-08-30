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

    /**
     * Id for a row relocated onto [epochDay].
     *
     * The canonical id is already the vacated-slot row when that day was
     * itself moved away (ADR-019). Reusing it would upsert MOVED back to
     * PLANNED and erase the vacancy.
     */
    fun unusedOccurrenceId(
        ruleId: String,
        epochDay: Long,
        takenIds: Collection<String>,
        fromEpochDay: Long,
    ): String {
        val canonical = occurrenceId(ruleId, epochDay)
        if (canonical !in takenIds) return canonical
        val relocated = "$canonical-from-$fromEpochDay"
        if (relocated !in takenIds) return relocated
        var n = 2
        while ("$relocated-$n" in takenIds) n++
        return "$relocated-$n"
    }

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
            // The rule's weekday within THIS week. plusDays(ordinal) assumed the
            // week starts on Monday; a Sunday week start put every rule one day
            // early (a FRIDAY rule landed on Thursday) and, after a preference
            // change, minted duplicate rows on the corrected day.
            val date = weekStart.nextOrSame(rule.weekday)
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
