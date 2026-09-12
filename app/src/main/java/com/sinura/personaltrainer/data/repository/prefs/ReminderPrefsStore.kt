package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.domain.DayReminder
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WorkoutAlarms
import kotlinx.coroutines.flow.Flow

/** The reminder opt-out, quiet hours, per-day alarms, and the occurrence a live session came from. */
interface ReminderPrefs {
    val reminderPreferences: Flow<ReminderPreferences>

    /**
     * Strength (and mixed) occurrence started through the live logger.
     * Device-local: not part of backup. Cleared on finish or discard.
     */
    val pendingOccurrenceId: Flow<String?>

    /** First-open permission walk. Device-local; grant and deny both count. */
    val launchPermissionsAsked: Flow<Boolean>

    suspend fun setReminderOptOut(optOut: Boolean)
    suspend fun setReminderQuietHours(startHour: Int, endHour: Int)
    suspend fun setDayAlarm(weekday: Weekday, hour: Int, minute: Int)
    suspend fun clearDayAlarm(weekday: Weekday)
    suspend fun setPendingOccurrenceId(id: String?)
    suspend fun setLaunchPermissionsAsked(asked: Boolean)
}

internal class ReminderPrefsStore(private val store: SettingsStore) : ReminderPrefs {
    override val reminderPreferences: Flow<ReminderPreferences> = store.pref { prefs ->
        ReminderPreferences(
            optOut = prefs[REMINDER_OPT_OUT] ?: false,
            quietStartHour = prefs[REMINDER_QUIET_START]
                ?: ReminderPreferences.DEFAULT_QUIET_START_HOUR,
            quietEndHour = prefs[REMINDER_QUIET_END]
                ?: ReminderPreferences.DEFAULT_QUIET_END_HOUR,
            dayAlarms = WorkoutAlarms.decode(prefs[REMINDER_DAY_ALARMS]),
        ).sanitized()
    }

    override val pendingOccurrenceId: Flow<String?> =
        store.pref { prefs -> prefs[PENDING_OCCURRENCE_ID]?.takeIf { it.isNotBlank() } }

    override val launchPermissionsAsked: Flow<Boolean> =
        store.pref { prefs -> prefs[LAUNCH_PERMISSIONS_ASKED] ?: false }

    override suspend fun setReminderOptOut(optOut: Boolean) {
        store.data.edit { prefs -> prefs[REMINDER_OPT_OUT] = optOut }
    }

    override suspend fun setReminderQuietHours(startHour: Int, endHour: Int) {
        store.data.edit { prefs ->
            prefs[REMINDER_QUIET_START] = startHour.coerceIn(0, 23)
            prefs[REMINDER_QUIET_END] = endHour.coerceIn(0, 23)
        }
    }

    override suspend fun setDayAlarm(weekday: Weekday, hour: Int, minute: Int) {
        store.data.edit { prefs ->
            val next = WorkoutAlarms.decode(prefs[REMINDER_DAY_ALARMS]).toMutableMap()
            next[weekday] = DayReminder(hour = hour, minute = minute).sanitized()
            prefs[REMINDER_DAY_ALARMS] = WorkoutAlarms.encode(next)
        }
    }

    override suspend fun clearDayAlarm(weekday: Weekday) {
        store.data.edit { prefs ->
            val next = WorkoutAlarms.decode(prefs[REMINDER_DAY_ALARMS]).toMutableMap()
            next.remove(weekday)
            if (next.isEmpty()) {
                prefs.remove(REMINDER_DAY_ALARMS)
            } else {
                prefs[REMINDER_DAY_ALARMS] = WorkoutAlarms.encode(next)
            }
        }
    }

    override suspend fun setPendingOccurrenceId(id: String?) {
        store.data.edit { prefs ->
            if (id.isNullOrBlank()) {
                prefs.remove(PENDING_OCCURRENCE_ID)
            } else {
                prefs[PENDING_OCCURRENCE_ID] = id
            }
        }
    }

    override suspend fun setLaunchPermissionsAsked(asked: Boolean) {
        store.data.edit { prefs -> prefs[LAUNCH_PERMISSIONS_ASKED] = asked }
    }
}
