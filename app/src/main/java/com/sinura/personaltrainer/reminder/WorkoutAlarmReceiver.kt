package com.sinura.personaltrainer.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires the per-day workout reminder, then arms the next week's same weekday.
 */
class WorkoutAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WorkoutAlarmScheduler.ACTION) return
        val weekday = Weekday.fromStorage(intent.getStringExtra(WorkoutAlarmScheduler.EXTRA_WEEKDAY))
            ?: return
        val app = context.applicationContext as? PersonalTrainerApp ?: return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + app.container.ioDispatcher)
        scope.launch {
            try {
                val prefs = app.container.preferencesRepository.reminderPreferences.first()
                WorkoutAlarmScheduler.rebuild(app, prefs, app.container.time)
                val today = app.container.time.civilDate(
                    app.container.time.nowMillis(),
                    app.container.time.defaultZoneId(),
                )
                if (today.dayOfWeek != weekday) return@launch
                val occurrence = app.container.plannerRepository
                    .occurrencesBetween(today.epochDay, today.epochDay)
                    .firstOrNull { it.status == OccurrenceStatus.PLANNED }
                ReminderNotifications.showWorkoutAlarm(
                    context = app,
                    occurrenceId = occurrence?.id,
                    title = "Time to train",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w(TAG, "Workout alarm failed", error)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "PT/WorkoutAlarmRx"
    }
}
