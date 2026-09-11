package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sinura.personaltrainer.domain.Weekday
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The one `user_settings` DataStore, and the two things every reader of it needs.
 *
 * `PreferencesRepository` was a single 1,070-line class over forty-six keys spanning ten
 * unrelated feature areas — display, coaching, schedule, rest, reminders, bodyweight, training
 * blocks, Drive and automatic backup, library collisions, onboarding, and the restore cutover.
 * Every one of those areas repeated this guard and this helper.
 *
 * They are still one DataStore and one file on disk. That is not incidental: the keys share
 * `user_settings`, so splitting them across stores would be a data migration, and the one
 * signed migration this app is allowed already happened (ADR-010). What is split is the code.
 */
class SettingsStore(
    /**
     * Writers reach this directly rather than through a wrapper method here. A wrapper would
     * be one more `edit { }` site than the code it replaced, and `check-required-args.py`
     * counts positional-call-with-trailing-lambda sites it cannot verify — a ceiling this
     * refactor has no business raising to buy a layer nobody needs.
     */
    val data: DataStore<Preferences>,
) {
    /**
     * The preferences stream with the one guard every reader needs: a corrupted or unreadable
     * settings file degrades to defaults instead of throwing into a collector. Read through
     * this rather than the DataStore directly.
     */
    val safePreferences: Flow<Preferences> = data.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    /**
     * A mapped preference that stays quiet when the stored value did not change. DataStore
     * re-emits the whole [Preferences] object on any write; without this, every reader of an
     * unrelated key reruns.
     */
    fun <T> pref(read: (Preferences) -> T): Flow<T> =
        safePreferences.map(read).distinctUntilChanged()

    suspend fun snapshot(): Preferences = safePreferences.first()
}

// Every key in `user_settings`, in one place.
//
// These were a private companion of `PreferencesRepository`, which is why nothing else could
// read or write a preference without going through that class — and why the class grew to
// cover ten feature areas. The string literals are the on-disk format and must not change.
//
// Package-level rather than members of an object: the stores beside them then need no import
// at all, and `PreferencesRepository` imports them by name the way it imports any other
// top-level declaration.

internal val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
internal val CLOCK_FORMAT = stringPreferencesKey("clock_format")
internal val BODYWEIGHT_CHECK_IN_WEEKDAY = stringPreferencesKey("bodyweight_check_in_weekday")

internal val TRAINING_GOAL = stringPreferencesKey("training_goal")
internal val TRAINING_EMPHASIS = stringPreferencesKey("training_emphasis")
internal val AVAILABLE_EQUIPMENT = stringSetPreferencesKey("available_equipment")
internal val TRAINING_AGE = stringPreferencesKey("training_age")
internal val TRAINING_PLACE = stringPreferencesKey("training_place")
internal val TRAINING_FOCUS = stringPreferencesKey("training_focus")
internal val HEAT_WINDOW = stringPreferencesKey("heat_window")

internal val TRAINING_DAYS = intPreferencesKey("training_days_per_week")
internal val SPLIT_STYLE = stringPreferencesKey("split_style")
internal val WEEK_START = stringPreferencesKey("week_start")
internal val PREFERRED_DAYS = stringSetPreferencesKey("preferred_days")
internal val LIGHTER_WEEK_START = longPreferencesKey("lighter_week_start_epoch_day")

internal val REST_SOUND = booleanPreferencesKey("rest_sound")
internal val REST_VIBRATE = booleanPreferencesKey("rest_vibrate")
internal val REST_TICK = booleanPreferencesKey("rest_tick")
internal val REST_DEFAULT = intPreferencesKey("rest_default_seconds")
internal val REST_LAST_PRESET = intPreferencesKey("rest_last_preset_seconds")
internal val REST_ALARM_ELIGIBLE = booleanPreferencesKey("rest_alarm_eligible")

internal val REMINDER_OPT_OUT = booleanPreferencesKey("reminder_opt_out")
internal val REMINDER_QUIET_START = intPreferencesKey("reminder_quiet_start_hour")
internal val REMINDER_QUIET_END = intPreferencesKey("reminder_quiet_end_hour")
internal val PENDING_OCCURRENCE_ID = stringPreferencesKey("pending_occurrence_id")

internal val DRIVE_ACCOUNT = stringPreferencesKey("drive_account_email")
internal val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
internal val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
internal val AUTO_BACKUP_SECRET = stringPreferencesKey("auto_backup_secret")
internal val AUTO_BACKUP_LAST_SESSION = stringPreferencesKey("auto_backup_last_session_id")
internal val AUTO_BACKUP_NEEDS_SIGN_IN = booleanPreferencesKey("auto_backup_needs_sign_in")
internal val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
internal val LAST_BACKUP_NAME = stringPreferencesKey("last_backup_name")
internal val LAST_VERIFIED_BACKUP_AT = longPreferencesKey("last_verified_backup_at")
internal val LAST_VERIFIED_BACKUP_NAME = stringPreferencesKey("last_verified_backup_name")
internal val LAST_RESTORE_AT = longPreferencesKey("last_restore_at")
internal val LAST_RESTORE_NAME = stringPreferencesKey("last_restore_name")
internal val RESTORE_RECOVERY_NOTE = stringPreferencesKey("restore_recovery_note")

internal val BODYWEIGHT_KG = doublePreferencesKey("bodyweight_kg")
internal val BODYWEIGHT_LOG = stringPreferencesKey("bodyweight_log")
internal val BLOCK_START = longPreferencesKey("block_start_epoch_day")
internal val BLOCK_WEEKS = intPreferencesKey("block_weeks")
internal val PAST_BLOCKS = stringPreferencesKey("past_blocks")

internal val DISMISSED_COLLISIONS = stringSetPreferencesKey("library_collision_dismissed_ids")
internal val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
internal val FOUNDATION_GENERATION = stringPreferencesKey("foundation_generation")

internal fun preferredDaysFrom(raw: Set<String>?): Set<Weekday> =
    raw.orEmpty().mapNotNull { name ->
        Weekday.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }.toSet()
