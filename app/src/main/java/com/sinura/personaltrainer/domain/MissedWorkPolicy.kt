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
                val filled = OccurrenceGenerator.generateWeek(
                    weekStart = weekStart,
                    rules = rules,
                    existing = marked,
                    time = time,
                    deviceZoneId = deviceZoneId,
                    nowMs = nowMs,
                )
                val createdIds = marked.map { it.id }.toSet()
                ApplyResult(
                    occurrences = filled,
                    created = filled.filter { it.id !in createdIds },
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
