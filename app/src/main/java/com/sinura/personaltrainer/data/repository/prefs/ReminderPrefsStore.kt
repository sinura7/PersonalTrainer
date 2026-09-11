package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.domain.ReminderPreferences
import kotlinx.coroutines.flow.Flow

/** The reminder opt-out, the quiet hours, and the occurrence a live session came from. */
interface ReminderPrefs {
    val reminderPreferences: Flow<ReminderPreferences>

    /**
     * Strength (and mixed) occurrence started through the live logger.
     * Device-local: not part of backup. Cleared on finish or discard.
     */
    val pendingOccurrenceId: Flow<String?>

    suspend fun setReminderOptOut(optOut: Boolean)
    suspend fun setReminderQuietHours(startHour: Int, endHour: Int)
    suspend fun setPendingOccurrenceId(id: String?)
}

internal class ReminderPrefsStore(private val store: SettingsStore) : ReminderPrefs {
    override val reminderPreferences: Flow<ReminderPreferences> = store.pref { prefs ->
        ReminderPreferences(
            optOut = prefs[REMINDER_OPT_OUT] ?: false,
            quietStartHour = prefs[REMINDER_QUIET_START]
                ?: ReminderPreferences.DEFAULT_QUIET_START_HOUR,
            quietEndHour = prefs[REMINDER_QUIET_END]
                ?: ReminderPreferences.DEFAULT_QUIET_END_HOUR,
        ).sanitized()
    }

    override val pendingOccurrenceId: Flow<String?> =
        store.pref { prefs -> prefs[PENDING_OCCURRENCE_ID]?.takeIf { it.isNotBlank() } }

    override suspend fun setReminderOptOut(optOut: Boolean) {
        store.data.edit { prefs -> prefs[REMINDER_OPT_OUT] = optOut }
    }

    override suspend fun setReminderQuietHours(startHour: Int, endHour: Int) {
        store.data.edit { prefs ->
            prefs[REMINDER_QUIET_START] = startHour.coerceIn(0, 23)
            prefs[REMINDER_QUIET_END] = endHour.coerceIn(0, 23)
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
}
