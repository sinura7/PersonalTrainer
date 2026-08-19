package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.WeightUnit
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

class PreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.userSettingsDataStore

    /**
     * The preferences stream with the one guard every reader needs: a corrupted or unreadable
     * settings file degrades to defaults instead of throwing into a collector. Read through
     * this rather than [dataStore].data directly.
     */
    private val safePreferences: Flow<Preferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }

    val weightUnit: Flow<WeightUnit> = safePreferences
        .map { prefs -> WeightUnit.fromStorage(prefs[WEIGHT_UNIT]) }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { prefs ->
            prefs[WEIGHT_UNIT] = unit.storageKey
        }
    }

    val schedulePreferences: Flow<SchedulePreferences> = safePreferences
        .map { prefs ->
            SchedulePreferences(
                trainingDaysPerWeek = prefs[TRAINING_DAYS] ?: SchedulePreferences.DEFAULT_DAYS,
                splitStyle = SplitStyle.fromStorage(prefs[SPLIT_STYLE]),
                weekStart = SchedulePreferences.weekStartFromStorage(prefs[WEEK_START]),
            ).sanitized()
        }

    suspend fun setTrainingDaysPerWeek(days: Int) {
        dataStore.edit { prefs ->
            prefs[TRAINING_DAYS] = days.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS)
        }
    }

    suspend fun setSplitStyle(style: SplitStyle) {
        dataStore.edit { prefs ->
            prefs[SPLIT_STYLE] = style.storageKey
        }
    }

    suspend fun setWeekStart(day: DayOfWeek) {
        dataStore.edit { prefs ->
            prefs[WEEK_START] = day.name
        }
    }

    val restTimerPreferences: Flow<RestTimerPreferences> = safePreferences
        .map { prefs ->
            RestTimerPreferences(
                soundEnabled = prefs[REST_SOUND] ?: true,
                vibrationEnabled = prefs[REST_VIBRATE] ?: true,
                defaultRestSeconds = prefs[REST_DEFAULT] ?: RestTimerPreferences.DEFAULT_SECONDS,
                lastPresetSeconds = prefs[REST_LAST_PRESET],
            ).sanitized()
        }

    suspend fun setRestSoundEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[REST_SOUND] = enabled }
    }

    suspend fun setRestVibrationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[REST_VIBRATE] = enabled }
    }

    suspend fun setDefaultRestSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[REST_DEFAULT] = seconds.coerceIn(RestTimerPreferences.MIN_SECONDS, RestTimerPreferences.MAX_SECONDS)
        }
    }

    suspend fun setLastRestPresetSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[REST_LAST_PRESET] = seconds.coerceIn(RestTimerPreferences.MIN_SECONDS, RestTimerPreferences.MAX_SECONDS)
        }
    }

    suspend fun setRestTimerPreferences(value: RestTimerPreferences) {
        val clean = value.sanitized()
        dataStore.edit { prefs ->
            prefs[REST_SOUND] = clean.soundEnabled
            prefs[REST_VIBRATE] = clean.vibrationEnabled
            prefs[REST_DEFAULT] = clean.defaultRestSeconds
            if (clean.lastPresetSeconds == null) {
                prefs.remove(REST_LAST_PRESET)
            } else {
                prefs[REST_LAST_PRESET] = clean.lastPresetSeconds
            }
        }
    }

    suspend fun setSchedulePreferences(value: SchedulePreferences) {
        val clean = value.sanitized()
        dataStore.edit { prefs ->
            prefs[TRAINING_DAYS] = clean.trainingDaysPerWeek
            prefs[SPLIT_STYLE] = clean.splitStyle.storageKey
            prefs[WEEK_START] = clean.weekStart.name
        }
    }

    val driveAccountEmail: Flow<String?> = safePreferences
        .map { prefs -> prefs[DRIVE_ACCOUNT] }

    val lastBackupAt: Flow<Long?> = safePreferences
        .map { prefs -> prefs[LAST_BACKUP_AT] }

    val lastBackupName: Flow<String?> = safePreferences
        .map { prefs -> prefs[LAST_BACKUP_NAME] }

    suspend fun setDriveAccountEmail(email: String?) {
        dataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(DRIVE_ACCOUNT)
            } else {
                prefs[DRIVE_ACCOUNT] = email
            }
        }
    }

    suspend fun driveFolderId(): String? = safePreferences.first()[DRIVE_FOLDER_ID]

    suspend fun setDriveFolderId(folderId: String?) {
        dataStore.edit { prefs ->
            if (folderId.isNullOrBlank()) {
                prefs.remove(DRIVE_FOLDER_ID)
            } else {
                prefs[DRIVE_FOLDER_ID] = folderId
            }
        }
    }

    /**
     * One atomic write for everything a restore carries, so a crash mid-way cannot leave the
     * restored data paired with half the old preferences.
     */
    suspend fun setRestoredPreferences(
        unit: WeightUnit,
        schedule: SchedulePreferences,
        rest: RestTimerPreferences,
    ) {
        val cleanSchedule = schedule.sanitized()
        val cleanRest = rest.sanitized()
        dataStore.edit { prefs ->
            prefs[WEIGHT_UNIT] = unit.storageKey
            prefs[TRAINING_DAYS] = cleanSchedule.trainingDaysPerWeek
            prefs[SPLIT_STYLE] = cleanSchedule.splitStyle.storageKey
            prefs[WEEK_START] = cleanSchedule.weekStart.name
            prefs[REST_SOUND] = cleanRest.soundEnabled
            prefs[REST_VIBRATE] = cleanRest.vibrationEnabled
            prefs[REST_DEFAULT] = cleanRest.defaultRestSeconds
        }
    }

    val lastRestoreAt: Flow<Long?> = safePreferences.map { prefs -> prefs[LAST_RESTORE_AT] }

    val lastRestoreName: Flow<String?> = safePreferences.map { prefs -> prefs[LAST_RESTORE_NAME] }

    /**
     * Tracked separately from [setLastBackup]. Restoring used to overwrite the last-backup
     * stamp, so Settings claimed a backup existed as of the restored file's date — the one
     * signal whose whole job is to nag the user into backing up.
     */
    suspend fun setLastRestore(fileName: String, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_RESTORE_NAME] = fileName
            prefs[LAST_RESTORE_AT] = atMillis
        }
    }

    suspend fun setLastBackup(fileName: String, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_BACKUP_NAME] = fileName
            prefs[LAST_BACKUP_AT] = atMillis
        }
    }

    suspend fun clearDriveSession() {
        dataStore.edit { prefs ->
            prefs.remove(DRIVE_ACCOUNT)
            prefs.remove(DRIVE_FOLDER_ID)
        }
    }

    private companion object {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val TRAINING_DAYS = intPreferencesKey("training_days_per_week")
        val SPLIT_STYLE = stringPreferencesKey("split_style")
        val WEEK_START = stringPreferencesKey("week_start")
        val REST_SOUND = booleanPreferencesKey("rest_sound")
        val REST_VIBRATE = booleanPreferencesKey("rest_vibrate")
        val REST_DEFAULT = intPreferencesKey("rest_default_seconds")
        val REST_LAST_PRESET = intPreferencesKey("rest_last_preset_seconds")
        val DRIVE_ACCOUNT = stringPreferencesKey("drive_account_email")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_NAME = stringPreferencesKey("last_backup_name")
        val LAST_RESTORE_AT = longPreferencesKey("last_restore_at")
        val LAST_RESTORE_NAME = stringPreferencesKey("last_restore_name")
    }
}
