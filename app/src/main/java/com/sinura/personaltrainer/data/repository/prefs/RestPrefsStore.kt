package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.domain.RestTimerPreferences
import kotlinx.coroutines.flow.Flow

/** Sound, vibration, ticks, the default rest and the last preset. */
interface RestPrefs {
    val restTimerPreferences: Flow<RestTimerPreferences>

    /**
     * Exact-alarm special-access is requested only after rest is used or
     * configured. Onboarding must never write this. Restore leaves it alone.
     */
    val restAlarmEligible: Flow<Boolean>

    /**
     * First-rest Samsung battery mention (T-16). Device-local: restore and
     * [setRestTimerPreferences] leave it alone, so a backup cannot hide the
     * line on a new phone.
     */
    val restBatteryHintShown: Flow<Boolean>

    suspend fun markRestAlarmEligible()
    suspend fun markRestBatteryHintShown()
    suspend fun setRestSoundEnabled(enabled: Boolean)
    suspend fun setRestVibrationEnabled(enabled: Boolean)

    /**
     * Device-local on purpose: [setRestTimerPreferences] and the restore
     * path leave this key alone, so a backup from another phone cannot
     * switch the ticks on or off here.
     */
    suspend fun setRestTickEnabled(enabled: Boolean)
    suspend fun setDefaultRestSeconds(seconds: Int)
    suspend fun setLastRestPresetSeconds(seconds: Int)
    suspend fun setRestTimerPreferences(value: RestTimerPreferences)
}

internal class RestPrefsStore(private val store: SettingsStore) : RestPrefs {
    override val restTimerPreferences: Flow<RestTimerPreferences> = store.pref { prefs ->
        RestTimerPreferences(
            soundEnabled = prefs[REST_SOUND] ?: true,
            vibrationEnabled = prefs[REST_VIBRATE] ?: true,
            tickEnabled = prefs[REST_TICK] ?: true,
            defaultRestSeconds = prefs[REST_DEFAULT] ?: RestTimerPreferences.DEFAULT_SECONDS,
            lastPresetSeconds = prefs[REST_LAST_PRESET],
        ).sanitized()
    }

    override val restAlarmEligible: Flow<Boolean> =
        store.pref { prefs -> prefs[REST_ALARM_ELIGIBLE] ?: false }

    override val restBatteryHintShown: Flow<Boolean> =
        store.pref { prefs -> prefs[REST_BATTERY_HINT] ?: false }

    override suspend fun markRestAlarmEligible() {
        store.data.edit { prefs -> prefs[REST_ALARM_ELIGIBLE] = true }
    }

    override suspend fun markRestBatteryHintShown() {
        store.data.edit { prefs -> prefs[REST_BATTERY_HINT] = true }
    }

    override suspend fun setRestSoundEnabled(enabled: Boolean) {
        store.data.edit { prefs ->
            prefs[REST_SOUND] = enabled
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    override suspend fun setRestVibrationEnabled(enabled: Boolean) {
        store.data.edit { prefs ->
            prefs[REST_VIBRATE] = enabled
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    override suspend fun setRestTickEnabled(enabled: Boolean) {
        store.data.edit { prefs ->
            prefs[REST_TICK] = enabled
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    override suspend fun setDefaultRestSeconds(seconds: Int) {
        store.data.edit { prefs ->
            prefs[REST_DEFAULT] = seconds.coerceIn(
                RestTimerPreferences.MIN_SECONDS,
                RestTimerPreferences.MAX_SECONDS,
            )
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    override suspend fun setLastRestPresetSeconds(seconds: Int) {
        store.data.edit { prefs ->
            prefs[REST_LAST_PRESET] = seconds.coerceIn(
                RestTimerPreferences.MIN_SECONDS,
                RestTimerPreferences.MAX_SECONDS,
            )
        }
    }

    override suspend fun setRestTimerPreferences(value: RestTimerPreferences) {
        val clean = value.sanitized()
        store.data.edit { prefs ->
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
}
