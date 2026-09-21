package com.sinura.personaltrainer.data.sync

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.data.repository.prefs.ACCOUNT_PROFILE_UPDATED_AT_MS
import com.sinura.personaltrainer.data.repository.prefs.AVAILABLE_EQUIPMENT
import com.sinura.personaltrainer.data.repository.prefs.BODYWEIGHT_CHECK_IN_WEEKDAY
import com.sinura.personaltrainer.data.repository.prefs.CLOCK_FORMAT
import com.sinura.personaltrainer.data.repository.prefs.COACH_PREFS_UPDATED_AT_MS
import com.sinura.personaltrainer.data.repository.prefs.DISPLAY_PREFS_UPDATED_AT_MS
import com.sinura.personaltrainer.data.repository.prefs.HEAT_WINDOW
import com.sinura.personaltrainer.data.repository.prefs.REMINDER_DAY_ALARMS
import com.sinura.personaltrainer.data.repository.prefs.REMINDER_OPT_OUT
import com.sinura.personaltrainer.data.repository.prefs.REMINDER_PREFS_UPDATED_AT_MS
import com.sinura.personaltrainer.data.repository.prefs.REMINDER_QUIET_END
import com.sinura.personaltrainer.data.repository.prefs.REMINDER_QUIET_START
import com.sinura.personaltrainer.data.repository.prefs.SAVE_POSTURE
import com.sinura.personaltrainer.data.repository.prefs.SAVE_POSTURE_CHOSEN
import com.sinura.personaltrainer.data.repository.prefs.SettingsStore
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_AGE
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_EMPHASIS
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_FOCUS
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_GOAL
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_PLACE
import com.sinura.personaltrainer.data.repository.prefs.WEIGHT_UNIT
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutAlarms
import com.sinura.personaltrainer.domain.HeatWindow

/** Reads and applies Temper Account preference snapshots (Packet 3). */
internal object SyncAccountPrefs {
    fun coachUpdatedAtMs(prefs: Preferences): Long = prefs[COACH_PREFS_UPDATED_AT_MS] ?: 0L
    fun reminderUpdatedAtMs(prefs: Preferences): Long = prefs[REMINDER_PREFS_UPDATED_AT_MS] ?: 0L
    fun displayUpdatedAtMs(prefs: Preferences): Long = prefs[DISPLAY_PREFS_UPDATED_AT_MS] ?: 0L
    fun accountProfileUpdatedAtMs(prefs: Preferences): Long = prefs[ACCOUNT_PROFILE_UPDATED_AT_MS] ?: 0L

    fun touchCoachUpdatedAt(prefs: MutablePreferences, nowMs: Long) {
        prefs[COACH_PREFS_UPDATED_AT_MS] = nowMs
    }

    fun touchReminderUpdatedAt(prefs: MutablePreferences, nowMs: Long) {
        prefs[REMINDER_PREFS_UPDATED_AT_MS] = nowMs
    }

    fun touchDisplayUpdatedAt(prefs: MutablePreferences, nowMs: Long) {
        prefs[DISPLAY_PREFS_UPDATED_AT_MS] = nowMs
    }

    fun touchAccountProfileUpdatedAt(prefs: MutablePreferences, nowMs: Long) {
        prefs[ACCOUNT_PROFILE_UPDATED_AT_MS] = nowMs
    }

    fun coachToRemote(userId: String, prefs: Preferences): RemoteCoachPrefsRow {
        val updatedAtMs = coachUpdatedAtMs(prefs).takeIf { it > 0L } ?: System.currentTimeMillis()
        return RemoteCoachPrefsRow(
            userId = userId,
            trainingGoal = TrainingGoal.fromStorage(prefs[TRAINING_GOAL]).name,
            trainingEmphasis = TrainingEmphasis.fromStorage(prefs[TRAINING_EMPHASIS]).name,
            availableEquipment = prefs[AVAILABLE_EQUIPMENT].orEmpty().sorted(),
            trainingAge = TrainingAge.fromStorage(prefs[TRAINING_AGE]).name,
            trainingPlace = prefs[TRAINING_PLACE].orEmpty(),
            trainingFocus = TrainingFocus.fromStorage(prefs[TRAINING_FOCUS]).name,
            heatWindow = HeatWindow.fromStorage(prefs[HEAT_WINDOW]).name,
            updatedAtMs = updatedAtMs,
        )
    }

