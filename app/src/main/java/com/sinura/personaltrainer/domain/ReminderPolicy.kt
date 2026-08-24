package com.sinura.personaltrainer.domain

/**
 * Best-effort reminder delivery (ADR-012). Never an exact alarm.
 *
 * Database rows are authoritative. A worker rereads; stale work is a
 * no-op. Quiet hours defer. Opt-out skips. Catch-up after reboot is
 * refused — past-due PENDING rows become STALE.
 */
enum class ReminderDecision {
    DELIVER,
    DEFER_QUIET,
    STALE,
    SKIP,
    TOO_EARLY,
}

enum class ReminderRebuildAction {
    RESCHEDULE,
    MARK_STALE,
    IGNORE,
}

object ReminderPolicy {

    const val SNOOZE_MINUTES = 15

    fun decide(
        delivery: ReminderDelivery,
        occurrence: ScheduleOccurrence?,
        nowMs: Long,
        prefs: ReminderPreferences,
        nowLocalMinutes: Int,
    ): ReminderDecision {
        val clean = prefs.sanitized()
        if (clean.optOut) return ReminderDecision.SKIP
        if (occurrence == null || occurrence.status != OccurrenceStatus.PLANNED) {
            return ReminderDecision.STALE
        }
        if (delivery.status != ReminderDeliveryStatus.PENDING) return ReminderDecision.SKIP
        if (nowMs < delivery.scheduledAtMs) return ReminderDecision.TOO_EARLY
        if (inQuietHours(nowLocalMinutes, clean.quietStartHour, clean.quietEndHour)) {
            return ReminderDecision.DEFER_QUIET
        }
        return ReminderDecision.DELIVER
    }

    fun inQuietHours(localMinutes: Int, startHour: Int, endHour: Int): Boolean {
        val start = startHour.coerceIn(0, 23) * 60
        val end = endHour.coerceIn(0, 23) * 60
        return if (start == end) {
            false
        } else if (start < end) {
            localMinutes in start until end
        } else {
            localMinutes >= start || localMinutes < end
        }
    }

    fun minutesUntilQuietEnd(nowLocalMinutes: Int, quietEndHour: Int): Int {
        val end = quietEndHour.coerceIn(0, 23) * 60
        return if (nowLocalMinutes < end) end - nowLocalMinutes else (24 * 60 - nowLocalMinutes) + end
    }

    fun scheduledAtMillis(occurrence: ScheduleOccurrence, offsetMinutes: Int): Long =
        occurrence.captured.instantMillis - offsetMinutes.coerceAtLeast(0) * 60_000L

    fun rebuildAction(delivery: ReminderDelivery, nowMs: Long): ReminderRebuildAction {
        if (delivery.status != ReminderDeliveryStatus.PENDING) return ReminderRebuildAction.IGNORE
        return if (delivery.scheduledAtMs >= nowMs) {
            ReminderRebuildAction.RESCHEDULE
        } else {
            ReminderRebuildAction.MARK_STALE
        }
    }
}
