package com.sinura.personaltrainer.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.flow.first

/**
 * Rereads the delivery row. Stale work is a no-op. Never schedules
 * an exact alarm (ADR-012).
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deliveryId = inputData.getString(KEY_DELIVERY_ID) ?: return Result.success()
        val app = applicationContext as? PersonalTrainerApp ?: return Result.success()
        val prefs = app.container.preferencesRepository.reminderPreferences.first()
        val now = JvmTime.captureNow()
        val nowLocalMinutes = run {
            val date = JvmTime.civilDate(now.instantMillis, now.zoneId)
            val start = JvmTime.startOfDayMillis(date, now.zoneId)
            (((now.instantMillis - start) / 60_000L).toInt()).coerceIn(0, 24 * 60 - 1)
        }
        var delivered: com.sinura.personaltrainer.domain.ScheduleOccurrence? = null
        app.container.plannerRepository.processDueDelivery(
            deliveryId = deliveryId,
            prefs = prefs,
            nowLocalMinutes = nowLocalMinutes,
            nowMs = now.instantMillis,
        ) { occurrence, _ ->
            delivered = occurrence
        }
        val occurrence = delivered
        if (occurrence != null) {
            val rule = app.container.plannerRepository.getRule(occurrence.ruleId)
            ReminderNotifications.show(
                applicationContext,
                occurrence,
                deliveryId,
                AgendaItem(occurrence, rule).title,
            )
        }
        return Result.success()
    }

    companion object {
        const val KEY_DELIVERY_ID = "delivery_id"
    }
}
