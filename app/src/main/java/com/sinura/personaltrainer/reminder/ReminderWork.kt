package com.sinura.personaltrainer.reminder

import com.sinura.personaltrainer.data.repository.PlannerRepository
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.util.JvmTime

/**
 * One due-reminder pass. The worker is a thin WorkManager wrapper around this.
 *
 * Database rows are authoritative (ADR-012): missing or stale deliveries are a
 * no-op, and this function never arms an exact alarm.
 */
internal object ReminderWork {
    suspend fun run(
        deliveryId: String?,
        planner: PlannerRepository,
        prefs: ReminderPreferences,
        now: CapturedCivilTime,
        notify: (occurrence: ScheduleOccurrence, deliveryId: String, title: String) -> Unit,
    ) {
        if (deliveryId == null) return
        val nowLocalMinutes = localMinutesOf(now)
        var delivered: ScheduleOccurrence? = null
        planner.processDueDelivery(
            deliveryId = deliveryId,
            prefs = prefs,
            nowLocalMinutes = nowLocalMinutes,
            nowMs = now.instantMillis,
        ) { occurrence, _ ->
            delivered = occurrence
        }
        val occurrence = delivered ?: return
        val rule = planner.getRule(occurrence.ruleId)
        notify(occurrence, deliveryId, AgendaItem(occurrence, rule).title)
    }

    internal fun localMinutesOf(now: CapturedCivilTime): Int {
        val date = JvmTime.civilDate(now.instantMillis, now.zoneId)
        val start = JvmTime.startOfDayMillis(date, now.zoneId)
        return (((now.instantMillis - start) / 60_000L).toInt()).coerceIn(0, 24 * 60 - 1)
    }
}
