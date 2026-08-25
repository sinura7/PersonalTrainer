package com.sinura.personaltrainer.domain

/**
 * Recurring schedule rules, dated occurrences, and the one missed-work
 * decision (ADR-007, ADR-012, P7.1–P7.3).
 *
 * Rules do not change when a day is missed. Occurrences are the dated
 * instances. Two occurrences on one local date may complete independently.
 */

enum class ScheduleModality {
    STRENGTH,
    CARDIO,
    MIXED,
}

enum class ZonePolicy {
    FOLLOW_DEVICE,
    FIXED,
}

enum class OccurrenceStatus {
    PLANNED,
    DONE,
    SKIPPED,
    MISSED,
    MOVED,
}

enum class MissedWorkChoice {
    MOVE_REMAINING,
    ADAPT_WEEK,
    KEEP_DATES,
    SKIP_MISSED,
}

enum class ReminderDeliveryStatus {
    PENDING,
    DELIVERED,
    STARTED,
    SNOOZED,
    MOVED,
    SKIPPED,
    CANCELLED,
    STALE,
}

data class ScheduleRule(
    val id: String,
    val weekday: Weekday,
    val hour: Int,
    val minute: Int,
    val modality: ScheduleModality,
    val zonePolicy: ZonePolicy = ZonePolicy.FOLLOW_DEVICE,
    val fixedZoneId: String? = null,
    val routineId: String? = null,
    val templateId: String? = null,
    val focusKind: SessionFocusKind? = null,
    val reminderOffsetMinutes: Int = 0,
    val enabled: Boolean = true,
    val createdAtMs: Long,
    val updatedAtMs: Long,
) {
    val minutesOfDay: Int get() = hour * 60 + minute

    fun resolveZoneId(deviceZoneId: String): String =
        if (zonePolicy == ZonePolicy.FIXED) {
            fixedZoneId?.takeIf { it.isNotBlank() } ?: deviceZoneId
        } else {
            deviceZoneId
        }
}

data class ScheduleOccurrence(
    val id: String,
    val ruleId: String,
    val status: OccurrenceStatus,
    val captured: CapturedCivilTime,
    val hour: Int,
    val minute: Int,
    val completedActivityId: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
) {
    val localEpochDay: Long get() = captured.localEpochDay
    val minutesOfDay: Int get() = hour * 60 + minute
}

data class MissedWorkDecision(
    val weekStartEpochDay: Long,
    val choice: MissedWorkChoice,
    val decidedAtMs: Long,
)

data class ReminderDelivery(
    val id: String,
    val occurrenceId: String,
    val scheduledAtMs: Long,
    val status: ReminderDeliveryStatus,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class ReminderPreferences(
    val optOut: Boolean = false,
    val quietStartHour: Int = DEFAULT_QUIET_START_HOUR,
    val quietEndHour: Int = DEFAULT_QUIET_END_HOUR,
) {
    fun sanitized(): ReminderPreferences = copy(
        quietStartHour = quietStartHour.coerceIn(0, 23),
        quietEndHour = quietEndHour.coerceIn(0, 23),
    )

    companion object {
        const val DEFAULT_QUIET_START_HOUR = 22
        const val DEFAULT_QUIET_END_HOUR = 7
        val DEFAULT = ReminderPreferences()
    }
}

data class AgendaItem(
    val occurrence: ScheduleOccurrence,
    val rule: ScheduleRule?,
    val routineName: String? = null,
) {
    val title: String
        get() = when (rule?.modality ?: ScheduleModality.STRENGTH) {
            ScheduleModality.CARDIO -> "Cardio"
            ScheduleModality.MIXED -> "Mixed"
            ScheduleModality.STRENGTH ->
                routineName?.takeIf { it.isNotBlank() }
                    ?: rule?.focusKind?.label
                    ?: "Strength"
        }

    val timeLabel: String =
        "${occurrence.hour.toString().padStart(2, '0')}:${occurrence.minute.toString().padStart(2, '0')}"
}
