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
    ): List<ScheduleOccurrence> = occurrences.filter { item ->
        // Missed means the civil day is over. Tonight's 19:00 row is
        // still tonight at 19:01; Keep-dates is a morning event.
        item.status == OccurrenceStatus.PLANNED &&
            item.localEpochDay < todayEpochDay
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
        val due = overdue(occurrences, todayEpochDay)
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
                // Adapt re-derives canonical rows of enabled rules. A disabled
                // once-rule and a relocated id are not regenerable — dropping
                // every future PLANNED row used to delete those one-offs.
                val rulesById = rules.associateBy { it.id }
                val regenerable = marked.filter { item ->
                    item.status == OccurrenceStatus.PLANNED &&
                        item.localEpochDay >= todayEpochDay &&
                        isRegenerable(item, rulesById)
                }.toSet()
                val kept = marked.filterNot { it in regenerable }
                val regenerated = OccurrenceGenerator.generateWeek(
                    weekStart = weekStart,
                    rules = rules,
                    existing = kept,
                    time = time,
                    deviceZoneId = deviceZoneId,
                    nowMs = nowMs,
                    todayEpochDay = todayEpochDay,
                    nowMinutes = nowMinutesOfDay,
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
                id = OccurrenceGenerator.unusedOccurrenceId(
                    ruleId = item.ruleId,
                    epochDay = target,
                    takenIds = working.map { it.id },
                    fromEpochDay = item.localEpochDay,
                ),
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

    private fun isRegenerable(
        item: ScheduleOccurrence,
        rulesById: Map<String, ScheduleRule>,
    ): Boolean {
        val rule = rulesById[item.ruleId] ?: return false
        if (!rule.enabled) return false
        return item.id == OccurrenceGenerator.occurrenceId(item.ruleId, item.localEpochDay)
    }
}
