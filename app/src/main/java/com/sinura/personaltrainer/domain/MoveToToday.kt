package com.sinura.personaltrainer.domain

/**
 * Relocate one leftover occurrence onto today (ADR-019).
 *
 * Recurrence does not change. This is not the week-level missed-work
 * prompt. The old row becomes MOVED; today gets a new PLANNED row.
 */
object MoveToToday {
    const val DO_IT_TODAY = "Do it today"
    const val STILL_OPEN = "Still open"
    const val STILL_OPEN_BODY = "Start moves it to today."
    const val ALREADY_HERE = "Today already has this session."
    const val NOT_MOVABLE = "That session cannot move."

    fun isLeftover(occurrence: ScheduleOccurrence, todayEpochDay: Long): Boolean =
        occurrence.localEpochDay < todayEpochDay &&
            (occurrence.status == OccurrenceStatus.PLANNED ||
                occurrence.status == OccurrenceStatus.MISSED)

    fun decide(
        current: ScheduleOccurrence,
        todayEpochDay: Long,
        existingOnToday: List<ScheduleOccurrence>,
        rule: ScheduleRule?,
        nowMs: Long,
        time: TimePort,
        deviceZoneId: String,
    ): Outcome {
        if (current.localEpochDay == todayEpochDay &&
            current.status == OccurrenceStatus.PLANNED
        ) {
            return Outcome.AlreadyThere(current)
        }
        val movable = current.status == OccurrenceStatus.PLANNED ||
            current.status == OccurrenceStatus.MISSED
        if (!movable) return Outcome.Blocked(NOT_MOVABLE)
        if (current.localEpochDay > todayEpochDay) return Outcome.Blocked(NOT_MOVABLE)
        val occupant = existingOnToday.firstOrNull { row ->
            row.ruleId == current.ruleId &&
                row.localEpochDay == todayEpochDay &&
                row.status != OccurrenceStatus.MOVED &&
                row.id != current.id
        }
        if (occupant != null) {
            return if (occupant.status == OccurrenceStatus.PLANNED) {
                Outcome.AlreadyThere(occupant)
            } else {
                Outcome.Blocked(ALREADY_HERE)
            }
        }
        if (current.localEpochDay == todayEpochDay) {
            return Outcome.AlreadyThere(current)
        }
        val zoneId = rule?.resolveZoneId(deviceZoneId) ?: current.captured.zoneId
        val captured = time.resolveLocal(
            CivilDateTime(CivilDate.fromEpochDay(todayEpochDay), current.hour, current.minute),
            zoneId,
            overlap = DstOverlapChoice.EARLIER,
            gap = DstGapPolicy.SHIFT_FORWARD,
        )
        val createdId = OccurrenceGenerator.unusedOccurrenceId(
            ruleId = current.ruleId,
            epochDay = todayEpochDay,
            takenIds = existingOnToday.map { it.id },
            fromEpochDay = current.localEpochDay,
        )
        return Outcome.Relocate(
            vacated = current.copy(status = OccurrenceStatus.MOVED, updatedAtMs = nowMs),
            created = ScheduleOccurrence(
                id = createdId,
                ruleId = current.ruleId,
                status = OccurrenceStatus.PLANNED,
                captured = captured,
                hour = current.hour,
                minute = current.minute,
                completedActivityId = null,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
            ),
        )
    }

    fun leftoverNote(fromDay: Weekday): String =
        "This was ${fromDay.titleLabel()}. Starting it today moves it here."

    sealed interface Outcome {
        data class Relocate(
            val vacated: ScheduleOccurrence,
            val created: ScheduleOccurrence,
        ) : Outcome

        data class AlreadyThere(val occurrence: ScheduleOccurrence) : Outcome

        data class Blocked(val message: String) : Outcome
    }
}
