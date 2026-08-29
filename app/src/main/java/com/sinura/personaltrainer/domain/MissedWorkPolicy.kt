package com.sinura.personaltrainer.domain

/**
 * One persisted missed-work decision (ADR-012 / FND-017).
 *
 * Recurrence rules never change here. Read paths must not call this.
 * The prompt is shown only when overdue PLANNED work exists and this
 * week has no stored decision.
 */
object MissedWorkPolicy {

    fun overdue(
        occurrences: List<ScheduleOccurrence>,
        todayEpochDay: Long,
        nowMinutesOfDay: Int,
    ): List<ScheduleOccurrence> = occurrences.filter { item ->
        item.status == OccurrenceStatus.PLANNED &&
            (
                item.localEpochDay < todayEpochDay ||
                    (item.localEpochDay == todayEpochDay && item.minutesOfDay < nowMinutesOfDay)
                )
    }

    fun promptNeeded(
        overdue: List<ScheduleOccurrence>,
        existingDecision: MissedWorkDecision?,
    ): Boolean = overdue.isNotEmpty() && existingDecision == null

    data class ApplyResult(
        val occurrences: List<ScheduleOccurrence>,
        val created: List<ScheduleOccurrence> = emptyList(),
        /** Rows Adapt dropped (a removed rule's future PLANNED days). */
        val removed: List<String> = emptyList(),
    )

    fun apply(
        choice: MissedWorkChoice,
        occurrences: List<ScheduleOccurrence>,
        weekStart: CivilDate,
        todayEpochDay: Long,
        nowMinutesOfDay: Int,
        nowMs: Long,
        time: TimePort,
        deviceZoneId: String,
        rules: List<ScheduleRule> = emptyList(),
    ): ApplyResult {
        val due = overdue(occurrences, todayEpochDay, nowMinutesOfDay)
        val dueIds = due.map { it.id }.toSet()
        return when (choice) {
            MissedWorkChoice.KEEP_DATES -> ApplyResult(
                occurrences.map { item ->
                    if (item.id in dueIds) item.copy(status = OccurrenceStatus.MISSED, updatedAtMs = nowMs) else item
                },
            )
            MissedWorkChoice.SKIP_MISSED -> ApplyResult(
                occurrences.map { item ->
                    if (item.id in dueIds) item.copy(status = OccurrenceStatus.SKIPPED, updatedAtMs = nowMs) else item
                },
            )
            MissedWorkChoice.MOVE_REMAINING -> moveRemaining(
                occurrences = occurrences,
                due = due,
                weekStart = weekStart,
                todayEpochDay = todayEpochDay,
                nowMs = nowMs,
                time = time,
                deviceZoneId = deviceZoneId,
                rules = rules,
            )
            MissedWorkChoice.ADAPT_WEEK -> {
                val marked = occurrences.map { item ->
                    if (item.id in dueIds) item.copy(status = OccurrenceStatus.MISSED, updatedAtMs = nowMs) else item
                }
                // Adapt re-derives the rest of the week from the rules as they
                // stand NOW: untouched future PLANNED rows are dropped and
                // regenerated, so an hour change or a removed rule takes effect
                // mid-week, while DONE / SKIPPED / MISSED / MOVED history stays.
                // Regenerating around the kept rows alone was a no-op — every
                // (rule, date) pair already existed — which made Adapt
                // behaviourally identical to Keep the dates.
                val remainingPlanned = marked.filter {
                    it.status == OccurrenceStatus.PLANNED && it.localEpochDay >= todayEpochDay
                }.toSet()
                val kept = marked.filterNot { it in remainingPlanned }
                val regenerated = OccurrenceGenerator.generateWeek(
                    weekStart = weekStart,
                    rules = rules,
                    existing = kept,
                    time = time,
                    deviceZoneId = deviceZoneId,
                    nowMs = nowMs,
                )
                val keptIds = kept.map { it.id }.toSet()
                // A day already past regenerates nothing: a rule added mid-week
                // must not mint an instantly-overdue row behind today.
                val next = regenerated.filter {
                    it.id in keptIds || it.localEpochDay >= todayEpochDay
                }
                val nextIds = next.map { it.id }.toSet()
                val beforeIds = occurrences.map { it.id }.toSet()
                ApplyResult(
                    occurrences = next,
                    created = next.filter { it.id !in beforeIds },
                    removed = marked.map { it.id }.filter { it !in nextIds },
                )
            }
        }
    }

    private fun moveRemaining(
        occurrences: List<ScheduleOccurrence>,
        due: List<ScheduleOccurrence>,
        weekStart: CivilDate,
        todayEpochDay: Long,
        nowMs: Long,
        time: TimePort,
        deviceZoneId: String,
        rules: List<ScheduleRule>,
    ): ApplyResult {
        val weekEnd = weekStart.epochDay + 6
        val working = occurrences.toMutableList()
        val created = mutableListOf<ScheduleOccurrence>()
        val rulesById = rules.associateBy { it.id }
        var cursor = maxOf(todayEpochDay, weekStart.epochDay)
        for (item in due.sortedWith(compareBy({ it.localEpochDay }, { it.minutesOfDay }))) {
            val occupied = working
                .filter { it.status != OccurrenceStatus.MOVED }
                .map { it.ruleId to it.localEpochDay }
                .toSet()
            val target = (cursor..weekEnd).firstOrNull { day ->
                (item.ruleId to day) !in occupied
            }
            val index = working.indexOfFirst { it.id == item.id }
            if (target == null) {
                if (index >= 0) {
                    working[index] = item.copy(status = OccurrenceStatus.MISSED, updatedAtMs = nowMs)
                }
                continue
            }
            if (index >= 0) {
                working[index] = item.copy(status = OccurrenceStatus.MOVED, updatedAtMs = nowMs)
            }
            val rule = rulesById[item.ruleId]
            val zoneId = rule?.resolveZoneId(deviceZoneId) ?: item.captured.zoneId
            val date = CivilDate.fromEpochDay(target)
            val captured = time.resolveLocal(
                CivilDateTime(date, item.hour, item.minute),
                zoneId,
                overlap = DstOverlapChoice.EARLIER,
                gap = DstGapPolicy.SHIFT_FORWARD,
            )
            val moved = ScheduleOccurrence(
                id = OccurrenceGenerator.occurrenceId(item.ruleId, target),
                ruleId = item.ruleId,
                status = OccurrenceStatus.PLANNED,
                captured = captured,
                hour = item.hour,
                minute = item.minute,
                completedActivityId = null,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            )
            working += moved
            created += moved
            cursor = target
        }
        return ApplyResult(occurrences = working, created = created)
    }
}