    fun reminderToRemote(userId: String, prefs: Preferences): RemoteReminderPrefsRow {
        val updatedAtMs = reminderUpdatedAtMs(prefs).takeIf { it > 0L } ?: System.currentTimeMillis()
        val reminder = ReminderPreferences(
            optOut = prefs[REMINDER_OPT_OUT] ?: false,
            quietStartHour = prefs[REMINDER_QUIET_START] ?: ReminderPreferences.DEFAULT_QUIET_START_HOUR,
            quietEndHour = prefs[REMINDER_QUIET_END] ?: ReminderPreferences.DEFAULT_QUIET_END_HOUR,
            dayAlarms = WorkoutAlarms.decode(prefs[REMINDER_DAY_ALARMS]),
        ).sanitized()
        return RemoteReminderPrefsRow(
            userId = userId,
            reminderOptOut = reminder.optOut,
            reminderQuietStartHour = reminder.quietStartHour,
            reminderQuietEndHour = reminder.quietEndHour,
            dayAlarms = WorkoutAlarms.encode(reminder.dayAlarms).sorted(),
            updatedAtMs = updatedAtMs,
        )
    }

    fun displayToRemote(userId: String, prefs: Preferences): RemoteDisplayPrefsRow {
        val updatedAtMs = displayUpdatedAtMs(prefs).takeIf { it > 0L } ?: System.currentTimeMillis()
        return RemoteDisplayPrefsRow(
            userId = userId,
            weightUnit = WeightUnit.fromStorage(prefs[WEIGHT_UNIT]).storageKey,
            clockFormat = ClockFormat.fromStorage(prefs[CLOCK_FORMAT]).storageKey,
            bodyweightCheckInWeekday = prefs[BODYWEIGHT_CHECK_IN_WEEKDAY],
            updatedAtMs = updatedAtMs,
        )
    }

    fun accountProfileToRemote(userId: String, prefs: Preferences): RemoteAccountProfileRow {
        val updatedAtMs = accountProfileUpdatedAtMs(prefs).takeIf { it > 0L } ?: System.currentTimeMillis()
        return RemoteAccountProfileRow(
            userId = userId,
            savePosture = SavePosture.fromStorage(prefs[SAVE_POSTURE])?.name ?: SavePosture.LOCAL.name,
            savePostureChosen = prefs[SAVE_POSTURE_CHOSEN] ?: false,
            updatedAtMs = updatedAtMs,
        )
    }

    suspend fun applyCoachRemote(store: SettingsStore, remote: RemoteCoachPrefsRow) {
        store.data.edit { prefs ->
            prefs[TRAINING_GOAL] = remote.trainingGoal
            prefs[TRAINING_EMPHASIS] = remote.trainingEmphasis
            prefs[AVAILABLE_EQUIPMENT] = remote.availableEquipment.toSet()
            prefs[TRAINING_AGE] = remote.trainingAge
            prefs[TRAINING_PLACE] = remote.trainingPlace
            prefs[TRAINING_FOCUS] = remote.trainingFocus
            prefs[HEAT_WINDOW] = remote.heatWindow
            prefs[COACH_PREFS_UPDATED_AT_MS] = remote.updatedAtMs
        }
    }

    suspend fun applyReminderRemote(store: SettingsStore, remote: RemoteReminderPrefsRow) {
        store.data.edit { prefs ->
            prefs[REMINDER_OPT_OUT] = remote.reminderOptOut
            prefs[REMINDER_QUIET_START] = remote.reminderQuietStartHour
            prefs[REMINDER_QUIET_END] = remote.reminderQuietEndHour
            if (remote.dayAlarms.isEmpty()) {
                prefs.remove(REMINDER_DAY_ALARMS)
            } else {
                prefs[REMINDER_DAY_ALARMS] = remote.dayAlarms.toSet()
            }
            prefs[REMINDER_PREFS_UPDATED_AT_MS] = remote.updatedAtMs
        }
    }

    suspend fun applyDisplayRemote(store: SettingsStore, remote: RemoteDisplayPrefsRow) {
        store.data.edit { prefs ->
            prefs[WEIGHT_UNIT] = remote.weightUnit
            prefs[CLOCK_FORMAT] = remote.clockFormat
            val weekday = remote.bodyweightCheckInWeekday?.let { Weekday.fromStorage(it) }
            if (weekday == null) {
                prefs.remove(BODYWEIGHT_CHECK_IN_WEEKDAY)
            } else {
                prefs[BODYWEIGHT_CHECK_IN_WEEKDAY] = weekday.name
            }
            prefs[DISPLAY_PREFS_UPDATED_AT_MS] = remote.updatedAtMs
        }
    }

    suspend fun applyAccountProfileRemote(store: SettingsStore, remote: RemoteAccountProfileRow) {
        store.data.edit { prefs ->
            prefs[SAVE_POSTURE] = remote.savePosture
            prefs[SAVE_POSTURE_CHOSEN] = remote.savePostureChosen
            prefs[ACCOUNT_PROFILE_UPDATED_AT_MS] = remote.updatedAtMs
        }
    }
}
